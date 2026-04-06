package com.javeriana.eventos.inscription.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Entidad local que replica solo los campos de cupo del evento.
 *
 * Inscription-service mantiene su propia tabla de cupos (sincronizada con event-service)
 * para poder hacer el SELECT FOR UPDATE de forma local, sin cruzar red.
 * Esta es la copia local del cupo — event-service es la fuente de verdad.
 *
 * El cupo local se actualiza:
 *  - Al crear inscripción: decremento (SELECT FOR UPDATE aquí)
 *  - Al expirar inscripción: incremento (+ llamada Feign a event-service)
 */
@Entity
@Table(name = "evento_cupo")
public class EventoCupoEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID eventoId;

    @Column(name = "cupo_disponible", nullable = false)
    private int cupoDisponible;

    @Column(name = "cupo_maximo", nullable = false)
    private int cupoMaximo;

    @Version
    private int version;

    public UUID getEventoId() { return eventoId; }
    public void setEventoId(UUID eventoId) { this.eventoId = eventoId; }
    public int getCupoDisponible() { return cupoDisponible; }
    public void setCupoDisponible(int cupoDisponible) { this.cupoDisponible = cupoDisponible; }
    public int getCupoMaximo() { return cupoMaximo; }
    public void setCupoMaximo(int cupoMaximo) { this.cupoMaximo = cupoMaximo; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
