package com.javeriana.eventos.event.infrastructure.persistence.entity;

import com.javeriana.eventos.event.domain.model.Tarifa;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entidad JPA para la tabla TARIFA (V1 migration).
 */
@Entity
@Table(name = "tarifa")
public class TarifaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "evento_id", nullable = false, columnDefinition = "uuid")
    private UUID eventoId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Enumerated(EnumType.STRING)
    @Column(name = "aplica_a", nullable = false, length = 30)
    private Tarifa.AplicaA aplicaA;

    @Column(name = "fecha_inicio_vigencia", nullable = false)
    private LocalDate fechaInicioVigencia;

    @Column(name = "fecha_fin_vigencia", nullable = false)
    private LocalDate fechaFinVigencia;

    @Column(nullable = false)
    private boolean activa = true;

    public UUID getId()                         { return id; }
    public void setId(UUID id)                  { this.id = id; }
    public UUID getEventoId()                   { return eventoId; }
    public void setEventoId(UUID eventoId)      { this.eventoId = eventoId; }
    public String getNombre()                   { return nombre; }
    public void setNombre(String nombre)        { this.nombre = nombre; }
    public BigDecimal getPrecio()               { return precio; }
    public void setPrecio(BigDecimal precio)    { this.precio = precio; }
    public String getMoneda()                   { return moneda; }
    public void setMoneda(String moneda)        { this.moneda = moneda; }
    public Tarifa.AplicaA getAplicaA()          { return aplicaA; }
    public void setAplicaA(Tarifa.AplicaA a)    { this.aplicaA = a; }
    public LocalDate getFechaInicioVigencia()   { return fechaInicioVigencia; }
    public void setFechaInicioVigencia(LocalDate f) { this.fechaInicioVigencia = f; }
    public LocalDate getFechaFinVigencia()      { return fechaFinVigencia; }
    public void setFechaFinVigencia(LocalDate f) { this.fechaFinVigencia = f; }
    public boolean isActiva()                   { return activa; }
    public void setActiva(boolean activa)       { this.activa = activa; }
}
