package com.quizconnect;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DistractorAiClient {

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private long ultimaSolicitacaoMillis;

    public DistractorAiClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        client = RestClient.builder()
            .baseUrl("https://text.pollinations.ai")
            .requestFactory(factory)
            .build();
    }

    public synchronized List<String> sugerir(String pergunta, String correta) {
        if (pergunta == null || pergunta.isBlank() || correta == null || correta.isBlank()) {
            throw new IllegalArgumentException("Informe a pergunta e a resposta correta.");
        }
        long agora = System.currentTimeMillis();
        if (agora - ultimaSolicitacaoMillis < 5_000) {
            throw new IllegalStateException("Aguarde alguns segundos antes de pedir novas sugestões.");
        }
        ultimaSolicitacaoMillis = agora;
        String prompt = "Em português do Brasil, gere exatamente três alternativas incorretas, curtas e plausíveis "
            + "para uma questão de Tecnologia. Cada alternativa deve ter no máximo 100 caracteres, uma linha, "
            + "ser diferente da resposta correta e entre si. Retorne exclusivamente um array JSON com três strings. "
            + "Não explique nem inclua a resposta correta. Pergunta: " + pergunta + " Resposta correta: " + correta;
        try {
            JsonNode response = client.post()
                .uri(uri -> uri.path("/openai").queryParam("model", "openai-fast").build())
                .body(Map.of(
                    "model", "openai-fast",
                    "temperature", 0.2,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
                ))
                .retrieve()
                .body(JsonNode.class);
            String content = response == null ? "" : response.path("choices").path(0)
                .path("message").path("content").asText();
            JsonNode suggestions = mapper.readTree(content);
            if (!suggestions.isArray() || suggestions.size() != 3) {
                throw new IllegalStateException("A IA não retornou três sugestões válidas. Tente novamente.");
            }
            List<String> alternatives = new java.util.ArrayList<>();
            for (JsonNode suggestion : suggestions) {
                String text = suggestion.asText().trim();
                long lines = text.lines().count();
                if (text.isBlank() || text.length() > 100 || lines > 2
                    || text.equalsIgnoreCase(correta) || alternatives.stream().anyMatch(text::equalsIgnoreCase)) {
                    throw new IllegalStateException("A IA retornou sugestões repetidas ou longas. Tente novamente.");
                }
                alternatives.add(text);
            }
            return alternatives;
        } catch (RestClientException erro) {
            throw new IllegalStateException("A IA gratuita está indisponível no momento; preencha as respostas manualmente.", erro);
        } catch (tools.jackson.core.JacksonException erro) {
            throw new IllegalStateException("A IA não retornou sugestões em formato válido. Tente novamente.", erro);
        }
    }
}