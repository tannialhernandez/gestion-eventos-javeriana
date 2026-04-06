package com.javeriana.eventos.inscription.domain.port.out;

import com.javeriana.eventos.shared.infrastructure.outbox.OutboxEvent;

import java.util.List;

/**
 * Port de salida para el Outbox Pattern.
 *
 * Los eventos de dominio se persisten en outbox_events dentro de la misma
 * transacción que la operación de negocio. El OutboxRelayService los lee
 * periódicamente y los publica en RabbitMQ.
 */
public interface OutboxEventRepository {

    void guardar(OutboxEvent event);

    List<OutboxEvent> buscarNoPublicados();

    void marcarComoPublicado(OutboxEvent event);
}
