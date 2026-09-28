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

Na pasta do projeto, use Java 26, como configurado no `pom.xml`:

```bash
./mvnw spring-boot:run
```

Neste workspace, o container tem Java 25 e o wrapper não possui permissão de execução. Para rodar aqui:

```bash
bash mvnw -Djava.version=25 spring-boot:run
```

Abra `http://localhost:8080`. O arquivo `quizconnect.db` é criado no diretório de execução.

### Conta do professor

Crie uma conta local com usuário (3 a 32 caracteres: letras, números, ponto, hífen ou sublinhado), nome completo, e-mail e uma senha própria do QuizConnect. A senha deve ter pelo menos 12 caracteres e é guardada com BCrypt. Depois, entre usando o nome de usuário ou o e-mail e essa senha. Nunca reutilize nem informe a senha do Gmail.

Os alunos continuam entrando sem conta, usando o código da atividade e o nome.

Para executar os testes:

```bash
bash mvnw -Djava.version=25 test
```

### Fluxo do quiz

1. O professor cria uma conta ou entra com usuário/e-mail e senha, e então cria uma atividade sem disciplinas.
2. Importa questões do OpenTDB ou cria cada pergunta manualmente.
3. A resposta correta ocupa o campo verde; os três distratores podem ser escritos ou sugeridos pela IA.
4. O professor compartilha o código. Cada aluno entra somente com o nome.
5. O painel atualiza a grade de respostas, pontuação e totais; permite pausar ou encerrar a atividade.

O OpenTDB só é consultado após o professor acionar a importação, com até cinco questões por atividade. A pergunta e a resposta correta são traduzidas sob demanda pelo MyMemory; as alternativas inglesas incorretas são descartadas. Os três campos de distrator ficam vazios. A IA gratuita Pollinations só é consultada quando o professor clica em **Sugerir distratores**; ela recebe a pergunta e a resposta correta e retorna três sugestões de até duas linhas. Revise as sugestões antes de salvar. Os serviços externos são gratuitos, sem chave neste protótipo, e podem impor limites ou ficar indisponíveis; é possível preencher tudo manualmente.

As perguntas e respostas enviadas à tradução ou à IA são processadas por serviços externos. Não inclua conteúdo confidencial. A aplicação limita cada lote e espaça pedidos à IA.

### Principais rotas

| Metodo | Rota | Funcao |
| --- | --- | --- |
| GET | `/api/auth/me` | Retorna estado da sessão do professor |
| GET | `/api/auth/csrf` | Inicializa token CSRF da sessão |
| POST | `/api/auth/register` | Cria conta de professor (`usuario`, `nome`, `email`, `senha`) |
| POST | `/api/auth/login` | Inicia sessão (`login`, `senha`), aceitando usuário ou e-mail |
| POST | `/api/auth/logout` | Encerra a sessão do professor |
| POST | `/api/salas` | Cria atividade para o professor autenticado (`titulo`) |
| POST | `/api/salas/entrar` | Inscreve aluno sem conta (`codigo`, `nome`) |
| GET | `/api/salas/{codigo}/professor` | Carrega grade privada do professor autenticado |
| PATCH | `/api/salas/{codigo}/status` | Pausa ou encerra atividade |
| GET | `/api/salas/{codigo}/questoes` | Lista questões para o professor proprietário |
| POST/PUT | `/api/salas/{codigo}/questoes` | Cria ou altera pergunta, gabarito e três distratores |
| DELETE | `/api/salas/{codigo}/questoes/{id}` | Remove uma questão antes do primeiro início |
| POST | `/api/salas/{codigo}/importar-opentdb` | Busca e traduz questões sob demanda |
| POST | `/api/salas/{codigo}/sugestoes-distratores` | Gera três alternativas incorretas sob demanda |
| GET | `/api/salas/{codigo}/participantes/{id}/quiz` | Inicia ou retoma quiz do aluno |
| POST | `/api/salas/{codigo}/participantes/{id}/respostas` | Registra e corrige resposta (`perguntaId`, `resposta`) |
| GET | `/api/salas/{codigo}/ranking` | Consulta ranking da atividade |

### Arquivos principais

- `SalaController.java`: rotas de salas e ranking.
- `QuizController.java`: rotas de perguntas, tentativas e respostas.
- `QuizService.java`: regras, validação, correção e persistência via JDBC.
- `static/index.html`: interface, lobby, quiz, sessão de participante e ranking.
- `DatabaseInitializer.java`: cria o schema e migra a base anterior.
- `schema.sql`: tabelas de avaliadores, atividades, questões, alunos e respostas.
- `QuizconnectApplicationTests.java`: testes de integração com SQLite em memória.

### Banco de dados

O SQLite persiste usuário, nome completo, e-mail e hash BCrypt do professor, além dos dados necessários para quizzes, questões, alunos e respostas. Ao iniciar com a base antiga, a migração apaga salas, perguntas, alternativas, participantes e tentativas e remove os campos do perfil Google. Não há perguntas seed: cada atividade começa vazia. Os convites usam oito caracteres aleatórios. O banco local e arquivos WAL são ignorados pelo Git.

As edições são bloqueadas após o primeiro início. Rotas de escrita usam token CSRF; edição, importação, status e painel validam a sessão local e a propriedade da atividade no servidor. Apenas professores com conta podem criar quizzes.

O link fixo **Inspired By Socrative** aponta para o site do Socrative. A tela é inspirada na grade de acompanhamento de sala, sem reproduzir os serviços ou a marca do produto.

