package com.javeriana.eventos.event.infrastructure.web.dto;

import com.javeriana.eventos.event.domain.model.Tarifa;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de respuesta para Tarifa.
 *
 * Campos alineados con EventoServicePort.TarifaInfo de inscription-service:
 *   record TarifaInfo(UUID id, BigDecimal monto, String moneda, String descripcion)
 */
public record TarifaResponse(
    UUID id,
    BigDecimal monto,
    String moneda,
    String descripcion
) {
    public static TarifaResponse from(Tarifa tarifa) {
        return new TarifaResponse(
            tarifa.getId(),
            tarifa.getPrecio(),
            tarifa.getMoneda(),
            tarifa.getNombre()   // nombre → descripcion en el contrato Feign
        );
    }
}
