package com.javeriana.eventos.event.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// RBAC para event-service (Prompt 21):
// GET  /api/v1/eventos*              - publico
// GET  /api/v1/tarifas/**            - publico
// POST /api/v1/eventos               - ORGANIZADOR | ADMIN
// POST /api/v1/eventos/{id}/publicar - ORGANIZADOR | ADMIN
// POST /api/v1/eventos/{id}/cupos/** - SERVICE | ORGANIZADOR | ADMIN
// otros                              - autenticado
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
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Consulta pública del catálogo (RNF-01 — sin auth para lecturas)
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/eventos", "/api/v1/eventos/**",
                    "/api/v1/tarifas/**").permitAll()
                // Actuator
                .requestMatchers(
                    "/actuator/health", "/actuator/health/**",
                    "/actuator/prometheus").permitAll()
                // Escritura: solo organizadores/admin
                .requestMatchers(HttpMethod.POST, "/api/v1/eventos")
                    .hasAnyRole("ORGANIZADOR", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/eventos/*/publicar")
                    .hasAnyRole("ORGANIZADOR", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/eventos/*/cancelar")
                    .hasAnyRole("ORGANIZADOR", "ADMIN")
                // Service-to-service: cupos (inscription-service usa rol SERVICE)
                .requestMatchers(HttpMethod.POST, "/api/v1/eventos/*/cupos/**")
                    .hasAnyRole("SERVICE", "ORGANIZADOR", "ADMIN")
                // Cualquier otra escritura requiere autenticación
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((req, res, denied) -> {
                    res.setStatus(HttpStatus.FORBIDDEN.value());
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                        "{\"error\":\"acceso_denegado\"," +
                        "\"message\":\"No tiene el rol necesario para esta operación\"}");
                }))
            .build();
    }
}
