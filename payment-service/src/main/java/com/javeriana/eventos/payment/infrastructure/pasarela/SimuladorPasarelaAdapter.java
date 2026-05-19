package com.javeriana.eventos.payment.infrastructure.pasarela;

import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adaptador Simulador — instanciado por DefaultPasarelaPagoFactory cuando
 * payment.gateway.provider=simulador (desarrollo/demo/tests).
 *
 * Implementa PasarelaPagoPort con respuestas predecibles y sin llamadas HTTP.
 * El Circuit Breaker lo envuelve igual que al adaptador real, demostrando
 * que el patrón Factory Method es agnóstico al proveedor.
 *
 * Para la demo:
 * - crearPreferencia() retorna una URL de checkout interna (/simulador/pagos/{id}/aprobar)
 * - Disparar POST /api/v1/webhooks/pagos con estado "approved" simula el pago exitoso
 */
public class SimuladorPasarelaAdapter implements PasarelaPagoPort {

    private static final Logger log = LoggerFactory.getLogger(SimuladorPasarelaAdapter.class);

    @Override
    public PreferenciaPago crearPreferencia(UUID pagoId, UUID inscripcionId,
                                            BigDecimal monto, String moneda) {
        // Simula latencia de red (50ms)
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        String preferenciaId = "SIM-PREF-" + pagoId.toString().substring(0, 8).toUpperCase();

        // La "URL de checkout" apunta al endpoint de simulación del mismo servicio
        String checkoutUrl = "http://localhost:8084/api/v1/simulador/pagos/" + pagoId + "/aprobar";

        log.info("[SIMULADOR] Preferencia creada: {} | Monto: {} {} | Checkout: {}",
            preferenciaId, monto, moneda, checkoutUrl);

        return new PreferenciaPago(preferenciaId, checkoutUrl);
    }

    @Override
    public ReembolsoResult reembolsar(String referenciaExterna, BigDecimal monto) {
        String reembolsoId = "SIM-REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[SIMULADOR] Reembolso emitido: {} para referencia: {}", reembolsoId, referenciaExterna);
        return new ReembolsoResult(reembolsoId, true);
    }
}
