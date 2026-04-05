package com.javeriana.eventos.event.infrastructure.web;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.model.ModalidadEvento;
import com.javeriana.eventos.event.domain.model.TipoEvento;
import com.javeriana.eventos.event.domain.port.in.ConsultarCatalogoUseCase;
import com.javeriana.eventos.event.domain.port.in.CrearEventoUseCase;
import com.javeriana.eventos.event.domain.port.in.PublicarEventoUseCase;
import com.javeriana.eventos.event.infrastructure.web.dto.CrearEventoRequest;
import com.javeriana.eventos.event.infrastructure.web.dto.EventoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/eventos")
public class EventoController {

    private final CrearEventoUseCase crearEvento;
    private final PublicarEventoUseCase publicarEvento;
    private final ConsultarCatalogoUseCase consultarCatalogo;

    public EventoController(CrearEventoUseCase crearEvento,
                            PublicarEventoUseCase publicarEvento,
                            ConsultarCatalogoUseCase consultarCatalogo) {
        this.crearEvento = crearEvento;
        this.publicarEvento = publicarEvento;
        this.consultarCatalogo = consultarCatalogo;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventoResponse crear(@Valid @RequestBody CrearEventoRequest request,
                                @RequestHeader("X-User-Id") UUID organizadorId) {
        Evento evento = crearEvento.crear(new CrearEventoUseCase.Command(
            request.titulo(),
            request.descripcion(),
            TipoEvento.valueOf(request.tipo()),
            ModalidadEvento.valueOf(request.modalidad()),
            request.fechaInicio(),
            request.fechaFin(),
            request.fechaLimiteInscripcion(),
            request.cupoMaximo(),
            organizadorId
        ));
        return EventoResponse.from(evento);
    }

    @PostMapping("/{id}/publicar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publicar(@PathVariable UUID id,
                         @RequestHeader("X-User-Id") UUID solicitanteId) {
        publicarEvento.publicar(id, solicitanteId);
    }

    @GetMapping
    public List<EventoResponse> listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String modalidad,
            @RequestParam(required = false) Boolean conCupos,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        ConsultarCatalogoUseCase.Filtros filtros = new ConsultarCatalogoUseCase.Filtros(
            tipo != null ? TipoEvento.valueOf(tipo) : null,
            modalidad != null ? ModalidadEvento.valueOf(modalidad) : null,
            conCupos,
            buscar,
            pagina,
            tamano
        );
        return consultarCatalogo.listarPublicados(filtros).stream()
            .map(EventoResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventoResponse> buscarPorId(@PathVariable UUID id) {
        return consultarCatalogo.buscarPorId(id)
            .map(e -> ResponseEntity.ok(EventoResponse.from(e)))
            .orElse(ResponseEntity.notFound().build());
    }
}
