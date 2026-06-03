package com.javeriana.eventos.inscription.application;

import com.javeriana.eventos.inscription.domain.model.Inscripcion;
import com.javeriana.eventos.inscription.domain.port.out.EventoServicePort;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class CertificadoGeneratorService {

    private static final Color AZUL_JAVERIANA = new Color(0, 60, 113);
    private static final Color ORO_JAVERIANA = new Color(255, 205, 0);
    private static final Locale LOCALE_CO = new Locale("es", "CO");

    public byte[] generar(Inscripcion inscripcion,
                          EventoServicePort.EventoInfo evento,
                          ParticipanteCertificado participante) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate(), 56, 56, 44, 44);
            PdfWriter.getInstance(document, out);
            document.open();

            agregarMarcaInstitucional(document);
            agregarTitulo(document);
            agregarCuerpo(document, inscripcion, evento, participante);
            agregarPie(document, inscripcion);

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible generar el certificado PDF", ex);
        }
    }

    private void agregarMarcaInstitucional(Document document) throws Exception {
        try (InputStream logo = getClass().getResourceAsStream("/templates/javeriana_logo.png")) {
            if (logo != null) {
                Image image = Image.getInstance(logo.readAllBytes());
                image.scaleToFit(120, 72);
                image.setAlignment(Element.ALIGN_CENTER);
                document.add(image);
                return;
            }
        }

        Paragraph marca = new Paragraph(
            "PONTIFICIA UNIVERSIDAD JAVERIANA",
            new Font(Font.HELVETICA, 16, Font.BOLD, AZUL_JAVERIANA));
        marca.setAlignment(Element.ALIGN_CENTER);
        document.add(marca);
    }

    private void agregarTitulo(Document document) throws DocumentException {
        Paragraph titulo = new Paragraph(
            "CERTIFICADO DE PARTICIPACION",
            new Font(Font.HELVETICA, 24, Font.BOLD, AZUL_JAVERIANA));
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingBefore(18);
        titulo.setSpacingAfter(24);
        document.add(titulo);
    }

    private void agregarCuerpo(Document document,
                               Inscripcion inscripcion,
                               EventoServicePort.EventoInfo evento,
                               ParticipanteCertificado participante) throws DocumentException {
        Font cuerpo = new Font(Font.HELVETICA, 14, Font.NORMAL, Color.DARK_GRAY);
        Paragraph intro = new Paragraph("La Pontificia Universidad Javeriana hace constar que:", cuerpo);
        intro.setAlignment(Element.ALIGN_CENTER);
        intro.setSpacingAfter(18);
        document.add(intro);

        Paragraph nombre = new Paragraph(
            valor(participante.nombre(), "Participante").toUpperCase(LOCALE_CO),
            new Font(Font.HELVETICA, 22, Font.BOLD, Color.BLACK));
        nombre.setAlignment(Element.ALIGN_CENTER);
        nombre.setSpacingAfter(18);
        document.add(nombre);

        String fecha = evento.fechaInicio() != null
            ? evento.fechaInicio().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", LOCALE_CO))
            : "fecha registrada";
        String modalidad = valor(evento.modalidad(), "modalidad registrada");

        Paragraph detalle = new Paragraph(
            "Participo en el evento academico:\n\n\"" + valor(evento.titulo(), "Evento academico") + "\"\n\n"
                + "Realizado el " + fecha + " en modalidad " + modalidad + ".",
            cuerpo);
        detalle.setAlignment(Element.ALIGN_CENTER);
        detalle.setLeading(20);
        detalle.setSpacingAfter(22);
        document.add(detalle);

        Paragraph email = new Paragraph(
            "Participante: " + valor(participante.email(), inscripcion.getUsuarioId().toString()),
            new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY));
        email.setAlignment(Element.ALIGN_CENTER);
        document.add(email);
    }

    private void agregarPie(Document document, Inscripcion inscripcion) throws DocumentException {
        Paragraph linea = new Paragraph(" ");
        linea.setSpacingBefore(18);
        linea.setSpacingAfter(6);
        document.add(linea);

        Paragraph codigo = new Paragraph(
            "Codigo de verificacion interno: " + inscripcion.getId(),
            new Font(Font.COURIER, 10, Font.NORMAL, AZUL_JAVERIANA));
        codigo.setAlignment(Element.ALIGN_CENTER);
        document.add(codigo);

        Paragraph nota = new Paragraph(
            "Emitido por la Plataforma de Gestion de Eventos Academicos PUJ",
            new Font(Font.HELVETICA, 9, Font.NORMAL, ORO_JAVERIANA));
        nota.setAlignment(Element.ALIGN_CENTER);
        nota.setSpacingBefore(8);
        document.add(nota);
    }

    private String valor(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record ParticipanteCertificado(String nombre, String email) {}
}
