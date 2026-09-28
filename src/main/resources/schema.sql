CREATE TABLE IF NOT EXISTS usuarios (
    email TEXT PRIMARY KEY,
    usuario TEXT NOT NULL UNIQUE,
    nome TEXT NOT NULL,
    password_hash TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS salas (
    codigo TEXT PRIMARY KEY,
    titulo TEXT NOT NULL,
    avaliador_email TEXT NOT NULL REFERENCES usuarios(email),
    pausada INTEGER NOT NULL DEFAULT 0 CHECK (pausada IN (0, 1)),
    finalizada INTEGER NOT NULL DEFAULT 0 CHECK (finalizada IN (0, 1)),
    criada_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS perguntas (
    id INTEGER PRIMARY KEY,
    codigo_sala TEXT NOT NULL REFERENCES salas(codigo) ON DELETE CASCADE,
    enunciado TEXT NOT NULL,
    resposta_correta TEXT NOT NULL,
    fonte TEXT NOT NULL DEFAULT 'MANUAL',
    ordem INTEGER NOT NULL,
    UNIQUE (codigo_sala, ordem)
);

CREATE TABLE IF NOT EXISTS distratores (
    pergunta_id INTEGER NOT NULL REFERENCES perguntas(id) ON DELETE CASCADE,
    posicao INTEGER NOT NULL CHECK (posicao BETWEEN 1 AND 3),
    texto TEXT,
    PRIMARY KEY (pergunta_id, posicao)
);

CREATE TABLE IF NOT EXISTS participantes (
    id TEXT PRIMARY KEY,
    codigo_sala TEXT NOT NULL REFERENCES salas(codigo) ON DELETE CASCADE,
    nome TEXT NOT NULL,
    criado_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tentativas (
    id TEXT PRIMARY KEY,
    participante_id TEXT NOT NULL UNIQUE REFERENCES participantes(id) ON DELETE CASCADE,
    iniciada_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tentativa_perguntas (
    tentativa_id TEXT NOT NULL REFERENCES tentativas(id) ON DELETE CASCADE,
    pergunta_id INTEGER NOT NULL REFERENCES perguntas(id),
    ordem INTEGER NOT NULL,
    PRIMARY KEY (tentativa_id, pergunta_id),
    UNIQUE (tentativa_id, ordem)
);

CREATE TABLE IF NOT EXISTS respostas (
    tentativa_id TEXT NOT NULL,
    pergunta_id INTEGER NOT NULL,
    resposta TEXT NOT NULL,
    correta INTEGER NOT NULL CHECK (correta IN (0, 1)),
    respondida_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tentativa_id, pergunta_id),
    FOREIGN KEY (tentativa_id, pergunta_id)
        REFERENCES tentativa_perguntas(tentativa_id, pergunta_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_participantes_sala ON participantes(codigo_sala);
CREATE INDEX IF NOT EXISTS idx_perguntas_sala ON perguntas(codigo_sala, ordem);