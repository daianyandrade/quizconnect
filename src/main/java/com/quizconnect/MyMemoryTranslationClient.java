package com.quizconnect;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

@Component
public class MyMemoryTranslationClient {

    private final RestClient client;

    public MyMemoryTranslationClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        client = RestClient.builder()
            .baseUrl("https://api.mymemory.translated.net")
            .requestFactory(factory)
            .build();
    }

    public String traduzir(String texto) {
        if (texto == null || texto.isBlank() || texto.length() > 450) {
            throw new IllegalArgumentException("A pergunta ou resposta excede o limite de tradução.");
        }
        try {
            JsonNode resultado = client.get()
                .uri(uri -> uri.path("/get")
                    .queryParam("q", texto)
                    .queryParam("langpair", "en|pt-BR")
                    .build())
                .retrieve()
                .body(JsonNode.class);
            String traduzido = resultado == null ? "" : HtmlUtils.htmlUnescape(
                resultado.path("responseData").path("translatedText").asText()).trim();
            if (resultado == null || resultado.path("responseStatus").asInt() != 200 || traduzido.isBlank()) {
                throw new IllegalStateException("O serviço de tradução não retornou português. Tente novamente.");
            }
            return traduzido;
        } catch (RestClientException erro) {
            throw new IllegalStateException("Não foi possível traduzir a pergunta. Tente novamente.", erro);
        }
    }
}