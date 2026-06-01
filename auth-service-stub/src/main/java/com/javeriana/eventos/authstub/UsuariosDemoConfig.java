package com.javeriana.eventos.authstub;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Validated
@ConfigurationProperties(prefix = "auth")
public record UsuariosDemoConfig(
    @NotBlank String issuer,
    @NotBlank String keyId,
    @NotBlank String privateKey,
    @NotNull Duration tokenTtl,
    @Valid @NotEmpty List<UsuarioDemo> users
) {
    public Optional<UsuarioDemo> buscarPorEmail(String email) {
        return users.stream()
            .filter(user -> user.email().equalsIgnoreCase(email))
            .findFirst();
    }

    public record UsuarioDemo(
        @NotBlank String id,
        @NotBlank String nombre,
        @Email String email,
        @NotBlank String password,
        @NotEmpty List<String> roles
    ) {
    }
}
