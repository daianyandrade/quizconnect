package com.quizconnect;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class QuizUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbc;

    public QuizUserDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        String normalizedLogin = login.trim().toLowerCase(Locale.ROOT);
        List<Map<String, String>> accounts = jdbc.query(
            "SELECT email, password_hash FROM usuarios WHERE lower(usuario) = ? OR lower(email) = ?",
            (rs, row) -> Map.of("email", rs.getString("email"),
                "password", rs.getString("password_hash") == null ? "" : rs.getString("password_hash")),
            normalizedLogin, normalizedLogin);
        if (accounts.isEmpty() || accounts.get(0).get("password").isBlank()) {
            throw new UsernameNotFoundException("Credenciais inválidas.");
        }
        return User.withUsername(accounts.get(0).get("email"))
            .password(accounts.get(0).get("password"))
            .roles("PROFESSOR")
            .build();
    }
}