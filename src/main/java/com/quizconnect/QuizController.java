package com.quizconnect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuizController {

    private final QuizService quizService;
    private final OpenTdbClient openTdbClient;
    private final MyMemoryTranslationClient translationClient;
    private final DistractorAiClient distractorAiClient;

    public QuizController(
        QuizService quizService,
        OpenTdbClient openTdbClient,
        MyMemoryTranslationClient translationClient,
        DistractorAiClient distractorAiClient
    ) {
        this.quizService = quizService;
        this.openTdbClient = openTdbClient;
        this.translationClient = translationClient;
        this.distractorAiClient = distractorAiClient;
    }

    @GetMapping("/api/salas/{codigo}/participantes/{participanteId}/quiz")
    public ResponseEntity<?> entrarNoQuiz(@PathVariable String codigo, @PathVariable String participanteId) {
        try {
            return ResponseEntity.ok(quizService.entrarNoQuiz(codigo, participanteId));
        } catch (IllegalStateException erro) {
            return ResponseEntity.status(409).body(Map.of("mensagem", erro.getMessage()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @PostMapping("/api/salas/{codigo}/participantes/{participanteId}/respostas")
    public ResponseEntity<Map<String, Object>> responder(
        @PathVariable String codigo,
        @PathVariable String participanteId,
        @RequestBody Map<String, Object> payload
    ) {
        if (!(payload.get("perguntaId") instanceof Number id)
            || !(payload.get("resposta") instanceof String answer)) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Questão e resposta são obrigatórias."));
        }
        try {
            return ResponseEntity.ok(quizService.responder(codigo, participanteId, id.intValue(), answer));
        } catch (IllegalStateException erro) {
            return ResponseEntity.status(409).body(Map.of("mensagem", erro.getMessage()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @GetMapping("/api/salas/{codigo}/questoes")
    public ResponseEntity<?> listarQuestoes(
        @PathVariable String codigo,
        @AuthenticationPrincipal UserDetails user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("mensagem", "Entre com sua conta de professor para editar o quiz."));
        }
        try {
            return ResponseEntity.ok(quizService.listarQuestoesDaSala(codigo, user.getUsername()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.status(403).body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @PostMapping("/api/salas/{codigo}/questoes")
    public ResponseEntity<Map<String, Object>> adicionarQuestao(
        @PathVariable String codigo,
        @AuthenticationPrincipal UserDetails user,
        @RequestBody Map<String, Object> payload
    ) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("mensagem", "Entre com sua conta de professor para editar o quiz."));
        }
        String enunciado = texto(payload.get("enunciado"));
        String correta = texto(payload.get("respostaCorreta"));
        List<String> distratores = listaTextos(payload.get("distratores"));
        if (enunciado == null || correta == null || distratores == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "mensagem", "Informe a pergunta, a resposta correta e três campos de distrator."));
        }
        try {
            return ResponseEntity.ok(quizService.adicionarQuestao(
                codigo, user.getUsername(), enunciado, correta, distratores, "MANUAL"));
        } catch (IllegalStateException erro) {
            return ResponseEntity.status(409).body(Map.of("mensagem", erro.getMessage()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @PutMapping("/api/salas/{codigo}/questoes/{perguntaId}")
    public ResponseEntity<?> atualizarQuestao(
        @PathVariable String codigo,
        @PathVariable int perguntaId,
        @AuthenticationPrincipal UserDetails user,
        @RequestBody Map<String, Object> payload
    ) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("mensagem", "Entre com sua conta de professor para editar o quiz."));
        }
        String enunciado = texto(payload.get("enunciado"));
        String correta = texto(payload.get("respostaCorreta"));
        List<String> distratores = listaTextos(payload.get("distratores"));
        if (enunciado == null || correta == null || distratores == null) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Pergunta, resposta correta e três distratores são necessários."));
        }
        try {
            quizService.atualizarQuestao(codigo, user.getUsername(), perguntaId,
                enunciado, correta, distratores);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException erro) {
            return ResponseEntity.status(409).body(Map.of("mensagem", erro.getMessage()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @DeleteMapping("/api/salas/{codigo}/questoes/{perguntaId}")
    public ResponseEntity<?> removerQuestao(
        @PathVariable String codigo,
        @PathVariable int perguntaId,
        @AuthenticationPrincipal UserDetails user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("mensagem", "Entre com sua conta de professor para editar o quiz."));
        }
        try {
            quizService.removerQuestao(codigo, user.getUsername(), perguntaId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException erro) {
            return ResponseEntity.status(409).body(Map.of("mensagem", erro.getMessage()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.status(403).body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @PostMapping("/api/salas/{codigo}/importar-opentdb")
    public ResponseEntity<Map<String, Object>> importarOpenTdb(
        @PathVariable String codigo,
        @AuthenticationPrincipal UserDetails user,
        @RequestBody(required = false) Map<String, Object> payload
    ) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("mensagem", "Entre com sua conta de professor para importar questões."));
        }
        String professorEmail = user.getUsername();
        try {
            quizService.validarImportacaoOpenTdb(codigo, professorEmail);
            int quantidade = payload == null || !(payload.get("quantidade") instanceof Number count)
                ? 5 : Math.min(5, Math.max(1, count.intValue()));
            String tipo = payload == null || !(payload.get("tipo") instanceof String value) ? "multiple" : value;
            String dificuldade = payload == null || !(payload.get("dificuldade") instanceof String value) ? "" : value;
            List<QuizService.QuestaoImportada> traduzidas = new ArrayList<>();
            for (OpenTdbClient.Questao question : openTdbClient.buscarQuestoes(quantidade, tipo, dificuldade)) {
                traduzidas.add(new QuizService.QuestaoImportada(
                    translationClient.traduzir(question.enunciado()),
                    translationClient.traduzir(question.respostaCorreta())
                ));
            }
            int imported = quizService.importarQuestoes(codigo, professorEmail, traduzidas);
            return ResponseEntity.ok(Map.of("importadas", imported, "fonte", "OpenTDB", "idioma", "pt-BR"));
        } catch (IllegalStateException erro) {
            return ResponseEntity.status(409).body(Map.of("mensagem", erro.getMessage()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", erro.getMessage()));
        }
    }

    @PostMapping("/api/salas/{codigo}/sugestoes-distratores")
    public ResponseEntity<?> sugerirDistratores(
        @PathVariable String codigo,
        @AuthenticationPrincipal UserDetails user,
        @RequestBody Map<String, String> payload
    ) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("mensagem", "Entre com sua conta de professor para usar a IA."));
        }
        try {
            quizService.validarAvaliador(codigo, user.getUsername());
            return ResponseEntity.ok(Map.of("distratores", distractorAiClient.sugerir(
                payload.get("enunciado"), payload.get("respostaCorreta"))));
        } catch (IllegalStateException erro) {
            return ResponseEntity.status(503).body(Map.of("mensagem", erro.getMessage()));
        } catch (IllegalArgumentException erro) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", erro.getMessage()));
        }
    }

    private String texto(Object valor) {
        return valor instanceof String texto ? texto : null;
    }

    private List<String> listaTextos(Object valor) {
        if (!(valor instanceof List<?> lista) || lista.size() != 3
            || lista.stream().anyMatch(item -> !(item instanceof String))) {
            return null;
        }
        return lista.stream().map(String.class::cast).toList();
    }
}