package com.javeriana.eventos.event.infrastructure.security;

import java.util.List;
import java.util.UUID;

/**
 * Principal autenticado extraído del JWT validado (Prompt 21 — patrón Prompt 12).
 */
public record JwtPrincipal(UUID userId, List<String> roles) {
    public boolean esOrganizador() { return roles.contains("ORGANIZADOR"); }
    public boolean esAdmin()       { return roles.contains("ADMIN"); }
    public boolean esServicio()    { return roles.contains("SERVICE"); }
}
