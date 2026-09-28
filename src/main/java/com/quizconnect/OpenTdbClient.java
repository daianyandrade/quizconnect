package com.quizconnect;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

@Component
public class OpenTdbClient {

    private final RestClient client;
    private long ultimaSolicitacaoMillis;

    public OpenTdbClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder()
            .baseUrl("https://opentdb.com")
            .requestFactory(requestFactory)
            .build();
    }

    public List<Questao> buscarQuestoes(int quantidade) {
        return buscarQuestoes(quantidade, "multiple", "");
    }

    public synchronized List<Questao> buscarQuestoes(int quantidade, String tipo, String dificuldade) {
        if (quantidade < 1 || quantidade > 25) {
            throw new IllegalArgumentException("A importação deve ter entre 1 e 25 questões.");
        }
        if (!List.of("multiple", "boolean").contains(tipo)) {
            throw new IllegalArgumentException("Escolha múltipla ou verdadeiro/falso.");
        }
        if (dificuldade == null || dificuldade.isBlank()) {
            dificuldade = "";
        } else if (!List.of("easy", "medium", "hard").contains(dificuldade)) {
            throw new IllegalArgumentException("Dificuldade inválida.");
        }
        String nivel = dificuldade;
        long agora = System.currentTimeMillis();
        if (agora - ultimaSolicitacaoMillis < 5_000) {
            throw new IllegalStateException("Aguarde alguns segundos antes de importar novamente.");
        }
        ultimaSolicitacaoMillis = agora;
        try {
            JsonNode resposta = client.get()
                .uri(uri -> uri.path("/api.php")
                    .queryParam("amount", quantidade)
                    .queryParam("category", 18)
                    .queryParam("type", tipo)
                    .queryParamIfPresent("difficulty", nivel.isEmpty()
                        ? java.util.Optional.empty() : java.util.Optional.of(nivel))
                    .build())
                .retrieve()
                .body(JsonNode.class);
            if (resposta == null || resposta.path("response_code").asInt(-1) != 0) {
                throw new IllegalStateException("OpenTDB não encontrou questões. Tente novamente mais tarde.");
            }
            List<Questao> questoes = new ArrayList<>();
            for (JsonNode item : resposta.path("results")) {
                String correta = limparHtml(item.path("correct_answer").asText());
                List<String> alternativas = new ArrayList<>();
                alternativas.add(correta);
                for (JsonNode alternativa : item.path("incorrect_answers")) {
                    alternativas.add(limparHtml(alternativa.asText()));
                }
                questoes.add(new Questao(limparHtml(item.path("question").asText()), alternativas, correta));
            }
            if (questoes.isEmpty()) {
                throw new IllegalStateException("OpenTDB retornou uma lista vazia.");
            }
            return questoes;
        } catch (RestClientException erro) {
            throw new IllegalStateException("Não foi possível conectar ao OpenTDB. Verifique a conexão e tente novamente.", erro);
        }
    }

    private String limparHtml(String texto) {
        return HtmlUtils.htmlUnescape(texto).trim();
    }

    public record Questao(String enunciado, List<String> alternativas, String respostaCorreta) {
    }
}