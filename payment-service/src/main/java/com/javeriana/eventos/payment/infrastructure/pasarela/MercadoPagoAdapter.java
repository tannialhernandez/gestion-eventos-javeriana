package com.javeriana.eventos.payment.infrastructure.pasarela;

import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador MercadoPago — instanciado por DefaultPasarelaPagoFactory cuando
 * payment.gateway.provider=mercadopago.
 *
 * Implementa PasarelaPagoPort llamando a la API real de MercadoPago.
 * El Circuit Breaker en CrearPreferenciaService lo envuelve exactamente
 * igual que al SimuladorPasarelaAdapter — el dominio no distingue cuál se usa.
 *
 * Para activar: payment.gateway.provider=mercadopago + MERCADOPAGO_ACCESS_TOKEN en .env
 * Documentación API: https://www.mercadopago.com.co/developers/es/reference
 */
public class MercadoPagoAdapter implements PasarelaPagoPort {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoAdapter.class);
    private static final String MP_API_BASE = "https://api.mercadopago.com";

    private final WebClient webClient;
    private final String accessToken;

    public MercadoPagoAdapter(String accessToken) {
        this.accessToken = accessToken;
        this.webClient = WebClient.builder()
            .baseUrl(MP_API_BASE)
            .defaultHeader("Authorization", "Bearer " + accessToken)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public PreferenciaPago crearPreferencia(UUID pagoId, UUID inscripcionId,
                                             BigDecimal monto, String moneda) {
        // Crea una preference en MercadoPago
        // external_reference = inscripcionId para correlacionar el webhook
        Map<String, Object> body = Map.of(
            "items", java.util.List.of(Map.of(
                "title", "Inscripción a Evento Académico",
                "quantity", 1,
                "unit_price", monto,
                "currency_id", moneda
            )),
            "external_reference", inscripcionId.toString(),
            "metadata", Map.of("pago_id", pagoId.toString()),
            "notification_url", "${mercadopago.webhook-url}",  // configurado en application.yml
            "expires", true,
            "expiration_date_to", java.time.Instant.now()
                .plusSeconds(16 * 60).toString()  // 16 min (1 min más que el timeout de inscripción)
        );

        Map<?, ?> response = webClient.post()
            .uri("/checkout/preferences")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block();  // Síncrono — el Circuit Breaker maneja el timeout via TimeLimiter

        String preferenciaId = (String) response.get("id");
        String checkoutUrl = (String) response.get("init_point");

        log.info("[MERCADOPAGO] Preferencia creada: {} para inscripción: {}",
            preferenciaId, inscripcionId);

        return new PreferenciaPago(preferenciaId, checkoutUrl);
    }

    @Override
    public ReembolsoResult reembolsar(String referenciaExterna, BigDecimal monto) {
        Map<String, Object> body = Map.of("amount", monto);

        Map<?, ?> response = webClient.post()
            .uri("/v1/payments/{id}/refunds", referenciaExterna)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        String reembolsoId = String.valueOf(response.get("id"));
        log.info("[MERCADOPAGO] Reembolso {} emitido para pago: {}", reembolsoId, referenciaExterna);

        return new ReembolsoResult(reembolsoId, true);
    }
}
