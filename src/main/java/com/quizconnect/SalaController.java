package com.quizconnect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SalaController {

    // Temporario: sera substituido pelo repositorio SQL quando o banco for integrado.
    private final Map<String, Sala> salas = new HashMap<>();

    @PostMapping("/api/salas")
    public Map<String, String> criarSala(@RequestBody(required = false) Map<String, String> payload) {
        String codigo = UUID.randomUUID()
            .toString()
            .substring(0, 6)
            .toUpperCase();
        String disciplina = payload == null || payload.get("disciplina") == null
            ? "Todas"
            : payload.get("disciplina");
        if (disciplina.isBlank()) {
            disciplina = "Todas";
        }

        salas.put(codigo, new Sala(codigo, disciplina));

        return Map.of(
            "codigo", codigo,
            "disciplina", disciplina,
            "mensagem", "Sala criada com sucesso!"
        );
    }

    public Map<String, String> criarSala() {
        return criarSala(Map.of());
    }

    @PostMapping("/api/salas/entrar")
    public ResponseEntity<Map<String, Object>> entrarSala(@RequestBody Map<String, String> payload) {
        String codigo = payload.get("codigo");
        String nome = payload.get("nome");

        if (codigo == null || nome == null || nome.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "mensagem", "Código e nome são obrigatórios."
            ));
        }

        Sala sala = salas.get(codigo.toUpperCase());

        if (sala == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "mensagem", "Sala não encontrada."
            ));
        }

        sala.addJogador(nome);

        return ResponseEntity.ok(Map.of(
            "mensagem", "Você entrou na sala!",
            "codigo", codigo.toUpperCase(),
            "disciplina", sala.getDisciplina(),
            "jogadores", sala.getJogadores()
        ));
    }

    @GetMapping("/api/salas/{codigo}")
    public ResponseEntity<Map<String, Object>> consultarSala(@PathVariable String codigo) {
        Sala sala = salas.get(codigo.toUpperCase());

        if (sala == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
            "codigo", codigo.toUpperCase(),
            "disciplina", sala.getDisciplina(),
            "jogadores", sala.getJogadores()
        ));
    }

    static class Sala {
        private final String codigo;
        private final String disciplina;
        private final List<String> jogadores = new ArrayList<>();

        public Sala(String codigo, String disciplina) {
            this.codigo = codigo;
            this.disciplina = disciplina;
        }

        public void addJogador(String nome) {
            jogadores.add(nome);
        }

        public List<String> getJogadores() {
            return jogadores;
        }

        public String getDisciplina() {
            return disciplina;
        }
    }
}
     