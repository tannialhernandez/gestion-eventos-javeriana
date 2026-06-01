package com.javeriana.eventos.inscription.infrastructure.clientes;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Interceptor Feign que añade el Bearer token de servicio en llamadas a event-service.
 *
 * event-service requiere rol SERVICE en POST /cupos/** (ADR-021, Prompt 21).
 * El token se configura vía services.event-service.auth-token.
 *
 * En produccion, auth-service emite el token de larga duracion.
 * En local/test, se usa un JWT pre-generado con la clave RSA de prueba.
 *
 * Si el token no esta configurado, el header no se añade y la llamada puede
 * fallar con 401/403 en entornos con seguridad activa.
 */
@Component
public class EventoServiceAuthInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(EventoServiceAuthInterceptor.class);

    private final String serviceToken;

    public EventoServiceAuthInterceptor(
            @Value("${services.event-service.auth-token:}") String serviceToken) {
        this.serviceToken = serviceToken;
        if (serviceToken == null || serviceToken.isBlank()) {
            log.warn("[feign-auth] services.event-service.auth-token no configurado. " +
                     "Las llamadas a endpoints protegidos de event-service fallaran con 401.");
        }
    }

    @Override
    public void apply(RequestTemplate template) {
        if (serviceToken != null && !serviceToken.isBlank()) {
            template.header("Authorization", "Bearer " + serviceToken);
        }
    }
}
