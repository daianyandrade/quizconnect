package com.quizconnect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

    private static final char[] ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    public QuizService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> criarQuiz(String professorEmail, String titulo) {
        if (professorEmail == null || professorEmail.isBlank()) {
            throw new IllegalArgumentException("Entre com sua conta de professor para criar um quiz.");
        }
        String tituloValidado = titulo == null || titulo.isBlank() ? "Quiz de Tecnologia" : titulo.trim();
        if (tituloValidado.length() > 80) {
            throw new IllegalArgumentException("O título deve ter no máximo 80 caracteres.");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE email = ?", Integer.class, professorEmail) == 0) {
            throw new IllegalArgumentException("A conta de professor não foi carregada. Entre novamente.");
        }
        String codigo;
        do {
            StringBuilder candidate = new StringBuilder(8);
            for (int index = 0; index < 8; index++) {
                candidate.append(ROOM_CODE_CHARS[SECURE_RANDOM.nextInt(ROOM_CODE_CHARS.length)]);
            }
            codigo = candidate.toString();
        } while (jdbc.queryForObject("SELECT COUNT(*) FROM salas WHERE codigo = ?", Integer.class, codigo) > 0);
        jdbc.update("INSERT INTO salas (codigo, titulo, avaliador_email) VALUES (?, ?, ?)",
            codigo, tituloValidado, professorEmail);
        return Map.of("codigo", codigo, "titulo", tituloValidado);
    }

    @Transactional
    public Map<String, Object> entrarSala(String codigo, String nome) {
        String codigoValidado = validarCodigo(codigo);
        Map<String, Object> sala = dadosSala(codigoValidado);
        if (Boolean.TRUE.equals(sala.get("finalizada"))) {
            throw new IllegalStateException("Esta atividade foi encerrada.");
        }
        String nomeValidado = validarTexto(nome, 50, "O nome deve ter entre 1 e 50 caracteres.");
        String participanteId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO participantes (id, codigo_sala, nome) VALUES (?, ?, ?)",
            participanteId, codigoValidado, nomeValidado);
        return Map.of("codigo", codigoValidado, "titulo", sala.get("titulo"),
            "participantId", participanteId, "nome", nomeValidado);
    }

    public Map<String, Object> consultarSala(String codigo) {
        String codigoValidado = validarCodigo(codigo);
        Map<String, Object> sala = dadosSala(codigoValidado);
        sala.put("jogadores", jdbc.query(
            "SELECT p.id, p.nome, COALESCE(SUM(r.correta), 0) AS pontuacao "
                + "FROM participantes p LEFT JOIN tentativas t ON t.participante_id = p.id "
                + "LEFT JOIN respostas r ON r.tentativa_id = t.id "
                + "WHERE p.codigo_sala = ? GROUP BY p.id ORDER BY p.criado_em",
            (rs, row) -> Map.<String, Object>of("id", rs.getString("id"),
                "nome", rs.getString("nome"), "pontuacao", rs.getInt("pontuacao")), codigoValidado));
        return sala;
    }

    public Map<String, Object> painelProfessor(String codigo, String professorEmail) {
        String codigoValidado = validarCodigo(codigo);
        validarAvaliador(codigoValidado, professorEmail);
        Map<String, Object> painel = dadosSala(codigoValidado);
        List<Map<String, Object>> questoes = jdbc.query(
            "SELECT id, enunciado, resposta_correta, ordem FROM perguntas "
                + "WHERE codigo_sala = ? ORDER BY ordem",
            (rs, row) -> {
                Map<String, Object> question = new LinkedHashMap<>();
                question.put("id", rs.getInt("id"));
                question.put("enunciado", rs.getString("enunciado"));
                question.put("respostaCorreta", rs.getString("resposta_correta"));
                question.put("ordem", rs.getInt("ordem"));
                return question;
            }, codigoValidado);
        List<Map<String, Object>> alunos = jdbc.query(
            "SELECT p.id, p.nome, COALESCE(SUM(r.correta), 0) AS pontuacao "
                + "FROM participantes p LEFT JOIN tentativas t ON t.participante_id = p.id "
                + "LEFT JOIN respostas r ON r.tentativa_id = t.id "
                + "WHERE p.codigo_sala = ? GROUP BY p.id ORDER BY p.criado_em",
            (rs, row) -> {
                Map<String, Object> student = new LinkedHashMap<>();
                student.put("id", rs.getString("id"));
                student.put("nome", rs.getString("nome"));
                student.put("pontuacao", rs.getInt("pontuacao"));
                student.put("respostas", new LinkedHashMap<Integer, Map<String, Object>>());
                return student;
            }, codigoValidado);
        Map<String, Map<String, Object>> alunosPorId = new LinkedHashMap<>();
        for (Map<String, Object> aluno : alunos) {
            alunosPorId.put((String) aluno.get("id"), aluno);
        }
        jdbc.query("SELECT t.participante_id, r.pergunta_id, r.resposta, r.correta "
                + "FROM respostas r JOIN tentativas t ON t.id = r.tentativa_id "
                + "JOIN participantes p ON p.id = t.participante_id WHERE p.codigo_sala = ?",
            rs -> {
                Map<String, Object> aluno = alunosPorId.get(rs.getString("participante_id"));
                if (aluno != null) {
                    @SuppressWarnings("unchecked")
                    Map<Integer, Map<String, Object>> respostas = (Map<Integer, Map<String, Object>>) aluno.get("respostas");
                    respostas.put(rs.getInt("pergunta_id"), Map.of(
                        "resposta", rs.getString("resposta"),
                        "correta", rs.getInt("correta") == 1
                    ));
                }
            }, codigoValidado);
        painel.put("questoes", questoes);
        painel.put("alunos", alunos);
        return painel;
    }

    @Transactional
    public Map<String, Object> atualizarStatus(String codigo, String professorEmail, Boolean pausada, Boolean finalizada) {
        String codigoValidado = validarCodigo(codigo);
        validarAvaliador(codigoValidado, professorEmail);
        Map<String, Object> atual = dadosSala(codigoValidado);
        if (Boolean.TRUE.equals(atual.get("finalizada")) && !Boolean.TRUE.equals(finalizada)) {
            throw new IllegalStateException("Uma atividade encerrada não pode ser reaberta.");
        }
        jdbc.update("UPDATE salas SET pausada = ?, finalizada = ? WHERE codigo = ?",
            Boolean.TRUE.equals(pausada) ? 1 : 0,
            Boolean.TRUE.equals(finalizada) || Boolean.TRUE.equals(atual.get("finalizada")) ? 1 : 0,
            codigoValidado);
        return dadosSala(codigoValidado);
    }

    public List<Map<String, Object>> listarQuestoesDaSala(String codigo, String professorEmail) {
        String codigoValidado = validarCodigo(codigo);
        validarAvaliador(codigoValidado, professorEmail);
        List<Map<String, Object>> questoes = jdbc.query(
            "SELECT id, enunciado, resposta_correta, fonte, ordem FROM perguntas "
                + "WHERE codigo_sala = ? ORDER BY ordem",
            (rs, row) -> {
                Map<String, Object> question = new LinkedHashMap<>();
                question.put("id", rs.getInt("id"));
                question.put("enunciado", rs.getString("enunciado"));
                question.put("respostaCorreta", rs.getString("resposta_correta"));
                question.put("fonte", rs.getString("fonte"));
                question.put("ordem", rs.getInt("ordem"));
                return question;
            }, codigoValidado);
        for (Map<String, Object> question : questoes) {
            question.put("distratores", jdbc.query(
                "SELECT texto FROM distratores WHERE pergunta_id = ? ORDER BY posicao",
                (rs, row) -> rs.getString("texto"), question.get("id")));
        }
        return questoes;
    }

    @Transactional
    public Map<String, Object> adicionarQuestao(
        String codigo, String professorEmail, String enunciado, String respostaCorreta,
        List<String> distratores, String fonte
    ) {
        String codigoValidado = validarCodigo(codigo);
        validarAvaliador(codigoValidado, professorEmail);
        validarSalaEditavel(codigoValidado);
        validarEnunciado(enunciado);
        String correta = validarTexto(respostaCorreta, 120, "A resposta correta deve ter até 120 caracteres.");
        List<String> erros = validarDistratores(distratores, correta, true);
        int ordem = jdbc.queryForObject("SELECT COUNT(*) FROM perguntas WHERE codigo_sala = ?",
            Integer.class, codigoValidado);
        int id = inserirQuestao(codigoValidado, enunciado.trim(), correta, erros, fonte, ordem);
        return Map.of("id", id);
    }

    @Transactional
    public void atualizarQuestao(
        String codigo, String professorEmail, int perguntaId, String enunciado,
        String respostaCorreta, List<String> distratores
    ) {
        String codigoValidado = validarCodigo(codigo);
        validarAvaliador(codigoValidado, professorEmail);
        validarSalaEditavel(codigoValidado);
        validarEnunciado(enunciado);
        String correta = validarTexto(respostaCorreta, 120, "A resposta correta deve ter até 120 caracteres.");
        List<String> erros = validarDistratores(distratores, correta, true);
        if (jdbc.update("UPDATE perguntas SET enunciado = ?, resposta_correta = ? "
                + "WHERE codigo_sala = ? AND id = ?", enunciado.trim(), correta, codigoValidado, perguntaId) == 0) {
            throw new IllegalArgumentException("Questão não encontrada nesta sala.");
        }
        for (int index = 0; index < erros.size(); index++) {
            jdbc.update("UPDATE distratores SET texto = ? WHERE pergunta_id = ? AND posicao = ?",
                erros.get(index).isBlank() ? null : erros.get(index), perguntaId, index + 1);
        }
    }

    @Transactional
    public int importarQuestoes(String codigo, String professorEmail, List<QuestaoImportada> questoes) {
        String codigoValidado = validarCodigo(codigo);
        validarAvaliador(codigoValidado, professorEmail);
        validarSalaEditavel(codigoValidado);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM perguntas WHERE codigo_sala = ? AND fonte = 'OPENTDB'",
            Integer.class, codigoValidado) > 0) {
            throw new IllegalStateException("Este quiz já importou questões do OpenTDB.");
        }
        int ordem = jdbc.queryForObject("SELECT COUNT(*) FROM perguntas WHERE codigo_sala = ?",
            Integer.class, codigoValidado);
        for (QuestaoImportada question : questoes) {
            validarEnunciado(question.enunciado());
            String correta = validarTexto(question.respostaCorreta(), 120,
                "A resposta correta importada excede 120 caracteres.");
            inserirQuestao(codigoValidado, question.enunciado(), correta,
                List.of("", "", ""), "OPENTDB", ordem++);
        }
        return questoes.size();
    }

    @Transactional
    public void removerQuestao(String codigo, String professorEmail, int perguntaId) {
        String codigoValidado = validarCodigo(codigo);
        validarAvaliador(codigoValidado, professorEmail);
        validarSalaEditavel(codigoValidado);
        if (jdbc.update("DELETE FROM perguntas WHERE codigo_sala = ? AND id = ?", codigoValidado, perguntaId) == 0) {
            throw new IllegalArgumentException("Questão não encontrada nesta sala.");
        }
    }

    @Transactional
    public Map<String, Object> entrarNoQuiz(String codigo, String participanteId) {
        String codigoValidado = validarCodigo(codigo);
        validarParticipante(codigoValidado, participanteId);
        Map<String, Object> sala = dadosSala(codigoValidado);
        validarAtividadeAtiva(sala);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM perguntas q WHERE q.codigo_sala = ? AND "
                + "(SELECT COUNT(*) FROM distratores d WHERE d.pergunta_id = q.id "
                + "AND d.texto IS NOT NULL AND TRIM(d.texto) <> '') < 3",
            Integer.class, codigoValidado) > 0) {
            throw new IllegalStateException("O avaliador precisa preencher os três distratores de cada questão.");
        }
        String attemptId = jdbc.query("SELECT id FROM tentativas WHERE participante_id = ?",
            rs -> rs.next() ? rs.getString(1) : null, participanteId);
        if (attemptId == null) {
            attemptId = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO tentativas (id, participante_id) VALUES (?, ?)", attemptId, participanteId);
            List<Integer> ids = jdbc.query("SELECT id FROM perguntas WHERE codigo_sala = ? ORDER BY ordem",
                (rs, row) -> rs.getInt("id"), codigoValidado);
            for (int index = 0; index < ids.size(); index++) {
                jdbc.update("INSERT INTO tentativa_perguntas (tentativa_id, pergunta_id, ordem) VALUES (?, ?, ?)",
                    attemptId, ids.get(index), index);
            }
        }
        String id = attemptId;
        List<Map<String, Object>> questoes = jdbc.query(
            "SELECT q.id, q.enunciado, q.resposta_correta, r.resposta, r.correta "
                + "FROM tentativa_perguntas tq JOIN perguntas q ON q.id = tq.pergunta_id "
                + "LEFT JOIN respostas r ON r.tentativa_id = tq.tentativa_id AND r.pergunta_id = q.id "
                + "WHERE tq.tentativa_id = ? ORDER BY tq.ordem",
            (rs, row) -> {
                int questionId = rs.getInt("id");
                List<String> options = new ArrayList<>();
                options.add(rs.getString("resposta_correta"));
                options.addAll(jdbc.query("SELECT texto FROM distratores WHERE pergunta_id = ? ORDER BY posicao",
                    (optionRs, optionRow) -> optionRs.getString("texto"), questionId));
                Collections.shuffle(options);
                Map<String, Object> question = new LinkedHashMap<>();
                question.put("id", questionId);
                question.put("enunciado", rs.getString("enunciado"));
                question.put("alternativas", options);
                String answer = rs.getString("resposta");
                question.put("respondida", answer != null);
                if (answer != null) {
                    question.put("respostaEscolhida", answer);
                    question.put("correta", rs.getInt("correta") == 1);
                    question.put("respostaCorreta", rs.getString("resposta_correta"));
                }
                return question;
            }, id);
        int score = pontuacao(id);
        long answered = questoes.stream().filter(question -> Boolean.TRUE.equals(question.get("respondida"))).count();
        return Map.of("tentativaId", id, "perguntas", questoes, "pontuacao", score,
            "respondidas", answered, "concluido", !questoes.isEmpty() && answered == questoes.size());
    }

    @Transactional
    public Map<String, Object> responder(String codigo, String participanteId, int perguntaId, String resposta) {
        String codigoValidado = validarCodigo(codigo);
        validarParticipante(codigoValidado, participanteId);
        validarAtividadeAtiva(dadosSala(codigoValidado));
        if (resposta == null || resposta.isBlank()) {
            throw new IllegalArgumentException("A resposta é obrigatória.");
        }
        String attemptId = jdbc.query("SELECT id FROM tentativas WHERE participante_id = ?",
            rs -> rs.next() ? rs.getString(1) : null, participanteId);
        if (attemptId == null || jdbc.queryForObject(
            "SELECT COUNT(*) FROM tentativa_perguntas WHERE tentativa_id = ? AND pergunta_id = ?",
            Integer.class, attemptId, perguntaId) == 0) {
            throw new IllegalArgumentException("Essa questão não pertence ao quiz do participante.");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM respostas WHERE tentativa_id = ? AND pergunta_id = ?",
            Integer.class, attemptId, perguntaId) > 0) {
            throw new IllegalStateException("Esta questão já foi respondida.");
        }
        String correta = jdbc.query("SELECT q.resposta_correta FROM perguntas q WHERE q.id = ? AND "
                + "(q.resposta_correta = ? OR EXISTS (SELECT 1 FROM distratores d "
                + "WHERE d.pergunta_id = q.id AND d.texto = ?))",
            rs -> rs.next() ? rs.getString(1) : null, perguntaId, resposta, resposta);
        if (correta == null) {
            throw new IllegalArgumentException("A resposta enviada não pertence às alternativas.");
        }
        boolean isCorrect = correta.equals(resposta);
        jdbc.update("INSERT INTO respostas (tentativa_id, pergunta_id, resposta, correta) VALUES (?, ?, ?, ?)",
            attemptId, perguntaId, resposta, isCorrect ? 1 : 0);
        return Map.of("correta", isCorrect, "respostaCorreta", correta, "pontuacao", pontuacao(attemptId));
    }

    public List<Map<String, Object>> ranking(String codigo) {
        String codigoValidado = validarCodigo(codigo);
        dadosSala(codigoValidado);
        return jdbc.query("SELECT p.nome, COUNT(CASE WHEN r.correta = 1 THEN 1 END) AS pontuacao "
                + "FROM participantes p LEFT JOIN tentativas t ON t.participante_id = p.id "
                + "LEFT JOIN respostas r ON r.tentativa_id = t.id WHERE p.codigo_sala = ? "
                + "GROUP BY p.id ORDER BY pontuacao DESC, p.criado_em, p.nome",
            (rs, row) -> Map.<String, Object>of("nome", rs.getString("nome"),
                "pontuacao", rs.getInt("pontuacao")), codigoValidado);
    }

    public void validarImportacaoOpenTdb(String codigo, String professorEmail) {
        String codigoValidado = validarCodigo(codigo);
        validarAvaliador(codigoValidado, professorEmail);
        validarSalaEditavel(codigoValidado);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM perguntas WHERE codigo_sala = ? AND fonte = 'OPENTDB'",
            Integer.class, codigoValidado) > 0) {
            throw new IllegalStateException("Este quiz já importou questões do OpenTDB.");
        }
    }

    public void validarAvaliador(String codigo, String professorEmail) {
        if (professorEmail == null || jdbc.queryForObject(
            "SELECT COUNT(*) FROM salas WHERE codigo = ? AND avaliador_email = ?",
            Integer.class, validarCodigo(codigo), professorEmail) == 0) {
            throw new IllegalArgumentException("Somente o professor proprietário pode acessar esta área.");
        }
    }

    private int inserirQuestao(String codigo, String enunciado, String correta,
        List<String> distratores, String fonte, int ordem) {
        int id = jdbc.queryForObject(
            "INSERT INTO perguntas (codigo_sala, enunciado, resposta_correta, fonte, ordem) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING id",
            Integer.class, codigo, enunciado, correta, fonte, ordem);
        for (int index = 0; index < 3; index++) {
            String distractor = distratores.size() > index ? distratores.get(index) : "";
            jdbc.update("INSERT INTO distratores (pergunta_id, posicao, texto) VALUES (?, ?, ?)",
                id, index + 1, distractor.isBlank() ? null : distractor);
        }
        return id;
    }

    private List<String> validarDistratores(List<String> distratores, String correta, boolean permitirVazios) {
        if (distratores == null || distratores.size() != 3) {
            throw new IllegalArgumentException("Informe exatamente três respostas incorretas.");
        }
        List<String> validos = distratores.stream().map(value -> value == null ? "" : value.trim()).toList();
        for (String text : validos) {
            if (!permitirVazios && text.isBlank()) {
                throw new IllegalArgumentException("Preencha os três distratores antes de salvar.");
            }
            if (text.length() > 100 || text.lines().count() > 2) {
                throw new IllegalArgumentException("Cada distrator deve ter até 100 caracteres e no máximo duas linhas.");
            }
            if (!text.isBlank() && (text.equalsIgnoreCase(correta)
                || validos.stream().filter(other -> !other.isBlank()).filter(other -> other.equalsIgnoreCase(text)).count() > 1)) {
                throw new IllegalArgumentException("Distratores devem ser únicos e diferentes da resposta correta.");
            }
        }
        return validos;
    }

    private Map<String, Object> dadosSala(String codigo) {
        List<Map<String, Object>> rows = jdbc.query("SELECT codigo, titulo, pausada, finalizada "
                + "FROM salas WHERE codigo = ?",
            (rs, row) -> Map.<String, Object>of(
                "codigo", rs.getString("codigo"),
                "titulo", rs.getString("titulo"),
                "pausada", rs.getInt("pausada") == 1,
                "finalizada", rs.getInt("finalizada") == 1
            ), codigo);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Quiz não encontrado.");
        }
        return new LinkedHashMap<>(rows.get(0));
    }

    private void validarSalaEditavel(String codigo) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tentativas t JOIN participantes p "
            + "ON p.id = t.participante_id WHERE p.codigo_sala = ?", Integer.class, codigo) > 0) {
            throw new IllegalStateException("O quiz não pode ser alterado depois que um aluno começou.");
        }
        Map<String, Object> sala = dadosSala(codigo);
        if (Boolean.TRUE.equals(sala.get("finalizada"))) {
            throw new IllegalStateException("A atividade já foi encerrada.");
        }
    }

    private void validarAtividadeAtiva(Map<String, Object> sala) {
        if (Boolean.TRUE.equals(sala.get("finalizada"))) {
            throw new IllegalStateException("Esta atividade foi encerrada.");
        }
        if (Boolean.TRUE.equals(sala.get("pausada"))) {
            throw new IllegalStateException("Esta atividade está pausada pelo professor.");
        }
    }

    private void validarParticipante(String codigo, String participantId) {
        if (participantId == null || jdbc.queryForObject(
            "SELECT COUNT(*) FROM participantes WHERE id = ? AND codigo_sala = ?",
            Integer.class, participantId, codigo) == 0) {
            throw new IllegalArgumentException("Participante não encontrado nesta sala.");
        }
    }

    private int pontuacao(String attemptId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM respostas WHERE tentativa_id = ? AND correta = 1",
            Integer.class, attemptId);
    }

    private String validarCodigo(String codigo) {
        if (codigo == null || !codigo.trim().matches("[A-HJ-NP-Z2-9]{8}")) {
            throw new IllegalArgumentException("Código inválido.");
        }
        return codigo.trim().toUpperCase();
    }

    private String validarEnunciado(String enunciado) {
        validarTexto(enunciado, 500, "A pergunta deve ter entre 1 e 500 caracteres.");
        return enunciado.trim();
    }

    private String validarTexto(String text, int max, String message) {
        if (text == null || text.isBlank() || text.trim().length() > max) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    public record QuestaoImportada(String enunciado, String respostaCorreta) {
    }
}