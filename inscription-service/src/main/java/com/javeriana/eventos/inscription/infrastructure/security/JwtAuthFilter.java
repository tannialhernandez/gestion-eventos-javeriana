package com.javeriana.eventos.inscription.infrastructure.security;

import com.javeriana.eventos.inscription.infrastructure.observability.MdcKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filtro de autenticación JWT ejecutado una vez por request (OncePerRequestFilter).
 *
 * Flujo:
 *  1. Lee el header "Authorization: Bearer <token>"
 *  2. Delega la validación de firma y expiración a JwtValidador
 *  3. Extrae sub (userId) y roles del payload validado
 *  4. Crea un Authentication y lo pone en el SecurityContextHolder
 *  5. Añade userId al MDC para trazabilidad estructurada (RNF-16, ADR-010)
 *
 * Si no hay token → continúa sin autenticar (el SecurityFilterChain
 *   rechazará el request si el endpoint requiere autenticación).
 * Si el token es inválido → retorna 401 inmediatamente.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidador jwtValidador;

    public JwtAuthFilter(JwtValidador jwtValidador) {
        this.jwtValidador = jwtValidador;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtValidador.validar(token);
            UUID userId = UUID.fromString(claims.getSubject());

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            if (roles == null) roles = List.of();

            List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

            JwtPrincipal principal = new JwtPrincipal(userId, roles);

            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);

            // ADR-010 Ley 1581: userId verificado en MDC para auditoría confiable
            MDC.put(MdcKeys.USER_ID, userId.toString());

        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("[jwt] Token inválido: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                "{\"error\":\"token_invalido\",\"message\":\"JWT inválido o expirado\"}");
            return;
        } finally {
            // MDC se limpia al terminar el request (gestionado por el finally)
            // La limpieza real ocurre en el FilterChain o aquí si hay 401
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.INSCRIPCION_ID);
            MDC.remove(MdcKeys.EVENTO_ID);
        }
    }
}
