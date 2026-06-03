package com.javeriana.eventos.payment.infrastructure.web;

import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase.ResultadoWebhook;
import com.javeriana.eventos.payment.domain.port.in.ProcesarWebhookUseCase.WebhookPayload;
import com.javeriana.eventos.payment.domain.model.EstadoPago;
import com.javeriana.eventos.payment.domain.model.Pago;
import com.javeriana.eventos.payment.domain.port.out.PagoRepository;
import com.javeriana.eventos.payment.domain.port.out.PasarelaPagoFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pagos/simulador")
public class SimuladorPagoController {

    private final ProcesarWebhookUseCase procesarWebhook;
    private final PagoRepository pagoRepository;
    private final PasarelaPagoFactory pasarelaPagoFactory;

    public SimuladorPagoController(ProcesarWebhookUseCase procesarWebhook,
                                   PagoRepository pagoRepository,
                                   PasarelaPagoFactory pasarelaPagoFactory) {
        this.procesarWebhook = procesarWebhook;
        this.pagoRepository = pagoRepository;
        this.pasarelaPagoFactory = pasarelaPagoFactory;
    }

    @PostMapping("/{inscripcionId}/aprobar")
    public ResponseEntity<Map<String, String>> aprobar(@PathVariable UUID inscripcionId) {
        ResultadoWebhook resultado = procesar("approved", inscripcionId);
        return ResponseEntity.ok(Map.of("resultado", resultado.name()));
    }

    @PostMapping("/{inscripcionId}/rechazar")
    public ResponseEntity<Map<String, String>> rechazar(@PathVariable UUID inscripcionId) {
        ResultadoWebhook resultado = procesar("rejected", inscripcionId);
        return ResponseEntity.ok(Map.of("resultado", resultado.name()));
    }

    @PostMapping("/{inscripcionId}/reembolsar")
    public ResponseEntity<Map<String, String>> reembolsar(@PathVariable UUID inscripcionId) {
        Pago pago = pagoRepository.buscarPorInscripcionId(inscripcionId).orElse(null);
        if (pago == null) {
            return ResponseEntity.ok(Map.of("resultado", "SIN_PAGO"));
        }
        if (pago.getEstado() == EstadoPago.REEMBOLSADO) {
            return ResponseEntity.ok(Map.of("resultado", "REEMBOLSADO"));
        }
        if (pago.getEstado() != EstadoPago.CONFIRMADO) {
            return ResponseEntity.ok(Map.of("resultado", "SIN_PAGO_CONFIRMADO"));
        }

        pasarelaPagoFactory.crearPasarela().reembolsar(pago.getReferenciaExterna(), pago.getMonto());
        pago.reembolsar();
        pagoRepository.guardar(pago);

        return ResponseEntity.ok(Map.of("resultado", "REEMBOLSADO"));
    }

    private ResultadoWebhook procesar(String estado, UUID inscripcionId) {
        String referencia = "SIM-" + UUID.randomUUID();
        String metadata = """
            {"origen":"simulador-spa","estado":"%s","inscripcion_id":"%s","referencia_externa":"%s"}
            """.formatted(estado, inscripcionId, referencia).trim();
        return procesarWebhook.procesar(new WebhookPayload(
            referencia,
            inscripcionId.toString(),
            estado,
            metadata
        ));
    }
}
