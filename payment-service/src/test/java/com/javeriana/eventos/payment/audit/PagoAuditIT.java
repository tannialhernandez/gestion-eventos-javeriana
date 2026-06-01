package com.javeriana.eventos.payment.audit;

import com.javeriana.eventos.payment.domain.audit.EventoAuditoria;
import com.javeriana.eventos.payment.domain.model.EstadoPago;
import com.javeriana.eventos.payment.domain.port.in.CrearPreferenciaUseCase;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase;
import com.javeriana.eventos.payment.domain.port.out.AuditoriaPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de integración para el audit log de pagos (Ley 1581 Art. 11).
 *
 * Verifica:
 * 1. Cada transicion de estado genera una entrada en pago_audit.
 * 2. La tabla es append-only (UPDATE/DELETE lanzan excepcion via trigger SQL).
 * 3. El historial de un pago se retorna en orden cronologico ascendente.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("PagoAuditIT — Audit log Ley 1581")
class PagoAuditIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("eventos_payment")
            .withUsername("test_user")
            .withPassword("test_pass");

    @Container
    static final RabbitMQContainer RABBITMQ =
        new RabbitMQContainer("rabbitmq:3-management-alpine");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host",       RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port",       RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username",   () -> "guest");
        registry.add("spring.rabbitmq.password",   () -> "guest");
        registry.add("payment.gateway.provider",   () -> "simulador");
    }

    @Autowired private CrearPreferenciaUseCase crearPreferenciaUseCase;
    @Autowired private ProcesarWebhookUseCase  procesarWebhookUseCase;
    @Autowired private AuditoriaPagoRepository auditoriaRepo;
    @Autowired private JdbcTemplate            jdbcTemplate;

    private UUID inscripcionId;

    @BeforeEach
    void setUp() {
        inscripcionId = UUID.randomUUID();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CrearPreferenciaUseCase.Result crearPago(BigDecimal monto) {
        return crearPreferenciaUseCase.crear(
            new CrearPreferenciaUseCase.Command(inscripcionId, monto, "COP", UUID.randomUUID()));
    }

    private ProcesarWebhookUseCase.WebhookPayload webhookAprobado(String refExt) {
        return new ProcesarWebhookUseCase.WebhookPayload(
            refExt, inscripcionId.toString(), "approved", "{}");
    }

    private ProcesarWebhookUseCase.WebhookPayload webhookRechazado(String refExt) {
        return new ProcesarWebhookUseCase.WebhookPayload(
            refExt, inscripcionId.toString(), "rejected", "{}");
    }

    // ─── tests ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Crear preferencia registra 2 entradas: null→INICIADO y INICIADO→PROCESANDO")
    void crearPreferencia_debeRegistrarDosEntradasAudit() {
        CrearPreferenciaUseCase.Result result = crearPago(new BigDecimal("50000"));

        List<EventoAuditoria> entradas = auditoriaRepo.buscarPorPago(result.pagoId());

        assertThat(entradas).hasSize(2);
        assertThat(entradas.get(0).estadoAnterior()).isNull();
        assertThat(entradas.get(0).estadoNuevo()).isEqualTo(EstadoPago.INICIADO.name());
        assertThat(entradas.get(1).estadoAnterior()).isEqualTo(EstadoPago.INICIADO.name());
        assertThat(entradas.get(1).estadoNuevo()).isEqualTo(EstadoPago.PROCESANDO.name());
    }

    @Test
    @DisplayName("Webhook aprobado agrega entrada PROCESANDO→CONFIRMADO")
    void webhookAprobado_debeRegistrarTransicionConfirmado() {
        CrearPreferenciaUseCase.Result result = crearPago(new BigDecimal("30000"));
        procesarWebhookUseCase.procesar(webhookAprobado("REF-OK-" + UUID.randomUUID()));

        List<EventoAuditoria> entradas = auditoriaRepo.buscarPorPago(result.pagoId());
        EventoAuditoria ultima = entradas.get(entradas.size() - 1);

        assertThat(ultima.estadoNuevo()).isEqualTo(EstadoPago.CONFIRMADO.name());
        assertThat(ultima.estadoAnterior()).isEqualTo(EstadoPago.PROCESANDO.name());
    }

    @Test
    @DisplayName("Webhook rechazado agrega entrada →FALLIDO")
    void webhookRechazado_debeRegistrarTransicionFallido() {
        CrearPreferenciaUseCase.Result result = crearPago(new BigDecimal("20000"));
        procesarWebhookUseCase.procesar(webhookRechazado("REF-FAIL-" + UUID.randomUUID()));

        List<EventoAuditoria> entradas = auditoriaRepo.buscarPorPago(result.pagoId());
        EventoAuditoria ultima = entradas.get(entradas.size() - 1);

        assertThat(ultima.estadoNuevo()).isEqualTo(EstadoPago.FALLIDO.name());
    }

    @Test
    @DisplayName("Historial de pago se retorna en orden cronologico ascendente")
    void historial_debeEstarOrdenadoCronologicamente() {
        CrearPreferenciaUseCase.Result result = crearPago(new BigDecimal("75000"));
        procesarWebhookUseCase.procesar(webhookAprobado("REF-ORD-" + UUID.randomUUID()));

        List<EventoAuditoria> entradas = auditoriaRepo.buscarPorPago(result.pagoId());
        for (int i = 1; i < entradas.size(); i++) {
            assertThat(entradas.get(i).ocurridoEn())
                .isAfterOrEqualTo(entradas.get(i - 1).ocurridoEn());
        }
    }

    @Test
    @DisplayName("UPDATE en pago_audit lanza excepcion (trigger append-only Ley 1581)")
    void update_debeSerBloqueadoPorTrigger() {
        crearPago(new BigDecimal("10000"));

        assertThatThrownBy(() ->
            jdbcTemplate.execute(
                "UPDATE pago_audit SET actor = 'HACKER' WHERE actor LIKE 'SISTEMA%'"))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("inmutable");
    }

    @Test
    @DisplayName("DELETE en pago_audit lanza excepcion (trigger append-only Ley 1581)")
    void delete_debeSerBloqueadoPorTrigger() {
        crearPago(new BigDecimal("10000"));

        assertThatThrownBy(() ->
            jdbcTemplate.execute("DELETE FROM pago_audit WHERE 1=1"))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("inmutable");
    }
}
