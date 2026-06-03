package com.javeriana.eventos.inscription.infrastructure.clientes;

import com.javeriana.eventos.inscription.domain.port.out.PaymentServicePort.PreferenciaPago;
import com.javeriana.eventos.inscription.domain.port.out.PaymentServicePort.ReembolsoPago;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cliente Feign hacia payment-service.
 *
 * Delega la creación de la preferencia de pago a payment-service, que es
 * dueño de la integración con la pasarela externa (MercadoPago, Simulador).
 */
@FeignClient(name = "payment-service", url = "${services.payment-service.url}")
public interface PaymentServiceFeignClient {

    @PostMapping("/api/v1/pagos/preferencias")
    PreferenciaPago crearPreferencia(@RequestBody CrearPreferenciaRequest request);

    @PostMapping("/api/v1/pagos/simulador/{inscripcionId}/reembolsar")
    ReembolsoPago reembolsar(@PathVariable("inscripcionId") UUID inscripcionId);

    /**
     * DTO de request para la creación de preferencia de pago.
     * Encapsula los parámetros que payment-service necesita de inscription-service.
     */
    record CrearPreferenciaRequest(
        UUID inscripcionId,
        BigDecimal monto,
        String moneda,
        UUID usuarioId
    ) {}
}
