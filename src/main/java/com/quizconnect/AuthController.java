package com.quizconnect;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final JdbcTemplate jdbc;
    private final AuthService authService;

    public AuthController(JdbcTemplate jdbc, AuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
    }

    @GetMapping("/api/auth/me")
    public Map<String, Object> me(Authentication authentication) {
        Map<String, Object> profile = new LinkedHashMap<>();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getPrincipal());
        profile.put("authenticated", authenticated);
        if (authenticated) {
            jdbc.query("SELECT usuario, nome, email FROM usuarios WHERE email = ?",
                (rs, row) -> Map.<String, String>of(
                    "usuario", rs.getString("usuario"),
                    "nome", rs.getString("nome"),
                    "email", rs.getString("email")
                ), authentication.getName()).stream().findFirst().ifPresent(profile::putAll);
        }
        return profile;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(authService.cadastrar(
                payload.get("usuario"), payload.get("nome"), payload.get("email"), payload.get("senha")));
        } catch (IllegalStateException erro) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", erro.getMessage()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @GetMapping("/api/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }
}