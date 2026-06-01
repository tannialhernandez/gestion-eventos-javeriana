package com.javeriana.eventos.authstub;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UsuariosDemoConfig usuariosDemoConfig;
    private final JwtIssuer jwtIssuer;

    public AuthController(UsuariosDemoConfig usuariosDemoConfig, JwtIssuer jwtIssuer) {
        this.usuariosDemoConfig = usuariosDemoConfig;
        this.jwtIssuer = jwtIssuer;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return usuariosDemoConfig.buscarPorEmail(request.email())
            .filter(usuario -> usuario.password().equals(request.password()))
            .<ResponseEntity<?>>map(usuario -> {
                JwtIssuer.TokenEmitido token = jwtIssuer.emitir(usuario);
                return ResponseEntity.ok(new LoginResponse(
                    token.accessToken(),
                    "Bearer",
                    token.expiresAt(),
                    new UsuarioResponse(usuario.id(), usuario.nombre(), usuario.email(), usuario.roles())
                ));
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "credenciales_invalidas",
                "message", "Email o contraseña inválidos"
            )));
    }

    @GetMapping("/.well-known/jwks.json")
    public JwtIssuer.JwksResponse jwks() {
        return jwtIssuer.jwks();
    }

    public record LoginRequest(@Email String email, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UsuarioResponse user) {
    }

    public record UsuarioResponse(String id, String nombre, String email, List<String> roles) {
    }
}
