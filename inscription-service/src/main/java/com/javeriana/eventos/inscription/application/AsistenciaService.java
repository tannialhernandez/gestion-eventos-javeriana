package com.javeriana.eventos.inscription.application;

import com.javeriana.eventos.inscription.domain.model.Asistencia;
import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.out.AsistenciaRepository;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import com.javeriana.eventos.inscription.domain.port.out.InscripcionRepository;
import com.javeriana.eventos.inscription.infrastructure.security.JwtPrincipal;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class AsistenciaService {

    private final InscripcionRepository inscripcionRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final EventoServicePort eventoService;

    public AsistenciaService(InscripcionRepository inscripcionRepository,
                             AsistenciaRepository asistenciaRepository,
                             EventoServicePort eventoService) {
        this.inscripcionRepository = inscripcionRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.eventoService = eventoService;
    }

    public List<InscripcionAsistencia> listarPorEvento(UUID eventoId, JwtPrincipal solicitante) {
        autorizarGestor(eventoId, solicitante);

        List<Inscripcion> inscripciones = inscripcionRepository.buscarPorEventoId(eventoId)
            .stream()
            .filter(this::esInscripcionCertificable)
            .sorted(Comparator.comparing(Inscripcion::getFechaInscripcion))
            .toList();

        Map<UUID, Asistencia> asistencias = asistenciaRepository
            .buscarPorInscripcionIds(inscripciones.stream().map(Inscripcion::getId).toList())
            .stream()
            .collect(Collectors.toMap(Asistencia::getInscripcionId, Function.identity()));

        return inscripciones.stream()
            .map(inscripcion -> InscripcionAsistencia.from(inscripcion, asistencias.get(inscripcion.getId())))
            .toList();
    }

    public InscripcionAsistencia registrar(UUID eventoId,
                                           UUID inscripcionId,
                                           boolean asistio,
                                           String registradoPor,
                                           String observaciones,
                                           JwtPrincipal solicitante) {
        autorizarGestor(eventoId, solicitante);

        Inscripcion inscripcion = buscarInscripcion(inscripcionId);
        if (!inscripcion.getEventoId().equals(eventoId)) {
            throw new BusinessRuleViolationException(
                "RN-ASISTENCIA-02",
                "La inscripción no pertenece al evento indicado"
            );
        }
        if (!esInscripcionCertificable(inscripcion)) {
            throw new BusinessRuleViolationException(
                "RN-ASISTENCIA-01",
                "Solo inscripciones confirmadas pueden registrar asistencia"
            );
        }

        Asistencia asistencia = asistenciaRepository.buscarPorInscripcionId(inscripcionId)
            .map(actual -> actual.actualizar(asistio, registradoPor, observaciones))
            .orElseGet(() -> Asistencia.nueva(inscripcionId, asistio, registradoPor, observaciones));

        return InscripcionAsistencia.from(inscripcion, asistenciaRepository.guardar(asistencia));
    }

    @Transactional(readOnly = true)
    public InscripcionAsistencia consultarPropiaPorEvento(UUID eventoId, JwtPrincipal participante) {
        Inscripcion inscripcion = inscripcionRepository
            .buscarPorUsuarioIdYEventoId(participante.userId(), eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Inscripción no encontrada para este evento"));

        Asistencia asistencia = asistenciaRepository.buscarPorInscripcionId(inscripcion.getId()).orElse(null);
        return InscripcionAsistencia.from(inscripcion, asistencia);
    }

    @Transactional(readOnly = true)
    public Asistencia buscarAsistencia(UUID inscripcionId) {
        return asistenciaRepository.buscarPorInscripcionId(inscripcionId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Inscripcion buscarInscripcion(UUID inscripcionId) {
        return inscripcionRepository.buscarPorId(inscripcionId)
            .orElseThrow(() -> new IllegalArgumentException("Inscripción no encontrada"));
    }

    public boolean esInscripcionCertificable(Inscripcion inscripcion) {
        return inscripcion.getEstado() == EstadoInscripcion.CONFIRMADA
            || inscripcion.getEstado() == EstadoInscripcion.ASISTENCIA_REGISTRADA
            || inscripcion.getEstado() == EstadoInscripcion.CERTIFICADO_EMITIDO;
    }

    private void autorizarGestor(UUID eventoId, JwtPrincipal solicitante) {
        if (solicitante == null) {
            throw new SecurityException("Operación requiere autenticación");
        }
        if (solicitante.esAdmin()) return;
        if (!solicitante.esOrganizador()) {
            throw new SecurityException("Solo ADMIN u ORGANIZADOR puede gestionar asistencia");
        }

        EventoServicePort.EventoInfo evento = eventoService.obtenerEvento(eventoId);
        if (evento.organizadorId() == null || !evento.organizadorId().equals(solicitante.userId())) {
            throw new SecurityException("Solo el organizador propietario puede gestionar asistencia");
        }
    }

    public record InscripcionAsistencia(
        UUID inscripcionId,
        UUID eventoId,
        UUID usuarioId,
        String estado,
        boolean asistio,
        String fechaRegistro,
        String registradoPor,
        String observaciones
    ) {
        static InscripcionAsistencia from(Inscripcion inscripcion, Asistencia asistencia) {
            return new InscripcionAsistencia(
                inscripcion.getId(),
                inscripcion.getEventoId(),
                inscripcion.getUsuarioId(),
                inscripcion.getEstado().name(),
                asistencia != null && asistencia.isAsistio(),
                asistencia != null ? asistencia.getFechaRegistro().toString() : null,
                asistencia != null ? asistencia.getRegistradoPor() : null,
                asistencia != null ? asistencia.getObservaciones() : null
            );
        }
    }
}
