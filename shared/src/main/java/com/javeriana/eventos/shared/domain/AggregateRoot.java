package com.javeriana.eventos.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Clase base para todos los Agregados del dominio.
 *
 * Provee el mecanismo de registro de eventos de dominio. Los eventos
 * se acumulan durante la ejecución del caso de uso y luego son leídos
 * por la capa de aplicación para publicarlos vía Outbox Pattern.
 *
 * Los subtipos NUNCA llaman directamente a un publisher; solo registran eventos.
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * Registra un evento de dominio ocurrido en este agregado.
     * Llamar desde los métodos de negocio (e.g., confirmar(), cancelar()).
     */
    protected void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    /**
     * Retorna los eventos pendientes de publicar y limpia la lista interna.
     * La capa de aplicación debe llamar este método después del COMMIT
     * para guardar los eventos en outbox_events.
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return Collections.unmodifiableList(events);
    }

    public boolean hasDomainEvents() {
        return !this.domainEvents.isEmpty();
    }
}
