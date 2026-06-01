package com.javeriana.eventos.authstub;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIT {

    @LocalServerPort
    int port;

    @Autowired
    JwtIssuer jwtIssuer;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void debeEmitirJwtRs256ParaUsuarioDemo() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/v1/auth/login"),
            Map.of("email", "laura.participante@javeriana.edu.co", "password", "demo123"),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("accessToken", "expiresAt", "user");
        assertThat((String) response.getBody().get("accessToken")).contains(".");
    }

    @Test
    void debeRechazarCredencialesInvalidas() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/v1/auth/login"),
            Map.of("email", "laura.participante@javeriana.edu.co", "password", "incorrecta"),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "credenciales_invalidas");
    }

    @Test
    void debePublicarJwks() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/v1/auth/.well-known/jwks.json"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> keys = (List<?>) response.getBody().get("keys");
        assertThat(keys).hasSize(1);
        Map<?, ?> jwk = (Map<?, ?>) keys.get(0);
        assertThat(jwk.get("alg")).isEqualTo("RS256");
    }

    @Test
    void jwtEmitidoDebeSerVerificableConClavePublicaLocal() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/v1/auth/login"),
            Map.of("email", "diego.participante@javeriana.edu.co", "password", "demo123"),
            Map.class);

        String token = (String) response.getBody().get("accessToken");

        assertThat(Jwts.parser()
            .verifyWith(jwtIssuer.publicKey())
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("roles", List.class)).contains("PARTICIPANTE");
    }

    private URI url(String path) {
        return URI.create("http://localhost:%d%s".formatted(port, path));
    }
}
