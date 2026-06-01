package com.javeriana.eventos.inscription.infrastructure.web;

import com.javeriana.eventos.inscription.domain.exceptions.ServicioExternoNoDisponibleException;
import com.javeriana.eventos.inscription.domain.port.in.CrearInscripcionUseCase;
import com.javeriana.eventos.inscription.infrastructure.security.JwtPrincipal;
import com.javeriana.eventos.inscription.infrastructure.web.dto.CrearInscripcionRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InscripcionControllerTest {

    @Mock
    private CrearInscripcionUseCase crearInscripcion;

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private InscripcionController controller;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        controller = new InscripcionController(crearInscripcion, circuitBreakerRegistry);
    }

    @Test
    @DisplayName("Responde 503 inmediato cuando el circuit breaker de event-service esta OPEN")
    void crear_conCircuitBreakerOpen_noInvocaCasoDeUso() {
        circuitBreakerRegistry.circuitBreaker("evento-service").transitionToOpenState();

        CrearInscripcionRequest request = new CrearInscripcionRequest(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0001-000000000001"),
            UUID.randomUUID()
        );
        JwtPrincipal principal = new JwtPrincipal(
            UUID.fromString("10000000-0000-4000-8000-000000000001"),
            List.of("PARTICIPANTE")
        );

        assertThatThrownBy(() -> controller.crear(request, principal))
            .isInstanceOf(ServicioExternoNoDisponibleException.class)
            .hasMessageContaining("event-service")
            .hasMessageContaining("circuit breaker abierto");

        verifyNoInteractions(crearInscripcion);
    }
}
