package com.javeriana.eventos.payment.domain.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port de salida — Pasarela de Pago.
 *
 * Define el contrato que deben cumplir TODOS los adaptadores de pasarela,
 * independientemente del proveedor. Esto es la esencia de Hexagonal Architecture:
 * el dominio define qué necesita, no cómo se obtiene.
 *
 * Implementaciones:
 *  - MercadoPagoAdapter  (profile: mercadopago) — llama a la API real de MercadoPago
 *  - SimuladorPasaraAdapter (profile: default)  — simula respuestas para demo/tests
 *
 * El Circuit Breaker (Resilience4j) envuelve AMBAS implementaciones en la
 * capa de aplicación, no en el adaptador — así el patrón es agnóstico al proveedor.
 */
public interface PasarelaPagoPort {

    /**
     * Crea una preferencia de pago en la pasarela.
     * Retorna la URL de checkout donde el usuario completa el pago.
     */
    PreferenciaPago crearPreferencia(UUID pagoId, UUID inscripcionId,
                                     BigDecimal monto, String moneda);

    /**
     * Emite un reembolso para un pago previamente confirmado.
     */
    ReembolsoResult reembolsar(String referenciaExterna, BigDecimal monto);

    record PreferenciaPago(
        String preferenciaId,    // ID en la pasarela (para trazabilidad)
        String checkoutUrl       // URL donde el usuario paga
    ) {}

    record ReembolsoResult(
        String reembolsoId,
        boolean exitoso
    ) {}
}
