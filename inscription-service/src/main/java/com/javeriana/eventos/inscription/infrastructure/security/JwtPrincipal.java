package com.javeriana.eventos.inscription.infrastructure.security;

import java.util.List;
import java.util.UUID;

/**
 * Principal autenticado extraído del JWT validado.
 *
 * Disponible como {@code @AuthenticationPrincipal JwtPrincipal} en controllers.
 * Contiene la identidad verificada del usuario (no un header HTTP falsificable).
 *
 * Campos del JWT → principal:
 *   - claim "sub"   → userId  (UUID del usuario en auth-service)
 *   - claim "roles" → roles   (["PARTICIPANTE"] | ["ORGANIZADOR"] | ["ADMIN"])
 *
 * ADR-010: el userId proviene de la firma RSA del auth-service, garantizando
 * que la identidad del usuario cumple Ley 1581 Art. 17 (responsabilidad).
 */
public record JwtPrincipal(
    UUID userId,
    List<String> roles,
    String email,
    String name
) {
    public JwtPrincipal(UUID userId, List<String> roles) {
        this(userId, roles, null, null);
    }

    public boolean esParticipante() { return roles.contains("PARTICIPANTE"); }
    public boolean esOrganizador()  { return roles.contains("ORGANIZADOR"); }
    public boolean esAdmin()        { return roles.contains("ADMIN"); }
}
