package com.javeriana.eventos.inscription.infrastructure.security;

import com.javeriana.eventos.inscription.domain.exceptions.ServicioExternoNoDisponibleException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Configuración de seguridad HTTP para inscription-service.
 *
 * Modelo: JWT stateless con RSA-256. Validación en JwtAuthFilter.
 *
 * RBAC implementado (RNF-08, ADR-010):
 *
 *   ENDPOINT                              ROL MÍNIMO
 *   POST /api/v1/inscripciones            PARTICIPANTE | ORGANIZADOR | ADMIN
 *   /actuator/health                      público (health checks k8s/ALB)
 *   /actuator/prometheus                  público (scraping Prometheus)
 *   cualquier otro                        autenticado
 *
 * Errores HTTP estandarizados:
 *   401 Unauthorized → sin token, token expirado, firma inválida
 *   403 Forbidden    → token válido pero rol insuficiente
 *
 * Session management: STATELESS — sin cookies, sin estado en servidor.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos — health checks y métricas
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/prometheus").permitAll()
                // Inscripciones — cualquier rol autenticado puede inscribirse
                .requestMatchers(HttpMethod.POST, "/api/v1/inscripciones")
                    .hasAnyRole("PARTICIPANTE", "ORGANIZADOR", "ADMIN")
                // Todo lo demás requiere autenticación
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                // 401 — no autenticado
                .authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                // 403 — autenticado pero sin el rol requerido
                .accessDeniedHandler((request, response, denied) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"error\":\"acceso_denegado\"," +
                        "\"message\":\"No tiene el rol necesario para esta operación\"}");
                }))
            .build();
    }
}
