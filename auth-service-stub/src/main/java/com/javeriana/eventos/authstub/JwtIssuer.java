package com.javeriana.eventos.authstub;

import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtIssuer {

    private final UsuariosDemoConfig config;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtIssuer(UsuariosDemoConfig config) {
        this.config = config;
        KeyPairMaterial keyMaterial = cargarClaves(config.privateKey());
        this.privateKey = keyMaterial.privateKey();
        this.publicKey = keyMaterial.publicKey();
    }

    public TokenEmitido emitir(UsuariosDemoConfig.UsuarioDemo usuario) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(config.tokenTtl());

        String token = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .issuer(config.issuer())
            .subject(usuario.id())
            .claim("email", usuario.email())
            .claim("name", usuario.nombre())
            .claim("roles", usuario.roles())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .header().keyId(config.keyId()).and()
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();

        return new TokenEmitido(token, expiresAt);
    }

    public JwksResponse jwks() {
        return new JwksResponse(List.of(new Jwk(
            "RSA",
            "sig",
            config.keyId(),
            "RS256",
            base64Url(publicKey.getModulus()),
            base64Url(publicKey.getPublicExponent())
        )));
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    private static KeyPairMaterial cargarClaves(String privateKeyBase64) {
        try {
            byte[] der = Base64.getDecoder().decode(privateKeyBase64.replaceAll("\\s", ""));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException("La clave privada RSA no incluye material CRT para derivar la clave publica");
            }
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
            return new KeyPairMaterial(privateKey, publicKey);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar la clave privada RSA del auth-service-stub", e);
        }
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record TokenEmitido(String accessToken, Instant expiresAt) {
    }

    public record JwksResponse(List<Jwk> keys) {
    }

    public record Jwk(String kty, String use, String kid, String alg, String n, String e) {
    }

    private record KeyPairMaterial(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
    }
}
