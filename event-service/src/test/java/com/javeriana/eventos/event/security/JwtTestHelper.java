package com.javeriana.eventos.event.security;

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

public class JwtTestHelper {

    private static final PrivateKey CLAVE_PRIVADA = cargarClavePrivada();

    public static String generarToken(UUID userId, List<String> roles) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("roles", roles)
            .claim("email", "test-" + userId.toString().substring(0, 8) + "@test.javeriana.edu")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(CLAVE_PRIVADA)
            .compact();
    }

    public static String generarTokenExpirado(UUID userId) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("roles", List.of("PARTICIPANTE"))
            .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
            .expiration(Date.from(Instant.now().minusSeconds(3600)))
            .signWith(CLAVE_PRIVADA)
            .compact();
    }

    private static PrivateKey cargarClavePrivada() {
        try (InputStream is = JwtTestHelper.class.getResourceAsStream(
                "/jwt/test-private-key.b64")) {
            String b64 = new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
            byte[] derBytes = Base64.getDecoder().decode(b64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(derBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar test-private-key.b64", e);
        }
    }
}
