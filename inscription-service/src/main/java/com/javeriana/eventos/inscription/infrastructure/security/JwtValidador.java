package com.javeriana.eventos.inscription.infrastructure.security;

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
 * Valida JWTs firmados con RSA-256 usando la clave pública del auth-service.
 *
 * Decisión de diseño (Prompt 12):
 *   Validación stateless: inscription-service NO necesita comunicarse con
 *   auth-service en cada request. Solo necesita la clave pública para verificar
 *   la firma. Si el token es válido, los claims (sub=userId, roles) son confiables.
 *
 * Configuración:
 *   jwt.public-key: clave pública RSA-2048 en formato Base64 (DER/X.509).
 *   Local/Test : clave incluida en application-local.yml / application-test.yml
 *   Producción : variable de entorno ${JWT_PUBLIC_KEY} inyectada por AWS Secrets Manager.
 */
@Component
public class JwtValidador {

    private static final Logger log = LoggerFactory.getLogger(JwtValidador.class);

    private final RSAPublicKey publicKey;

    public JwtValidador(@Value("${jwt.public-key}") String publicKeyBase64) {
        this.publicKey = cargarClavePublica(publicKeyBase64);
    }

    /**
     * Valida el token y retorna sus claims si la firma es válida y no está expirado.
     *
     * @param token JWT sin el prefijo "Bearer "
     * @throws JwtException (RuntimeException) si la firma es inválida, el token expiró,
     *                       o el formato es incorrecto.
     */
    public Claims validar(String token) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Versión booleana para casos donde se necesita verificar sin propagar excepción.
     */
    public boolean esValido(String token) {
        try {
            validar(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expirado: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.warn("JWT inválido: {}", e.getMessage());
            return false;
        }
    }

    private RSAPublicKey cargarClavePublica(String base64) {
        try {
            byte[] derBytes = Base64.getDecoder().decode(base64.strip());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(derBytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException(
                "No se pudo cargar la clave pública JWT (jwt.public-key). " +
                "Verificar configuración o variable de entorno JWT_PUBLIC_KEY.", e);
        }
    }
}
