package com.javeriana.eventos.payment.domain.port.out;

import com.javeriana.eventos.shared.infrastructure.outbox.OutboxEvent;

import java.util.List;

public interface OutboxEventRepository {
    void guardar(OutboxEvent event);
    List<OutboxEvent> buscarNoPublicados();
    void marcarComoPublicado(OutboxEvent event);
}
