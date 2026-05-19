package com.javeriana.eventos.payment.application;

import com.javeriana.eventos.payment.domain.model.Pago;
import com.javeriana.eventos.payment.domain.port.in.CrearPreferenciaUseCase;
import com.javeriana.eventos.payment.domain.port.out.PagoRepository;
import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoFactory;
import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Crea una preferencia de pago en la pasarela.
 *
 * ADR-18 — Circuit Breaker (Resilience4j):
 * El @CircuitBreaker envuelve la llamada a PasarelaPagoPort, que puede ser
 * MercadoPagoAdapter (HTTP real) o SimuladorPasarelaAdapter (demo).
 * Si la pasarela no responde en 5s o falla 5 veces → OPEN → fallback.
 *
 * Dos implementaciones del mismo port, mismo Circuit Breaker:
 * el patrón es agnóstico al proveedor.
 */
@Service
public class CrearPreferenciaService implements CrearPreferenciaUseCase {

    private static final Logger log = LoggerFactory.getLogger(CrearPreferenciaService.class);

    private final PagoRepository pagoRepository;
    private final PasarelaPagoFactory pasarelaFactory;

    public CrearPreferenciaService(PagoRepository pagoRepository,
                                   PasarelaPagoFactory pasarelaFactory) {
        this.pagoRepository = pagoRepository;
        this.pasarelaFactory = pasarelaFactory;
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "pasarela-pago", fallbackMethod = "fallbackCrearPreferencia")
    public Result crear(Command command) {
        // Factory Method: el adaptador concreto se resuelve según la config (ADR-18)
        PasarelaPagoPort pasarela = pasarelaFactory.crearPasarela();

        // Crear el agregado Pago en estado INICIADO
        Pago pago = new Pago(
            UUID.randomUUID(),
            command.inscripcionId(),
            command.monto(),
            command.moneda(),
            pasarela.getClass().getSimpleName()   // Registra qué adaptador se usó
        );
        pagoRepository.guardar(pago);

        // Llamar a la pasarela (MercadoPago real o Simulador)
        PasarelaPagoPort.PreferenciaPago preferencia = pasarela.crearPreferencia(
            pago.getId(),
            command.inscripcionId(),
            command.monto(),
            command.moneda()
        );

        // Registrar la preferencia en el agregado
        pago.registrarPreferencia(preferencia.preferenciaId());
        pagoRepository.guardar(pago);

        log.info("Preferencia creada para inscripción {}. Pasarela: {}",
            command.inscripcionId(), pago.getPasarela());

        return new Result(pago.getId(), preferencia.checkoutUrl(), preferencia.preferenciaId());
    }

    /**
     * Fallback cuando el Circuit Breaker está OPEN o la pasarela lanza excepción.
     * Se lanza una excepción descriptiva que el controller mapea a HTTP 503.
     */
    public Result fallbackCrearPreferencia(Command command, Exception ex) {
        log.error("Circuit Breaker activado para pasarela de pago: {}", ex.getMessage());
        throw new PasarelaNoDisponibleException(
            "El servicio de pagos no está disponible temporalmente. " +
            "Tu inscripción se reservó por 15 minutos. Intenta de nuevo en unos instantes."
        );
    }
}
