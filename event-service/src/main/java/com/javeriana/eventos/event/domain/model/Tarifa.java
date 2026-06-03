package com.javeriana.eventos.event.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entidad: Tarifa de inscripción a un evento.
 *
 * Invariante: precio >= 0 (precio 0 = evento gratuito).
 * Un evento puede tener múltiples tarifas por categoría (estudiante, docente, externo).
 */
public class Tarifa {

    public enum AplicaA {
        ESTUDIANTE_JAVERIANA,
        DOCENTE_JAVERIANA,
        EXTERNO,
        PONENTE
    }

    private UUID id;
    private UUID eventoId;
    private String nombre;
    private BigDecimal precio;
    private String moneda;
    private AplicaA aplicaA;
    private LocalDate fechaInicioVigencia;
    private LocalDate fechaFinVigencia;
    private boolean activa;

    public Tarifa(UUID id, UUID eventoId, String nombre, BigDecimal precio,
                  AplicaA aplicaA, LocalDate fechaInicioVigencia, LocalDate fechaFinVigencia) {
        validarPrecio(precio);
        validarVigencia(fechaInicioVigencia, fechaFinVigencia);
        this.id = id;
        this.eventoId = eventoId;
        this.nombre = nombre;
        this.precio = precio;
        this.moneda = "COP";
        this.aplicaA = aplicaA;
        this.fechaInicioVigencia = fechaInicioVigencia;
        this.fechaFinVigencia = fechaFinVigencia;
        this.activa = true;
    }

    public Tarifa(UUID id, UUID eventoId, String nombre, BigDecimal precio,
                  String moneda, AplicaA aplicaA, LocalDate fechaInicioVigencia,
                  LocalDate fechaFinVigencia, boolean activa) {
        validarPrecio(precio);
        validarVigencia(fechaInicioVigencia, fechaFinVigencia);
        this.id = id;
        this.eventoId = eventoId;
        this.nombre = nombre;
        this.precio = precio;
        this.moneda = validarMoneda(moneda);
        this.aplicaA = aplicaA;
        this.fechaInicioVigencia = fechaInicioVigencia;
        this.fechaFinVigencia = fechaFinVigencia;
        this.activa = activa;
    }

    public boolean estaVigente(LocalDate fecha) {
        return activa
            && !fecha.isBefore(fechaInicioVigencia)
            && !fecha.isAfter(fechaFinVigencia);
    }

    public void desactivar() { this.activa = false; }

    public void actualizar(String nombre, BigDecimal precio, String moneda) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la tarifa es obligatorio");
        }
        validarPrecio(precio);
        this.nombre = nombre;
        this.precio = precio;
        this.moneda = validarMoneda(moneda);
        this.activa = true;
    }

    private void validarPrecio(BigDecimal precio) {
        if (precio == null || precio.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo");
        }
    }

    private void validarVigencia(LocalDate inicio, LocalDate fin) {
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha de fin de vigencia debe ser posterior a la de inicio");
        }
    }

    private String validarMoneda(String moneda) {
        String normalizada = moneda == null || moneda.isBlank() ? "COP" : moneda.trim().toUpperCase();
        if (!normalizada.equals("COP") && !normalizada.equals("USD")) {
            throw new IllegalArgumentException("La moneda debe ser COP o USD");
        }
        return normalizada;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public String getNombre() { return nombre; }
    public BigDecimal getPrecio() { return precio; }
    public String getMoneda() { return moneda; }
    public AplicaA getAplicaA() { return aplicaA; }
    public LocalDate getFechaInicioVigencia() { return fechaInicioVigencia; }
    public LocalDate getFechaFinVigencia() { return fechaFinVigencia; }
    public boolean isActiva() { return activa; }

    public void setActiva(boolean activa) { this.activa = activa; }
}
