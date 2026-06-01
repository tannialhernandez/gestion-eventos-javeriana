package com.javeriana.eventos.inscription.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios de JwtValidador.
 *
 * Usa JwtTestHelper para generar tokens firmados con la clave privada
 * de prueba. Valida con la clave pública correspondiente.
 *
 * Tests NO requieren Spring context — JwtValidador es POJO puro con una
 * clave pública como única dependencia.
 */
@DisplayName("JwtValidador — Validación RSA-256")
class JwtValidadorTest {

    // Clave pública del par de prueba (la misma de application-local.yml)
    private static final String PUBLIC_KEY_B64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4C3rkbm3lVOmdTFJH/fGTNz1Fk+Sc" +
        "JIu+SkFXJdu8D9C10X9Bmpj97owkfgX2zGbZ+jDjbvwCIDkIKxW1KzgdeGxEWSnYUBCH5fakq" +
        "W1f6eA1B08yoWT1WzAB46CI6Dlu9Fd965/zN1tWzzFrpvmrmqZDpm5fVsCxsDSrRrKhO36wMJV" +
        "pXMFxRNxmYIbQmw/CsKCA4oJdTaC0wKF86s5p3sneqs4tH/eTNhjyrbpufYkS6aDWOxSxtqhX5" +
        "C1CRqF+65QoLyvRlTocLs69O+XRQoDYxa5teZM77stmLMqAjZCtDVpSnNaYknsaqQnS9AfPiYA" +
        "KJ3dd9ql/jYF5UtF9QIDAQAB";

    private JwtValidador validador;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        validador = new JwtValidador(PUBLIC_KEY_B64);
        usuarioId = UUID.randomUUID();
    }

    // ─── Token válido ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token válido")
    class TokenValido {

        @Test
        @DisplayName("Extrae correctamente el subject (userId) del token")
        void debeExtraerSubjectComoUserId() {
            String token = JwtTestHelper.generarToken(usuarioId, List.of("PARTICIPANTE"));

            Claims claims = validador.validar(token);

            assertThat(claims.getSubject()).isEqualTo(usuarioId.toString());
        }

        @Test
        @DisplayName("Extrae correctamente los roles del token")
        void debeExtraerRoles() {
            String token = JwtTestHelper.generarToken(usuarioId, List.of("ORGANIZADOR", "ADMIN"));

            Claims claims = validador.validar(token);

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            assertThat(roles).containsExactlyInAnyOrder("ORGANIZADOR", "ADMIN");
        }

        @Test
        @DisplayName("Token válido → esValido() retorna true")
        void esValido_tokenCorrecto_retornaTrue() {
            String token = JwtTestHelper.generarToken(usuarioId, List.of("PARTICIPANTE"));

            assertThat(validador.esValido(token)).isTrue();
        }
    }

    // ─── Token expirado ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Token expirado")
    class TokenExpirado {

        @Test
        @DisplayName("Token expirado lanza ExpiredJwtException")
        void tokenExpirado_lanzaExpiredJwtException() {
            String token = JwtTestHelper.generarTokenExpirado(usuarioId);

            assertThatThrownBy(() -> validador.validar(token))
                .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("Token expirado → esValido() retorna false")
        void tokenExpirado_esValido_retornaFalse() {
            String token = JwtTestHelper.generarTokenExpirado(usuarioId);

            assertThat(validador.esValido(token)).isFalse();
        }
    }

    // ─── Token malformado / firma inválida ────────────────────────────────

    @Nested
    @DisplayName("Token inválido")
    class TokenInvalido {

        @Test
        @DisplayName("Token con firma diferente lanza JwtException")
        void firmaInvalida_lanzaJwtException() {
            String token = JwtTestHelper.generarToken(usuarioId, List.of("PARTICIPANTE"));
            String tokenManipulado = token.substring(0, token.length() - 10) + "XXXXXXXXXX";

            assertThatThrownBy(() -> validador.validar(tokenManipulado))
                .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("String arbitrario lanza JwtException")
        void stringArbitrario_lanzaJwtException() {
            assertThatThrownBy(() -> validador.validar("no.es.un.jwt"))
                .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Token inválido → esValido() retorna false")
        void tokenInvalido_esValido_retornaFalse() {
            assertThat(validador.esValido("esto.no.es.jwt")).isFalse();
        }
    }
}
