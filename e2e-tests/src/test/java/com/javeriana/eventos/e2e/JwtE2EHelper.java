package com.javeriana.eventos.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Genera JWTs firmados con la clave privada de prueba (mismo par que inscription-service).
 *
 * La clave pública está configurada en application-local.yml de inscription-service.
 * Los tokens generados aquí son válidos para llamadas HTTP a inscription-service.
 */
public class JwtE2EHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PrivateKey CLAVE_PRIVADA = cargarClavePrivada();

    public static String tokenParticipante() {
        return generarToken(UUID.randomUUID(), List.of("PARTICIPANTE"));
    }

    public static String tokenOrganizador() {
        return generarToken(UUID.randomUUID(), List.of("ORGANIZADOR"));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static UUID extractUserId(String token) {
        try {
            String payload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8);
            return UUID.fromString(MAPPER.readTree(payload).get("sub").asText());
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo extraer sub del JWT E2E", e);
        }
    }

    public static String generarToken(UUID userId, List<String> roles) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("roles", roles)
            .claim("email", "e2e-" + userId.toString().substring(0, 8) + "@test.javeriana.edu")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(CLAVE_PRIVADA)
            .compact();
    }

    private static PrivateKey cargarClavePrivada() {
        try (InputStream is = JwtE2EHelper.class.getResourceAsStream("/jwt/test-private-key.b64")) {
            String b64 = new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
            byte[] der = Base64.getDecoder().decode(b64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar test-private-key.b64 en e2e-tests", e);
        }
    }
}
