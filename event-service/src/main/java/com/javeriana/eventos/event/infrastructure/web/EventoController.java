package com.javeriana.eventos.event.infrastructure.web;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.model.EstadoEvento;
import com.javeriana.eventos.event.domain.model.ModalidadEvento;
import com.javeriana.eventos.event.domain.model.TipoEvento;
import com.javeriana.eventos.event.domain.port.in.ActualizarEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.CancelarEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.ConsultarCatalogoUseCase;
import com.javeriana.eventos.event.domain.port.in.CrearEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.GestionarWorkflowEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.LiberarCupoUseCase;
import com.javeriana.eventos.event.domain.port.in.PublicarEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.ReservarCupoUseCase;
import com.javeriana.eventos.event.domain.port.out.EventoRepository;
import com.javeriana.eventos.event.infrastructure.security.JwtPrincipal;
import com.javeriana.eventos.event.infrastructure.web.dto.CrearEventoRequest;
import com.javeriana.eventos.event.infrastructure.web.dto.EventoResponse;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller REST para Evento.
 *
 * Prompt 21 — JWT/RBAC:
 *   @AuthenticationPrincipal reemplaza @RequestHeader("X-User-Id") en los
 *   endpoints de escritura. Los endpoints de lectura son públicos (permitAll).
 */
@RestController
@RequestMapping("/api/v1/eventos")
public class EventoController {

    private final CrearEventoUseCase       crearEvento;
    private final ActualizarEventoUseCase  actualizarEvento;
    private final GestionarWorkflowEventoUseCase workflowEvento;
    private final PublicarEventoUseCase    publicarEvento;
    private final CancelarEventoUseCase    cancelarEvento;
    private final ConsultarCatalogoUseCase consultarCatalogo;
    private final LiberarCupoUseCase       liberarCupoUseCase;
    private final ReservarCupoUseCase      reservarCupoUseCase;
    private final EventoRepository         eventoRepository;

    public EventoController(CrearEventoUseCase crearEvento,
                            ActualizarEventoUseCase actualizarEvento,
                            GestionarWorkflowEventoUseCase workflowEvento,
                            PublicarEventoUseCase publicarEvento,
                            CancelarEventoUseCase cancelarEvento,
                            ConsultarCatalogoUseCase consultarCatalogo,
                            LiberarCupoUseCase liberarCupoUseCase,
                            ReservarCupoUseCase reservarCupoUseCase,
                            EventoRepository eventoRepository) {
        this.crearEvento         = crearEvento;
        this.actualizarEvento    = actualizarEvento;
        this.workflowEvento      = workflowEvento;
        this.publicarEvento      = publicarEvento;
        this.cancelarEvento      = cancelarEvento;
        this.consultarCatalogo   = consultarCatalogo;
        this.liberarCupoUseCase  = liberarCupoUseCase;
        this.reservarCupoUseCase = reservarCupoUseCase;
        this.eventoRepository    = eventoRepository;
    }

    // ─── Comandos ────────────────────────────────────────────────────────────────

    /**
     * Requiere rol ORGANIZADOR o ADMIN (SecurityConfig).
     * El organizadorId proviene del JWT validado, no de un header HTTP falsificable.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventoResponse crear(@Valid @RequestBody CrearEventoRequest request,
                                @AuthenticationPrincipal JwtPrincipal principal) {
        JwtPrincipal solicitante = requirePrincipal(principal);
        if (!solicitante.esOrganizador()) {
            throw new SecurityException("Solo ORGANIZADOR puede crear eventos");
        }

        Evento evento = crearEvento.crear(new CrearEventoUseCase.Command(
            request.titulo(), request.descripcion(),
            TipoEvento.valueOf(request.tipo()),
            ModalidadEvento.valueOf(request.modalidad()),
            request.fechaInicio(), request.fechaFin(),
            request.fechaLimiteInscripcion(), request.cupoMaximo(), solicitante.userId()
        ));

        return EventoResponse.from(evento);
    }

    @PutMapping("/{id}")
    public EventoResponse actualizar(@PathVariable UUID id,
                                     @Valid @RequestBody CrearEventoRequest request,
                                     @AuthenticationPrincipal JwtPrincipal principal) {
        UUID solicitanteId = requirePrincipal(principal).userId();
        Evento evento = actualizarEvento.actualizar(new ActualizarEventoUseCase.Command(
            id,
            request.titulo(), request.descripcion(),
            TipoEvento.valueOf(request.tipo()),
            ModalidadEvento.valueOf(request.modalidad()),
            request.fechaInicio(), request.fechaFin(),
            request.fechaLimiteInscripcion(), request.cupoMaximo(),
            request.estado() != null ? EstadoEvento.valueOf(request.estado()) : null,
            solicitanteId,
            principal.esAdmin()
        ));
        return EventoResponse.from(evento);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id,
                         @AuthenticationPrincipal JwtPrincipal principal) {
        JwtPrincipal solicitante = requirePrincipal(principal);
        Evento evento = eventoRepository.buscarPorId(id)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado: " + id));
        if (!solicitante.esAdmin() && !evento.getOrganizadorId().equals(solicitante.userId())) {
            throw new SecurityException("Solo el organizador propietario o ADMIN puede eliminar este evento");
        }
        if (!solicitante.esAdmin() && evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new BusinessRuleViolationException(
                "RN-EVENTO-14",
                "El organizador solo puede eliminar eventos en BORRADOR"
            );
        }
        cancelarEvento.cancelar(id, solicitante.userId(), "Eliminado desde CRUD de eventos");
    }

    @PostMapping("/{id}/enviar-revision")
    public EventoResponse enviarARevision(@PathVariable UUID id,
                                          @AuthenticationPrincipal JwtPrincipal principal) {
        JwtPrincipal solicitante = requirePrincipal(principal);
        Evento evento = workflowEvento.enviarARevision(id, solicitante.userId());
        return EventoResponse.from(evento);
    }

    @PostMapping("/{id}/aprobar")
    public EventoResponse aprobar(@PathVariable UUID id,
                                  @AuthenticationPrincipal JwtPrincipal principal) {
        JwtPrincipal admin = requirePrincipal(principal);
        Evento evento = workflowEvento.aprobar(id, admin.userId());
        return EventoResponse.from(evento);
    }

    @PostMapping("/{id}/rechazar")
    public EventoResponse rechazar(@PathVariable UUID id,
                                   @RequestBody RechazarEventoRequest request,
                                   @AuthenticationPrincipal JwtPrincipal principal) {
        JwtPrincipal admin = requirePrincipal(principal);
        Evento evento = workflowEvento.rechazar(id, admin.userId(), request.motivo());
        return EventoResponse.from(evento);
    }

    @PostMapping("/{id}/publicar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publicar(@PathVariable UUID id,
                         @AuthenticationPrincipal JwtPrincipal principal) {
        UUID solicitanteId = principal != null ? principal.userId() : UUID.randomUUID();
        publicarEvento.publicar(id, solicitanteId);
    }

    @PostMapping("/{id}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable UUID id,
                         @RequestParam(defaultValue = "Sin motivo especificado") String motivo,
                         @AuthenticationPrincipal JwtPrincipal principal) {
        JwtPrincipal solicitante = requirePrincipal(principal);
        Evento evento = eventoRepository.buscarPorId(id)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado: " + id));
        if (!solicitante.esAdmin() && !evento.getOrganizadorId().equals(solicitante.userId())) {
            throw new SecurityException("Solo el organizador propietario o ADMIN puede cancelar este evento");
        }
        cancelarEvento.cancelar(id, solicitante.userId(), motivo);
    }

    /** C-02 Prompt 17: path /cupos/liberar para inscription-service Feign. */
    @PostMapping("/{id}/cupos/liberar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void liberarCupo(@PathVariable UUID id) {
        liberarCupoUseCase.liberar(id);
    }

    @PostMapping("/{id}/cupos/reservar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reservarCupo(@PathVariable UUID id) {
        reservarCupoUseCase.reservar(id);
    }

    // ─── Consultas (públicas — permitAll en SecurityConfig) ────────────────────

    @GetMapping
    public List<EventoResponse> listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String modalidad,
            @RequestParam(required = false) Boolean conCupos,
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "false") boolean incluirPropios,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @AuthenticationPrincipal JwtPrincipal principal) {

        ConsultarCatalogoUseCase.Filtros filtros = new ConsultarCatalogoUseCase.Filtros(
            tipo      != null ? TipoEvento.valueOf(tipo)           : null,
            modalidad != null ? ModalidadEvento.valueOf(modalidad) : null,
            conCupos, buscar, pagina, tamano);

        if (principal != null && principal.esAdmin()) {
            List<Evento> eventos = estado != null
                ? eventoRepository.buscarPorEstado(EstadoEvento.valueOf(estado))
                : eventoRepository.buscarTodos();
            return aplicarFiltros(eventos, filtros).stream()
                .map(EventoResponse::from).toList();
        }

        if (principal != null && principal.esOrganizador() && incluirPropios) {
            List<Evento> eventos = Stream.concat(
                    eventoRepository.buscarPublicados().stream(),
                    eventoRepository.buscarPorOrganizadorId(principal.userId()).stream())
                .collect(Collectors.toMap(Evento::getId, Function.identity(), (actual, duplicado) -> actual))
                .values().stream().toList();
            if (estado != null) {
                EstadoEvento estadoFiltro = EstadoEvento.valueOf(estado);
                eventos = eventos.stream()
                    .filter(evento -> evento.getEstado() == estadoFiltro)
                    .toList();
            }
            return aplicarFiltros(eventos, filtros).stream()
                .map(EventoResponse::from).toList();
        }

        return consultarCatalogo.listarPublicados(filtros).stream()
            .map(EventoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventoResponse> buscarPorId(@PathVariable UUID id) {
        return consultarCatalogo.buscarPorId(id)
            .map(e -> ResponseEntity.ok(EventoResponse.from(e)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── Exception handlers ───────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "parametro_invalido", "message", ex.getMessage()));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Map<String, String>> handleBusinessRule(BusinessRuleViolationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of("error", ex.getRule(), "message", ex.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "acceso_denegado", "message", ex.getMessage()));
    }

    private JwtPrincipal requirePrincipal(JwtPrincipal principal) {
        if (principal == null) {
            throw new SecurityException("Operacion requiere autenticacion");
        }
        return principal;
    }

    private List<Evento> aplicarFiltros(List<Evento> eventos, ConsultarCatalogoUseCase.Filtros filtros) {
        return eventos.stream()
            .filter(e -> filtros.tipo() == null || e.getTipo() == filtros.tipo())
            .filter(e -> filtros.modalidad() == null || e.getModalidad() == filtros.modalidad())
            .filter(e -> filtros.conCuposDisponibles() == null
                || !filtros.conCuposDisponibles()
                || e.getCupoDisponible() > 0)
            .filter(e -> filtros.textoBusqueda() == null
                || e.getTitulo().toLowerCase().contains(filtros.textoBusqueda().toLowerCase())
                || e.getDescripcion().toLowerCase().contains(filtros.textoBusqueda().toLowerCase()))
            .skip((long) filtros.pagina() * filtros.tamano())
            .limit(filtros.tamano())
            .toList();
    }

    public record RechazarEventoRequest(String motivo) {}
}
