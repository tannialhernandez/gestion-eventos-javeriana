package com.javeriana.eventos.event.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Entidad de dominio: Espacio Físico (sala, auditorio, laboratorio).
 *
 * ALCANCE: Este sistema gestiona únicamente la disponibilidad temporal del espacio
 * (si ya tiene una sesión asignada en ese horario). La administración interna del
 * espacio (mantenimiento, limpieza, recursos) está fuera del alcance declarado en SRS §1.3.
 */
public class EspacioFisico {

    private UUID id;
    private String nombre;
    private String edificio;
    private int capacidadMaxima;
    private List<String> equipamiento;
    private boolean activo;

    public EspacioFisico(UUID id, String nombre, String edificio,
                         int capacidadMaxima, List<String> equipamiento) {
        if (capacidadMaxima <= 0) {
            throw new IllegalArgumentException("La capacidad máxima debe ser mayor a 0");
        }
        this.id = id;
        this.nombre = nombre;
        this.edificio = edificio;
        this.capacidadMaxima = capacidadMaxima;
        this.equipamiento = equipamiento;
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    // Getters
    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getEdificio() { return edificio; }
    public int getCapacidadMaxima() { return capacidadMaxima; }
    public List<String> getEquipamiento() { return equipamiento; }
    public boolean isActivo() { return activo; }

    // Setter para reconstrucción desde persistencia
    public void setActivo(boolean activo) { this.activo = activo; }
}
