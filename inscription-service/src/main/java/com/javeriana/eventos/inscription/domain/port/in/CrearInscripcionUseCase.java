package com.javeriana.eventos.inscription.domain.port.in;

import com.javeriana.eventos.inscription.domain.model.Inscripcion;

import java.util.UUID;

public interface CrearInscripcionUseCase {

    /**
     * Resultado: inscripción creada + URL de checkout de la pasarela de pago.
     */
    Result crear(Command command);

    record Command(
        UUID usuarioId,
        UUID eventoId,
        UUID tarifaId,
        UUID idempotencyKey   // Enviado por el cliente para prevenir duplicados
    ) {}

    record Result(
        Inscripcion inscripcion,
        String checkoutUrl,    // URL para redirigir al usuario a pagar
        long expiraEnSegundos  // Cuántos segundos tiene para pagar (900 = 15 min)
    ) {}
}
