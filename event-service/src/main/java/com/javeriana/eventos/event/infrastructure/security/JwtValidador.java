package com.javeriana.eventos.event.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Valida JWTs firmados con RSA-256 — réplica del patrón de inscription-service (Prompt 12).
 */
@Component
public class JwtValidador {

    private static final Logger log = LoggerFactory.getLogger(JwtValidador.class);
    private final RSAPublicKey publicKey;

    public JwtValidador(@Value("${jwt.public-key}") String publicKeyBase64) {
        this.publicKey = cargarClavePublica(publicKeyBase64);
    }

    public Claims validar(String token) {
        return Jwts.parser().verifyWith(publicKey).build()
            .parseSignedClaims(token).getPayload();
    }

    public boolean esValido(String token) {
        try { validar(token); return true; }
        catch (ExpiredJwtException e) { log.debug("JWT expirado"); return false; }
        catch (JwtException e)        { log.warn("JWT inválido: {}", e.getMessage()); return false; }
    }

    private RSAPublicKey cargarClavePublica(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64.strip());
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar jwt.public-key en event-service", e);
        }
    }
}
