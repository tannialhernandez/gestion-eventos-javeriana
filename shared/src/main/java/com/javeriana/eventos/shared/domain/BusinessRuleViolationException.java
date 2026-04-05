package com.javeriana.eventos.shared.domain;

/**
 * Excepción de dominio: se lanza cuando se viola una invariante de negocio.
 *
 * Ejemplos:
 *  - Intentar confirmar una inscripción que ya está EXPIRADA
 *  - Intentar publicar un evento sin cupos definidos
 *  - Evaluador intentando evaluar su propia propuesta
 *
 * La capa de infraestructura (ExceptionHandler) mapea esta excepción a HTTP 422.
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final String rule;

    public BusinessRuleViolationException(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    public String getRule() {
        return rule;
    }
}
