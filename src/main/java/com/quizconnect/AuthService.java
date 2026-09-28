package com.quizconnect;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,32}$");
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public AuthService(org.springframework.jdbc.core.JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Map<String, String> cadastrar(String usuario, String nome, String email, String password) {
        String nomeValidado = texto(nome, 100, "Informe seu nome completo (até 100 caracteres).");
        String usuarioValidado = usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(usuarioValidado).matches()) {
            throw new IllegalArgumentException("O usuário deve ter de 3 a 32 caracteres: letras, números, ponto, hífen ou sublinhado.");
        }
        String emailValidado = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (emailValidado.length() > 254 || !EMAIL_PATTERN.matcher(emailValidado).matches()) {
            throw new IllegalArgumentException("Informe um endereço de e-mail válido.");
        }
        if (password == null || password.length() < 12
            || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter no mínimo 12 caracteres e no máximo 72 bytes.");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE lower(usuario) = ? OR lower(email) = ?",
            Integer.class, usuarioValidado, emailValidado) > 0) {
            throw new IllegalStateException("Este usuário ou e-mail já está cadastrado.");
        }
        try {
            jdbc.update("INSERT INTO usuarios (email, usuario, nome, password_hash) VALUES (?, ?, ?, ?)",
                emailValidado, usuarioValidado, nomeValidado, passwordEncoder.encode(password));
        } catch (DataAccessException erro) {
            if (erro.getMessage() != null && erro.getMessage().contains("UNIQUE constraint failed")) {
                throw new IllegalStateException("Este usuário ou e-mail já está cadastrado.");
            }
            throw erro;
        }
        return Map.of("usuario", usuarioValidado, "nome", nomeValidado, "email", emailValidado);
    }

    private String texto(String value, int max, String error) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(error);
        }
        return value.trim();
    }
}