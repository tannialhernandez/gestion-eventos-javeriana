package com.javeriana.eventos.inscription.infrastructure.outbox;

import com.javeriana.eventos.inscription.domain.port.out.OutboxRetentionRepository;
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
        return jdbcTemplate.update("""
            DELETE FROM mensaje_procesado
            WHERE (message_id, consumer_grupo) IN (
                SELECT message_id, consumer_grupo FROM mensaje_procesado
                WHERE procesado_en < ?
                LIMIT ?
            )
            """, Timestamp.from(umbral), batchSize);
    }
}
