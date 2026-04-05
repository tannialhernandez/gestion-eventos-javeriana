package com.javeriana.eventos.event.infrastructure.persistence.entity;

import com.javeriana.eventos.event.domain.model.EstadoEvento;
import com.javeriana.eventos.event.domain.model.ModalidadEvento;
import com.javeriana.eventos.event.domain.model.TipoEvento;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad JPA para EVENTO.
 *
 * Vive exclusivamente en infrastructure/persistence. El dominio (Evento.java)
 * no tiene anotaciones JPA. El mapper convierte entre ambos mundos.
 */
@Entity
@Table(name = "evento")
public class EventoEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 300)
    private String titulo;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEvento tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModalidadEvento modalidad;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "fecha_limite_inscripcion", nullable = false)
    private LocalDateTime fechaLimiteInscripcion;

    @Column(name = "cupo_maximo", nullable = false)
    private int cupoMaximo;

    @Column(name = "cupo_disponible", nullable = false)
    private int cupoDisponible;

    @Column(name = "url_imagen", length = 500)
    private String urlImagen;

    @Column(name = "url_evento_virtual", length = 500)
    private String urlEventoVirtual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoEvento estado;

    @Column(name = "organizador_id", nullable = false, columnDefinition = "uuid")
    private UUID organizadorId;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Version
    private int version;

    // Getters y setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public TipoEvento getTipo() { return tipo; }
    public void setTipo(TipoEvento tipo) { this.tipo = tipo; }
    public ModalidadEvento getModalidad() { return modalidad; }
    public void setModalidad(ModalidadEvento modalidad) { this.modalidad = modalidad; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public LocalDateTime getFechaLimiteInscripcion() { return fechaLimiteInscripcion; }
    public void setFechaLimiteInscripcion(LocalDateTime fechaLimiteInscripcion) { this.fechaLimiteInscripcion = fechaLimiteInscripcion; }
    public int getCupoMaximo() { return cupoMaximo; }
    public void setCupoMaximo(int cupoMaximo) { this.cupoMaximo = cupoMaximo; }
    public int getCupoDisponible() { return cupoDisponible; }
    public void setCupoDisponible(int cupoDisponible) { this.cupoDisponible = cupoDisponible; }
    public String getUrlImagen() { return urlImagen; }
    public void setUrlImagen(String urlImagen) { this.urlImagen = urlImagen; }
    public String getUrlEventoVirtual() { return urlEventoVirtual; }
    public void setUrlEventoVirtual(String urlEventoVirtual) { this.urlEventoVirtual = urlEventoVirtual; }
    public EstadoEvento getEstado() { return estado; }
    public void setEstado(EstadoEvento estado) { this.estado = estado; }
    public UUID getOrganizadorId() { return organizadorId; }
    public void setOrganizadorId(UUID organizadorId) { this.organizadorId = organizadorId; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
