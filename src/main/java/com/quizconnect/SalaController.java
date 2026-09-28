package com.quizconnect;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SalaController {

    private final QuizService quizService;

    public SalaController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/api/salas")
    public ResponseEntity<Map<String, Object>> criarSala(
        @RequestBody Map<String, String> payload,
        @AuthenticationPrincipal UserDetails user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("mensagem", "Entre com sua conta de professor para criar um quiz."));
        }
        try {
            return ResponseEntity.ok(quizService.criarQuiz(user.getUsername(), payload.get("titulo")));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @PostMapping("/api/salas/entrar")
    public ResponseEntity<Map<String, Object>> entrarSala(@RequestBody Map<String, String> payload) {
        try {
            return ResponseEntity.ok(quizService.entrarSala(payload.get("codigo"), payload.get("nome")));
        } catch (IllegalStateException erro) {
            return ResponseEntity.status(409).body(Map.of("mensagem", erro.getMessage()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @GetMapping("/api/salas/{codigo}")
    public ResponseEntity<?> consultarSala(@PathVariable String codigo) {
        try {
            return ResponseEntity.ok(quizService.consultarSala(codigo));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/api/salas/{codigo}/professor")
    public ResponseEntity<?> painelProfessor(
        @PathVariable String codigo,
        @AuthenticationPrincipal UserDetails user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("mensagem", "Entre com sua conta de professor para abrir o painel."));
        }
        try {
            return ResponseEntity.ok(quizService.painelProfessor(codigo, user.getUsername()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.status(403).body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @PatchMapping("/api/salas/{codigo}/status")
    public ResponseEntity<?> atualizarStatus(
        @PathVariable String codigo,
        @AuthenticationPrincipal UserDetails user,
        @RequestBody Map<String, Object> payload
    ) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("mensagem", "Entre com sua conta de professor para controlar a atividade."));
        }
        Object paused = payload.get("pausada");
        Object finished = payload.get("finalizada");
        if (!(paused instanceof Boolean) && !(finished instanceof Boolean)) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Informe o estado de pausa ou encerramento."));
        }
        try {
            return ResponseEntity.ok(quizService.atualizarStatus(
                codigo, user.getUsername(),
                paused instanceof Boolean value ? value : null,
                finished instanceof Boolean value ? value : null
            ));
        } catch (IllegalStateException erro) {
            return ResponseEntity.status(409).body(Map.of("mensagem", erro.getMessage()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.status(403).body(Map.of("mensagem", erro.getMessage()));
        }
    }
}