package com.javeriana.eventos.event.domain.port.in;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.model.ModalidadEvento;
import com.javeriana.eventos.event.domain.model.TipoEvento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de entrada — CQRS: Query side.
 *
 * Las lecturas del catálogo usan Redis como fuente primaria (< 300ms p95).
 * Si hay miss en cache, se lee de PostgreSQL y se repopula el cache.
 * Este port es el contrato de la operación de consulta, independiente
 * de si la fuente es Redis o PostgreSQL.
 */
public interface ConsultarCatalogoUseCase {

    List<Evento> listarPublicados(Filtros filtros);

    Optional<Evento> buscarPorId(UUID eventoId);

    record Filtros(
        TipoEvento tipo,
        ModalidadEvento modalidad,
        Boolean conCuposDisponibles,
        String textoBusqueda,
        int pagina,
        int tamano
    ) {
        public static Filtros sinFiltros() {
            return new Filtros(null, null, null, null, 0, 20);
        }
    }
}
