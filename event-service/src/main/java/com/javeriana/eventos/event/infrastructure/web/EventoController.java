package com.javeriana.eventos.event.infrastructure.web;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.model.ModalidadEvento;
import com.javeriana.eventos.event.domain.model.TipoEvento;
import com.javeriana.eventos.event.domain.port.in.CancelarEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.ConsultarCatalogoUseCase;
import com.javeriana.eventos.event.domain.port.in.CrearEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.LiberarCupoUseCase;
import com.javeriana.eventos.event.domain.port.in.PublicarEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.ReservarCupoUseCase;
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
    private final PublicarEventoUseCase    publicarEvento;
    private final CancelarEventoUseCase    cancelarEvento;
    private final ConsultarCatalogoUseCase consultarCatalogo;
    private final LiberarCupoUseCase       liberarCupoUseCase;
    private final ReservarCupoUseCase      reservarCupoUseCase;

    public EventoController(CrearEventoUseCase crearEvento,
                            PublicarEventoUseCase publicarEvento,
                            CancelarEventoUseCase cancelarEvento,
                            ConsultarCatalogoUseCase consultarCatalogo,
                            LiberarCupoUseCase liberarCupoUseCase,
                            ReservarCupoUseCase reservarCupoUseCase) {
        this.crearEvento         = crearEvento;
        this.publicarEvento      = publicarEvento;
        this.cancelarEvento      = cancelarEvento;
        this.consultarCatalogo   = consultarCatalogo;
        this.liberarCupoUseCase  = liberarCupoUseCase;
        this.reservarCupoUseCase = reservarCupoUseCase;
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
        UUID organizadorId = principal != null ? principal.userId() : UUID.randomUUID();
        Evento evento = crearEvento.crear(new CrearEventoUseCase.Command(
            request.titulo(), request.descripcion(),
            TipoEvento.valueOf(request.tipo()),
            ModalidadEvento.valueOf(request.modalidad()),
            request.fechaInicio(), request.fechaFin(),
            request.fechaLimiteInscripcion(), request.cupoMaximo(), organizadorId
        ));
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
        UUID solicitanteId = principal != null ? principal.userId() : UUID.randomUUID();
        cancelarEvento.cancelar(id, solicitanteId, motivo);
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
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        ConsultarCatalogoUseCase.Filtros filtros = new ConsultarCatalogoUseCase.Filtros(
            tipo      != null ? TipoEvento.valueOf(tipo)           : null,
            modalidad != null ? ModalidadEvento.valueOf(modalidad) : null,
            conCupos, buscar, pagina, tamano);
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
}
