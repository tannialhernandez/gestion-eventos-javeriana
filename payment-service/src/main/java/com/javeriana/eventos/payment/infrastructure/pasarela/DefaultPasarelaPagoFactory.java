package com.javeriana.eventos.payment.infrastructure.pasarela;

import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoFactory;
import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ConcreteCreator del patrón Factory Method (GoF — Creacional).
 *
 * Resuelve qué adaptador de pasarela instanciar según la propiedad:
 *   payment.gateway.provider: mercadopago | simulador  (default: simulador)
 *
 * OCP: añadir un nuevo proveedor (ej. PayU) solo requiere agregar una clase
 * que implemente PasarelaPagoPort y una rama en el switch — sin modificar
 * CrearPreferenciaService ni ningún otro servicio.
 *
 * El adaptador se crea una sola vez en el constructor (singleton de facto
 * dentro del contexto Spring) para evitar la overhead de instanciación por llamada.
 */
@Component
public class DefaultPasarelaPagoFactory implements PasarelaPagoFactory {

    private static final Logger log = LoggerFactory.getLogger(DefaultPasarelaPagoFactory.class);

    private final PasarelaPagoPort pasarela;

    public DefaultPasarelaPagoFactory(
            @Value("${payment.gateway.provider:simulador}") String provider,
            @Value("${mercadopago.access-token:}") String mpAccessToken,
            @Value("${mercadopago.webhook-url:http://localhost:8084/api/v1/webhooks/pagos}") String webhookUrl,
            @Value("${payment.gateway.simulator-public-base-url:http://localhost:8084}") String simulatorPublicBaseUrl) {

        this.pasarela = switch (provider.toLowerCase()) {
            case "mercadopago" -> {
                log.info("[PasarelaPagoFactory] Usando adaptador MercadoPago (producción). webhookUrl={}",
                    webhookUrl);
                yield new MercadoPagoAdapter(mpAccessToken, webhookUrl);
            }
            default -> {
                log.info("[PasarelaPagoFactory] Usando SimuladorPasarelaAdapter (demo/test).");
                yield new SimuladorPasarelaAdapter(simulatorPublicBaseUrl);
            }
        };
    }

    @Override
    public PasarelaPagoPort crearPasarela() {
        return this.pasarela;
    }
}
