package com.javeriana.eventos.inscription.application;

import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CertificadoGeneratorServiceTest {

    private final CertificadoGeneratorService service = new CertificadoGeneratorService();

    @Test
    @DisplayName("Genera un PDF válido para inscripción confirmada con asistencia")
    void generar_pdfValido() {
        Inscripcion inscripcion = new Inscripcion(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            EstadoInscripcion.CONFIRMADA,
            Instant.now(),
            null,
            "QR-test",
            UUID.randomUUID(),
            0
        );
        EventoServicePort.EventoInfo evento = new EventoServicePort.EventoInfo(
            inscripcion.getEventoId(),
            "Seminario de Inteligencia Artificial Aplicada",
            "PUBLICADO",
            1,
            true,
            UUID.randomUUID(),
            "VIRTUAL",
            LocalDate.of(2033, 11, 10)
        );

        byte[] pdf = service.generar(
            inscripcion,
            evento,
            new CertificadoGeneratorService.ParticipanteCertificado(
                "Laura Participante",
                "laura.participante@javeriana.edu.co"
            )
        );

        assertThat(pdf).hasSizeGreaterThan(500);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
