package com.javeriana.eventos.event.domain.model;

import com.javeriana.eventos.shared.domain.AggregateRoot;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import com.javeriana.eventos.event.domain.events.EventoPublicadoEvent;
import com.javeriana.eventos.event.domain.events.EventoCanceladoEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agregado raíz: EVENTO
 *
 * Contiene las invariantes de negocio del ciclo de vida del evento.
 * Las reglas de negocio viven aquí, no en los servicios de aplicación.
 *
 * Máquina de estados (ver docs/modelo-datos-conceptual.md §4.1):
 *   BORRADOR → PENDIENTE_PUBLICACION → PUBLICADO → FINALIZADO
 *                                    ↘ CANCELADO (desde cualquier estado no terminal)
 */
public class Evento extends AggregateRoot {

    private UUID id;
    private String titulo;
    private String descripcion;
    private TipoEvento tipo;
    private ModalidadEvento modalidad;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalDateTime fechaLimiteInscripcion;
    private int cupoMaximo;
    private int cupoDisponible;
    private String urlImagen;
    private String urlEventoVirtual;
    private EstadoEvento estado;
    private UUID organizadorId;
    private LocalDateTime fechaCreacion;
    private int version;

    // Constructor para crear un nuevo evento
    public Evento(UUID id, String titulo, String descripcion, TipoEvento tipo,
                  ModalidadEvento modalidad, LocalDate fechaInicio, LocalDate fechaFin,
                  LocalDateTime fechaLimiteInscripcion, int cupoMaximo, UUID organizadorId) {
        validarFechas(fechaInicio, fechaFin, fechaLimiteInscripcion);
        validarCupo(cupoMaximo);

        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.tipo = tipo;
        this.modalidad = modalidad;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.fechaLimiteInscripcion = fechaLimiteInscripcion;
        this.cupoMaximo = cupoMaximo;
        this.cupoDisponible = cupoMaximo;
        this.estado = EstadoEvento.BORRADOR;
        this.organizadorId = organizadorId;
        this.fechaCreacion = LocalDateTime.now();
        this.version = 0;
    }

    // Constructor para reconstruir desde persistencia (todos los campos)
    public Evento(UUID id, String titulo, String descripcion, TipoEvento tipo,
                  ModalidadEvento modalidad, LocalDate fechaInicio, LocalDate fechaFin,
                  LocalDateTime fechaLimiteInscripcion, int cupoMaximo, int cupoDisponible,
                  String urlImagen, String urlEventoVirtual, EstadoEvento estado,
                  UUID organizadorId, LocalDateTime fechaCreacion, int version) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.tipo = tipo;
        this.modalidad = modalidad;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.fechaLimiteInscripcion = fechaLimiteInscripcion;
        this.cupoMaximo = cupoMaximo;
        this.cupoDisponible = cupoDisponible;
        this.urlImagen = urlImagen;
        this.urlEventoVirtual = urlEventoVirtual;
        this.estado = estado;
        this.organizadorId = organizadorId;
        this.fechaCreacion = fechaCreacion;
        this.version = version;
    }

    // ─── Comportamiento de dominio ─────────────────────────────────────────────

    /**
     * RN: Envía el evento a revisión. Solo BORRADOR puede transicionar.
     */
    public void enviarARevision() {
        if (this.estado != EstadoEvento.BORRADOR) {
            throw new BusinessRuleViolationException(
                "RN-EVENTO-01",
                "Solo un evento en BORRADOR puede enviarse a revisión. Estado actual: " + this.estado
            );
        }
        this.estado = EstadoEvento.PENDIENTE_PUBLICACION;
    }

    /**
     * RN: Publica el evento. Solo PENDIENTE_PUBLICACION o BORRADOR (admin directo).
     */
    public void publicar() {
        if (this.estado != EstadoEvento.PENDIENTE_PUBLICACION && this.estado != EstadoEvento.BORRADOR) {
            throw new BusinessRuleViolationException(
                "RN-EVENTO-02",
                "Solo un evento en PENDIENTE_PUBLICACION puede publicarse. Estado actual: " + this.estado
            );
        }
        if (this.cupoMaximo <= 0) {
            throw new BusinessRuleViolationException(
                "RN-EVENTO-03",
                "Un evento debe tener cupo máximo definido antes de publicarse"
            );
        }
        this.estado = EstadoEvento.PUBLICADO;
        registerEvent(new EventoPublicadoEvent(this.id, this.titulo));
    }

    /**
     * RN: Cancela el evento. No se puede cancelar si ya está finalizado o cancelado.
     */
    public void cancelar(String motivo) {
        if (this.estado.esTerminal()) {
            throw new BusinessRuleViolationException(
                "RN-EVENTO-04",
                "No se puede cancelar un evento en estado terminal: " + this.estado
            );
        }
        this.estado = EstadoEvento.CANCELADO;
        registerEvent(new EventoCanceladoEvent(this.id, this.titulo, motivo));
    }

    /**
     * RN: Finaliza el evento (llamado automáticamente cuando fecha_fin es alcanzada).
     */
    public void finalizar() {
        if (this.estado != EstadoEvento.PUBLICADO) {
            throw new BusinessRuleViolationException(
                "RN-EVENTO-05",
                "Solo un evento PUBLICADO puede finalizarse. Estado actual: " + this.estado
            );
        }
        this.estado = EstadoEvento.FINALIZADO;
    }

    /**
     * RN-08: cupo_disponible no puede ser negativo.
     * Usado por inscription-service al reservar un cupo.
     * El bloqueo pesimista ocurre en el repositorio; esta validación es la guardia del dominio.
     */
    public void reservarCupo() {
        if (this.cupoDisponible <= 0) {
            throw new BusinessRuleViolationException(
                "RN-08",
                "No hay cupos disponibles para el evento: " + this.titulo
            );
        }
        if (!this.estado.aceptaInscripciones()) {
            throw new BusinessRuleViolationException(
                "RN-EVENTO-06",
                "El evento no acepta inscripciones en su estado actual: " + this.estado
            );
        }
        this.cupoDisponible--;
    }

    /**
     * Libera un cupo (inscripción expirada o cancelada).
     */
    public void liberarCupo() {
        if (this.cupoDisponible >= this.cupoMaximo) {
            throw new BusinessRuleViolationException(
                "RN-EVENTO-07",
                "No se puede liberar cupo: cupoDisponible ya es igual al cupoMaximo"
            );
        }
        this.cupoDisponible++;
    }

    // ─── Validaciones privadas ──────────────────────────────────────────────────

    private void validarFechas(LocalDate inicio, LocalDate fin, LocalDateTime limiteInscripcion) {
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha de fin debe ser posterior a la fecha de inicio");
        }
    }

    private void validarCupo(int cupo) {
        if (cupo <= 0) {
            throw new IllegalArgumentException("El cupo máximo debe ser mayor a 0");
        }
    }

    // ─── Getters ───────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public TipoEvento getTipo() { return tipo; }
    public ModalidadEvento getModalidad() { return modalidad; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public LocalDateTime getFechaLimiteInscripcion() { return fechaLimiteInscripcion; }
    public int getCupoMaximo() { return cupoMaximo; }
    public int getCupoDisponible() { return cupoDisponible; }
    public String getUrlImagen() { return urlImagen; }
    public String getUrlEventoVirtual() { return urlEventoVirtual; }
    public EstadoEvento getEstado() { return estado; }
    public UUID getOrganizadorId() { return organizadorId; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public int getVersion() { return version; }

    public void setUrlImagen(String urlImagen) { this.urlImagen = urlImagen; }
    public void setUrlEventoVirtual(String urlEventoVirtual) { this.urlEventoVirtual = urlEventoVirtual; }
}
