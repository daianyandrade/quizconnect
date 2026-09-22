package com.quizconnect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class QuizconnectApplicationTests {

	@Test
	void deveListarPerguntasDeAds() {
		QuizController controller = new QuizController();

		var perguntas = controller.listarPerguntas();

		assertThat(perguntas).hasSize(5);
		assertThat(perguntas).extracting(pergunta -> pergunta.get("disciplina"))
				.containsExactlyInAnyOrder(
					"Programação",
					"Banco de Dados",
					"Engenharia de Software",
					"Redes de Computadores",
					"Sistemas Operacionais");
		assertThat(perguntas.get(0)).doesNotContainKey("respostaCorreta");
	}

	@Test
	void deveInformarSeRespostaEstaCorreta() {
		QuizController controller = new QuizController();

		ResponseEntity<Map<String, Object>> resposta = controller.responder(1, Map.of("resposta", "while"));

		assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(resposta.getBody()).containsEntry("correta", true);
	}

	@Test
	void deveCriarSalaComCodigoValido() {
		SalaController controller = new SalaController();

		Map<String, String> sala = controller.criarSala();

		assertThat(sala.get("codigo")).isNotBlank();
		assertThat(sala.get("codigo")).matches("[A-Z0-9]{6}");
		assertThat(sala.get("mensagem")).isEqualTo("Sala criada com sucesso!");
	}

	@Test
	void deveEntrarNaSalaComCodigoValido() {
		SalaController controller = new SalaController();
		String codigo = controller.criarSala().get("codigo");

		ResponseEntity<Map<String, Object>> resposta = controller.entrarSala(Map.of("codigo", codigo, "nome", "Maria"));

		assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(resposta.getBody()).isNotNull();
		assertThat(resposta.getBody().get("mensagem")).isEqualTo("Você entrou na sala!");
		assertThat(resposta.getBody().get("codigo")).isEqualTo(codigo);
		assertThat(resposta.getBody().get("jogadores")).isNotNull();
	}

	@Test
	void deveConsultarJogadoresDaSala() {
		SalaController controller = new SalaController();
		String codigo = controller.criarSala().get("codigo");
		controller.entrarSala(Map.of("codigo", codigo, "nome", "Maria"));

		ResponseEntity<Map<String, Object>> resposta = controller.consultarSala(codigo.toLowerCase());

		assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(resposta.getBody()).isNotNull();
		assertThat(resposta.getBody().get("codigo")).isEqualTo(codigo);
		assertThat(resposta.getBody().get("jogadores")).asList().containsExactly("Maria");
	}

}
