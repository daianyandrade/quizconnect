# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/maven-plugin/build-image.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.1.1/reference/using/devtools.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

## QuizConnect

### Como executar

Na pasta do projeto, execute:

```powershell
.\mvnw.cmd spring-boot:run
```

Depois abra `http://localhost:8080`.

Para executar os testes:

```powershell
.\mvnw.cmd test
```

### Fluxo atual

1. Escolha uma disciplina ou todas as disciplinas.
2. Crie uma sala e copie o codigo.
3. Outros jogadores entram usando o codigo.
4. O lobby mostra a disciplina e os jogadores.
5. Inicie o quiz e responda as perguntas.
6. Ao final, veja a pontuacao local.

### Principais rotas

| Metodo | Rota | Funcao |
| --- | --- | --- |
| POST | `/api/salas` | Cria uma sala |
| POST | `/api/salas/entrar` | Entra em uma sala |
| GET | `/api/salas/{codigo}` | Consulta o lobby |
| GET | `/api/perguntas` | Lista perguntas |
| POST | `/api/perguntas/{id}/responder` | Verifica uma resposta |

### Arquivos principais

- `SalaController.java`: criacao, entrada e consulta das salas.
- `QuizController.java`: perguntas e correcao das respostas.
- `static/index.html`: interface, lobby, quiz, pontuacao e sons.
- `QuizconnectApplicationTests.java`: testes do backend.

### Banco de dados

Salas e perguntas ainda ficam em memoria. Ao reiniciar o servidor, os dados sao
apagados. O banco SQL sera conectado depois, com tabelas para disciplinas,
perguntas, alternativas, jogadores e pontuacoes.

