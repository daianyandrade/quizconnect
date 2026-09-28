package com.quizconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:file:quizconnect-test-v2?mode=memory&cache=shared",
    "spring.datasource.hikari.maximum-pool-size=1"
})
@AutoConfigureMockMvc
class QuizconnectApplicationTests {

    private static final String PROFESSOR_EMAIL = "professor@example.test";
    private static final String PROFESSOR_USERNAME = "professor-teste";
    private static final String PROFESSOR_PASSWORD = "UmaSenhaSegura123!";

    @Autowired
    private QuizService quizService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private QuizUserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void registrarProfessorDeTeste() {
        jdbc.update("INSERT INTO usuarios (email, usuario, nome, password_hash) VALUES (?, ?, ?, ?) "
            + "ON CONFLICT(email) DO NOTHING", PROFESSOR_EMAIL, PROFESSOR_USERNAME,
            "Professora Teste", passwordEncoder.encode(PROFESSOR_PASSWORD));
    }

    @Test
    void cadastroCriaUsuarioComSenhaBcryptENomeCompleto() {
        Map<String, String> account = authService.cadastrar("nova-professora", "Nova Professora",
            "nova@example.test", "OutraSenhaSegura123!");
        String storedPassword = jdbc.queryForObject(
            "SELECT password_hash FROM usuarios WHERE email = ?", String.class, "nova@example.test");

        assertThat(account).containsEntry("usuario", "nova-professora")
            .containsEntry("nome", "Nova Professora")
            .containsEntry("email", "nova@example.test");
        assertThat(storedPassword).isNotEqualTo("OutraSenhaSegura123!");
        assertThat(passwordEncoder.matches("OutraSenhaSegura123!", storedPassword)).isTrue();
    }

    @Test
    void endpointDeCadastroAceitaOsQuatroCamposERejeitaDuplicata() throws Exception {
        String payload = "{\"usuario\":\"docente-novo\",\"nome\":\"Docente Novo\","
            + "\"email\":\"docente.novo@example.test\",\"senha\":\"SenhaLocalForte123!\"}";
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.usuario").value("docente-novo"))
            .andExpect(jsonPath("$.nome").value("Docente Novo"));

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isConflict());
    }

    @Test
    void autenticacaoAceitaUsuarioOuEmailEAbreSessaoDoProfessor() throws Exception {
        assertThat(userDetailsService.loadUserByUsername(PROFESSOR_USERNAME).getUsername())
            .isEqualTo(PROFESSOR_EMAIL);
        assertThat(userDetailsService.loadUserByUsername(PROFESSOR_EMAIL).getUsername())
            .isEqualTo(PROFESSOR_EMAIL);
        verificarLoginCriaSessao(PROFESSOR_USERNAME);
        verificarLoginCriaSessao(PROFESSOR_EMAIL);
    }

    private void verificarLoginCriaSessao(String login) throws Exception {
        var loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .param("login", login)
                .param("senha", PROFESSOR_PASSWORD))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.usuario").value(PROFESSOR_USERNAME))
            .andExpect(jsonPath("$.nome").value("Professora Teste"));
        mockMvc.perform(post("/api/salas")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Quiz autenticado\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void criacaoVinculaQuizAoProfessorSemCriarAlunoAutomaticamente() {
        Map<String, Object> room = quizService.criarQuiz(PROFESSOR_EMAIL, "Avaliação de redes");
        Map<String, Object> storedRoom = quizService.consultarSala((String) room.get("codigo"));

        assertThat(room).containsEntry("titulo", "Avaliação de redes");
        assertThat(storedRoom.get("jogadores")).asList().isEmpty();
        assertThat(quizService.painelProfessor((String) room.get("codigo"), PROFESSOR_EMAIL))
            .containsEntry("titulo", "Avaliação de redes");
    }

    @Test
    void alunoEntraSomenteComNomeERespostaCorretaFicaOcultaAteResponder() {
        Map<String, Object> room = quizService.criarQuiz(PROFESSOR_EMAIL, "Quiz de banco");
        String code = (String) room.get("codigo");
        int questionId = ((Number) quizService.adicionarQuestao(code, PROFESSOR_EMAIL,
            "Qual comando consulta dados?", "SELECT", List.of("", "", ""), "MANUAL").get("id")).intValue();
        String participantId = (String) quizService.entrarSala(code, "Marina").get("participantId");

        assertThatThrownBy(() -> quizService.entrarNoQuiz(code, participantId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("três distratores");
        quizService.atualizarQuestao(code, PROFESSOR_EMAIL, questionId, "Qual comando consulta dados?",
            "SELECT", List.of("INSERT", "UPDATE", "DELETE"));
        Map<String, Object> attempt = quizService.entrarNoQuiz(code, participantId);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) attempt.get("perguntas");

        assertThat(questions).hasSize(1);
        assertThat(questions.get(0)).doesNotContainKey("respostaCorreta");
        Map<String, Object> result = quizService.responder(code, participantId, questionId, "SELECT");
        assertThat(result).containsEntry("correta", true).containsEntry("pontuacao", 1);
        assertThat(quizService.ranking(code).get(0)).containsEntry("nome", "Marina").containsEntry("pontuacao", 1);

        Map<String, Object> panel = quizService.painelProfessor(code, PROFESSOR_EMAIL);
        List<Map<String, Object>> students = (List<Map<String, Object>>) panel.get("alunos");
        Map<Integer, Map<String, Object>> answers = (Map<Integer, Map<String, Object>>) students.get(0).get("respostas");
        assertThat(answers.get(questionId)).containsEntry("resposta", "SELECT").containsEntry("correta", true);
    }

    @Test
    void questoesImportadasFicamEmPortuguesComCamposIncorretosVazios() {
        Map<String, Object> room = quizService.criarQuiz(PROFESSOR_EMAIL, "Quiz importado");
        String code = (String) room.get("codigo");
        int imported = quizService.importarQuestoes(code, PROFESSOR_EMAIL, List.of(
            new QuizService.QuestaoImportada("O que significa CPU?", "Unidade Central de Processamento")
        ));
        List<Map<String, Object>> questions = quizService.listarQuestoesDaSala(code, PROFESSOR_EMAIL);

        assertThat(imported).isEqualTo(1);
        assertThat(questions).singleElement().satisfies(question -> {
            assertThat(question).containsEntry("enunciado", "O que significa CPU?")
                .containsEntry("respostaCorreta", "Unidade Central de Processamento")
                .containsEntry("fonte", "OPENTDB");
            assertThat(question.get("distratores")).asList().containsExactly(null, null, null);
        });
    }

    @Test
    void somenteDonoDaContaPodeAbrirPainelOuEditarQuestao() {
        Map<String, Object> room = quizService.criarQuiz(PROFESSOR_EMAIL, "Privado");
        String code = (String) room.get("codigo");

        assertThatThrownBy(() -> quizService.painelProfessor(code, "outra-conta"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quizService.adicionarQuestao(code, "outra-conta",
            "Pergunta", "Correta", List.of("", "", ""), "MANUAL"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operacoesDeEscritaExigemCsrfELoginParaCriar() throws Exception {
        mockMvc.perform(post("/api/salas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Sem login\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/salas")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Sem login\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validacaoLimitaDistratoresAteDuasLinhas() {
        Map<String, Object> room = quizService.criarQuiz(PROFESSOR_EMAIL, "Validação");

        assertThatThrownBy(() -> quizService.adicionarQuestao((String) room.get("codigo"), PROFESSOR_EMAIL,
            "Pergunta", "Correta", List.of("linha 1\nlinha 2\nlinha 3", "", ""), "MANUAL"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duas linhas");
    }
}