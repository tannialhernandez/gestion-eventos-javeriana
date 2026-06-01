package com.javeriana.eventos.payment.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * DTO tipado para el payload del Webhook de la pasarela.
 *
 * m-01 (Prompt 18/19): reemplaza el Map genérico que causaba NPE en
 * UUID.fromString("") cuando los campos eran nulos o vacíos.
 *
 * Mapeo snake_case → camelCase via @JsonProperty.
 * @JsonIgnoreProperties(ignoreUnknown = true): tolera campos extra de la pasarela
 * sin lanzar DeserializationException (el payload de MercadoPago es extenso).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPagoRequest(

    @JsonProperty("referencia_externa")
    String referenciaExterna,

    @JsonProperty("inscripcion_id")
    String inscripcionId,

    @JsonProperty("estado")
    String estado,

    @JsonProperty("metadata")
    Map<String, Object> metadata

) {
    /**
     * Valida que los campos requeridos están presentes y no vacíos.
     * Si alguno falta, el controller retorna HTTP 400 en lugar de HTTP 500.
     */
    public boolean camposRequeridosPresentes() {
        return referenciaExterna != null && !referenciaExterna.isBlank()
            && inscripcionId     != null && !inscripcionId.isBlank()
            && estado            != null && !estado.isBlank();
    }
}
