package com.quizconnect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuizController {

    // Perguntas temporarias; futuramente virao do banco de dados.
    private final List<Map<String, Object>> perguntas = List.of(
        pergunta(1, "Programação", "Qual estrutura de repetição executa um bloco enquanto uma condição for verdadeira?",
            List.of("if", "while", "switch", "try/catch"), "while"),
        pergunta(2, "Banco de Dados", "Qual comando SQL é usado para consultar dados?",
            List.of("SELECT", "INSERT", "UPDATE", "DELETE"), "SELECT"),
        pergunta(3, "Engenharia de Software", "Qual prática busca verificar automaticamente se uma pequena parte do código funciona?",
            List.of("Teste unitário", "Deploy manual", "Modelagem de rede", "Backup"), "Teste unitário"),
        pergunta(4, "Redes de Computadores", "Qual protocolo é usado para acessar páginas web com segurança?",
            List.of("FTP", "HTTP", "HTTPS", "SMTP"), "HTTPS"),
        pergunta(5, "Sistemas Operacionais", "Qual é uma função principal de um sistema operacional?",
            List.of("Gerenciar recursos do computador", "Criar cabos de rede", "Desenhar páginas web", "Substituir o banco de dados"),
            "Gerenciar recursos do computador")
    );

    @GetMapping("/api/perguntas")
    public List<Map<String, Object>> listarPerguntas(@RequestParam(required = false) String disciplina) {
        // O filtro permite criar uma sala para uma disciplina ou para todas.
        List<Map<String, Object>> perguntasDaDisciplina = perguntas.stream()
            .filter(pergunta -> disciplina == null || disciplina.isBlank() || disciplina.equals("Todas")
                || disciplina.equals(pergunta.get("disciplina")))
            .toList();
        List<Map<String, Object>> ordemAleatoria = new ArrayList<>(perguntasDaDisciplina);
        Collections.shuffle(ordemAleatoria);

        // O gabarito fica fora da resposta publica e nao chega ao navegador.
        return ordemAleatoria.stream()
            .map(pergunta -> Map.of(
                "id", pergunta.get("id"),
                "disciplina", pergunta.get("disciplina"),
                "enunciado", pergunta.get("enunciado"),
                "alternativas", alternativasAleatorias(pergunta)
            ))
            .toList();
    }

    public List<Map<String, Object>> listarPerguntas() {
        return listarPerguntas(null);
    }

    @SuppressWarnings("unchecked")
    private static List<String> alternativasAleatorias(Map<String, Object> pergunta) {
        // Copia antes de embaralhar para nao alterar a lista original.
        List<String> alternativas = new ArrayList<>((List<String>) pergunta.get("alternativas"));
        Collections.shuffle(alternativas);
        return alternativas;
    }

    @PostMapping("/api/perguntas/{id}/responder")
    public ResponseEntity<Map<String, Object>> responder(
        @PathVariable int id,
        @RequestBody Map<String, String> resposta
    ) {
        String respostaEscolhida = resposta.get("resposta");

        Map<String, Object> pergunta = perguntas.stream()
            .filter(item -> item.get("id").equals(id))
            .findFirst()
            .orElse(null);

        if (pergunta == null || respostaEscolhida == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "mensagem", "Pergunta ou resposta inválida."
            ));
        }

        boolean correta = pergunta.get("respostaCorreta").equals(respostaEscolhida);

        return ResponseEntity.ok(Map.of(
            "correta", correta,
            "respostaCorreta", pergunta.get("respostaCorreta")
        ));
    }

    private static Map<String, Object> pergunta(
        int id,
        String disciplina,
        String enunciado,
        List<String> alternativas,
        String respostaCorreta
    ) {
        return Map.of(
            "id", id,
            "disciplina", disciplina,
            "enunciado", enunciado,
            "alternativas", alternativas,
            "respostaCorreta", respostaCorreta
        );
    }
}

