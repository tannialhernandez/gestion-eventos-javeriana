package com.javeriana.eventos.inscription.application;

import com.javeriana.eventos.inscription.domain.model.Asistencia;
import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.out.AsistenciaRepository;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import com.javeriana.eventos.inscription.domain.port.out.InscripcionRepository;
import com.javeriana.eventos.inscription.infrastructure.security.JwtPrincipal;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsistenciaServiceTest {

    @Mock private InscripcionRepository inscripcionRepository;
    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private EventoServicePort eventoService;

    private AsistenciaService service;

    private final UUID eventoId = UUID.randomUUID();
    private final UUID organizadorId = UUID.randomUUID();
    private final UUID inscripcionId = UUID.randomUUID();
    private final UUID participanteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AsistenciaService(inscripcionRepository, asistenciaRepository, eventoService);
    }

    @Test
    @DisplayName("Lista inscripciones pendientes y confirmadas que ocupan cupo para gestion")
    void listarPorEvento_incluyePendientesYExcluyeLiberadas() {
        JwtPrincipal admin = new JwtPrincipal(UUID.randomUUID(), List.of("ADMIN"),
            "ana.admin@javeriana.edu.co", "Ana Admin");
        Inscripcion pendiente = inscripcion(UUID.randomUUID(), UUID.randomUUID(), EstadoInscripcion.PENDIENTE_PAGO);
        Inscripcion confirmada = inscripcion(UUID.randomUUID(), UUID.randomUUID(), EstadoInscripcion.CONFIRMADA);
        Inscripcion expirada = inscripcion(UUID.randomUUID(), UUID.randomUUID(), EstadoInscripcion.EXPIRADA);

        when(inscripcionRepository.buscarPorEventoId(eventoId))
            .thenReturn(List.of(pendiente, confirmada, expirada));
        when(asistenciaRepository.buscarPorInscripcionIds(any())).thenReturn(List.of());

        List<AsistenciaService.InscripcionAsistencia> resultado = service.listarPorEvento(eventoId, admin);

        assertThat(resultado)
            .extracting(AsistenciaService.InscripcionAsistencia::estado)
            .containsExactly("PENDIENTE_PAGO", "CONFIRMADA");
    }

    @Test
    @DisplayName("Organizador propietario puede marcar asistencia de inscripción confirmada")
    void registrar_asistenciaConfirmada_ok() {
        Inscripcion inscripcion = inscripcion(EstadoInscripcion.CONFIRMADA);
        JwtPrincipal organizador = new JwtPrincipal(organizadorId, List.of("ORGANIZADOR"),
            "carlos.organizador@javeriana.edu.co", "Carlos Organizador");
        when(eventoService.obtenerEvento(eventoId)).thenReturn(eventoInfo());
        when(inscripcionRepository.buscarPorId(inscripcionId)).thenReturn(Optional.of(inscripcion));
        when(asistenciaRepository.buscarPorInscripcionId(inscripcionId)).thenReturn(Optional.empty());
        when(asistenciaRepository.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AsistenciaService.InscripcionAsistencia resultado = service.registrar(
            eventoId, inscripcionId, true, "carlos.organizador@javeriana.edu.co", null, organizador);

        assertThat(resultado.asistio()).isTrue();
        assertThat(resultado.inscripcionId()).isEqualTo(inscripcionId);
        verify(asistenciaRepository).guardar(any(Asistencia.class));
    }

    @Test
    @DisplayName("Rechaza asistencia sobre inscripción pendiente de pago")
    void registrar_inscripcionPendiente_rechaza() {
        JwtPrincipal admin = new JwtPrincipal(UUID.randomUUID(), List.of("ADMIN"),
            "ana.admin@javeriana.edu.co", "Ana Admin");
        when(inscripcionRepository.buscarPorId(inscripcionId))
            .thenReturn(Optional.of(inscripcion(EstadoInscripcion.PENDIENTE_PAGO)));

        assertThatThrownBy(() -> service.registrar(
            eventoId, inscripcionId, true, "ana.admin@javeriana.edu.co", null, admin))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("confirmadas");

        verifyNoInteractions(asistenciaRepository);
    }

    @Test
    @DisplayName("Organizador no propietario no puede marcar asistencia")
    void registrar_organizadorNoPropietario_rechaza() {
        JwtPrincipal otroOrganizador = new JwtPrincipal(UUID.randomUUID(), List.of("ORGANIZADOR"),
            "otro@javeriana.edu.co", "Otro Organizador");
        when(eventoService.obtenerEvento(eventoId)).thenReturn(eventoInfo());

        assertThatThrownBy(() -> service.registrar(
            eventoId, inscripcionId, true, "otro@javeriana.edu.co", null, otroOrganizador))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("organizador propietario");
    }

    private EventoServicePort.EventoInfo eventoInfo() {
        return new EventoServicePort.EventoInfo(
            eventoId,
            "Seminario de Arquitectura",
            "PUBLICADO",
            10,
            true,
            organizadorId,
            "VIRTUAL",
            LocalDate.of(2033, 11, 10)
        );
    }

    private Inscripcion inscripcion(EstadoInscripcion estado) {
        return inscripcion(inscripcionId, participanteId, estado);
    }

    private Inscripcion inscripcion(UUID id, UUID usuarioId, EstadoInscripcion estado) {
        return new Inscripcion(
            id,
            usuarioId,
            eventoId,
            UUID.randomUUID(),
            estado,
            Instant.now(),
            null,
            "QR-test",
            UUID.randomUUID(),
            0
        );
    }
}
