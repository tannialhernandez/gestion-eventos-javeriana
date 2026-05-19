package com.javeriana.eventos.payment.domain;

import com.javeriana.eventos.payment.domain.events.PagoReembolsadoEvent;
import com.javeriana.eventos.payment.domain.model.EstadoPago;
import com.javeriana.eventos.payment.domain.model.Pago;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import com.javeriana.eventos.shared.domain.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios del agregado Pago — sin Spring, sin mocks.
 *
 * Cubre la máquina de estados declarada en SRS §9.3.3 y la regla
 * RN-PAGO-05 (propuesta, docs/srs-casos-uso-pendientes.md §11).
 */
class PagoTest {

    private static final UUID PAGO_ID         = UUID.randomUUID();
    private static final UUID INSCRIPCION_ID  = UUID.randomUUID();
    private static final BigDecimal MONTO     = new BigDecimal("50000.00");

    // ─── Helpers de construcción ─────────────────────────────────────────────

    /** Pago recién creado → estado INICIADO */
    private Pago pagoIniciado() {
        return new Pago(PAGO_ID, INSCRIPCION_ID, MONTO, "COP", "SIMULADOR");
    }

    /** Pago tras recibir preferencia → estado PROCESANDO */
    private Pago pagoProcesando() {
        Pago pago = pagoIniciado();
        pago.registrarPreferencia("PREF-TEST-001");
        return pago;
    }

    /** Pago reconstruido desde persistencia con un estado específico */
    private Pago pagoEnEstado(EstadoPago estado) {
        return new Pago(
            PAGO_ID, INSCRIPCION_ID, MONTO, "COP", "SIMULADOR",
            "REF-EXT-001", "PREF-001",
            estado, Instant.now(), null, null, 0, null, 0
        );
    }

    // ─── reembolsarPorExpiracion — happy paths ───────────────────────────────

    @Test
    @DisplayName("INICIADO → reembolsarPorExpiracion() → REEMBOLSADO + PagoReembolsadoEvent publicado")
    void reembolsarPorExpiracion_desdeIniciado_transicionaYRegistraEvento() {
        Pago pago = pagoIniciado();

        pago.reembolsarPorExpiracion();

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
        assertThat(pago.getFechaReembolso()).isNotNull();

        List<DomainEvent> events = pago.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PagoReembolsadoEvent.class);

        PagoReembolsadoEvent event = (PagoReembolsadoEvent) events.get(0);
        assertThat(event.aggregateId()).isEqualTo(PAGO_ID);
        assertThat(event.inscripcionId()).isEqualTo(INSCRIPCION_ID);
        assertThat(event.eventType()).isEqualTo("PAGO_REEMBOLSADO");
    }

    @Test
    @DisplayName("PROCESANDO → reembolsarPorExpiracion() → REEMBOLSADO + PagoReembolsadoEvent publicado")
    void reembolsarPorExpiracion_desdeProcesando_transicionaYRegistraEvento() {
        Pago pago = pagoProcesando();

        pago.reembolsarPorExpiracion();

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
        assertThat(pago.getFechaReembolso()).isNotNull();

        List<DomainEvent> events = pago.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PagoReembolsadoEvent.class);
    }

    // ─── reembolsarPorExpiracion — guards (RN-PAGO-05) ──────────────────────

    @Test
    @DisplayName("CONFIRMADO (esFinal=true) → reembolsarPorExpiracion() → BusinessRuleViolationException")
    void reembolsarPorExpiracion_desdeConfirmado_lanzaExcepcion() {
        Pago pago = pagoEnEstado(EstadoPago.CONFIRMADO);

        assertThatThrownBy(pago::reembolsarPorExpiracion)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("CONFIRMADO")
            .extracting(ex -> ((BusinessRuleViolationException) ex).getRule())
            .isEqualTo("RN-PAGO-05");
    }

    @Test
    @DisplayName("FALLIDO (esFinal=true) → reembolsarPorExpiracion() → BusinessRuleViolationException")
    void reembolsarPorExpiracion_desdeFallido_lanzaExcepcion() {
        Pago pago = pagoEnEstado(EstadoPago.FALLIDO);

        assertThatThrownBy(pago::reembolsarPorExpiracion)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("FALLIDO")
            .extracting(ex -> ((BusinessRuleViolationException) ex).getRule())
            .isEqualTo("RN-PAGO-05");
    }

    @Test
    @DisplayName("REEMBOLSADO (esFinal=true) → reembolsarPorExpiracion() → BusinessRuleViolationException")
    void reembolsarPorExpiracion_desdeReembolsado_lanzaExcepcion() {
        Pago pago = pagoEnEstado(EstadoPago.REEMBOLSADO);

        assertThatThrownBy(pago::reembolsarPorExpiracion)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("REEMBOLSADO")
            .extracting(ex -> ((BusinessRuleViolationException) ex).getRule())
            .isEqualTo("RN-PAGO-05");
    }
}
