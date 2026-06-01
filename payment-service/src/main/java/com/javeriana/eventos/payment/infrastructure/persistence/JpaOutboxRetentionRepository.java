package com.javeriana.eventos.payment.infrastructure.persistence;

import com.javeriana.eventos.payment.domain.port.out.OutboxRetentionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class JpaOutboxRetentionRepository implements OutboxRetentionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JpaOutboxRetentionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public int eliminarEnviadosAnterioresA(Instant umbral, int batchSize) {
        return jdbcTemplate.update("""
            DELETE FROM outbox_events
            WHERE id IN (
                SELECT id FROM outbox_events
                WHERE estado = 'ENVIADO' AND enviado_en < ?
                LIMIT ?
            )
            """, Timestamp.from(umbral), batchSize);
    }

    @Override
    @Transactional
    public int eliminarFallidosAnterioresA(Instant umbral, int batchSize) {
        return jdbcTemplate.update("""
            DELETE FROM outbox_events
            WHERE id IN (
                SELECT id FROM outbox_events
                WHERE estado = 'FALLIDO' AND intentos >= 10 AND creado_en < ?
                LIMIT ?
            )
            """, Timestamp.from(umbral), batchSize);
    }

    @Override
    @Transactional
    public int eliminarMensajesProcesadosAnterioresA(Instant umbral, int batchSize) {
        // payment-service uses processed_at and consumer_group (English columns)
        return jdbcTemplate.update("""
            DELETE FROM mensaje_procesado
            WHERE (message_id, consumer_group) IN (
                SELECT message_id, consumer_group FROM mensaje_procesado
                WHERE processed_at < ?
                LIMIT ?
            )
            """, Timestamp.from(umbral), batchSize);
    }
}
