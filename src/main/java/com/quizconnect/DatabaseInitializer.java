package com.quizconnect;

import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements InitializingBean {

    private static final List<String> LEGACY_TABLES = List.of(
        "respostas", "tentativa_perguntas", "tentativas", "sala_perguntas",
        "alternativas", "participantes", "perguntas", "salas"
    );

    private final JdbcTemplate jdbc;

    public DatabaseInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterPropertiesSet() {
        if (temModeloDeDisciplinas()) {
            jdbc.execute("PRAGMA foreign_keys=OFF");
            LEGACY_TABLES.forEach(table -> jdbc.execute("DROP TABLE IF EXISTS " + table));
            jdbc.execute("PRAGMA foreign_keys=ON");
        }
        migrarPerfilGoogle();
        adicionarColunaSenha();
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(jdbc.getDataSource());
    }

    private void adicionarColunaSenha() {
        List<String> columns = jdbc.query("PRAGMA table_info(usuarios)",
            (rs, row) -> rs.getString("name"));
        if (!columns.isEmpty() && !columns.contains("usuario")) {
            jdbc.execute("ALTER TABLE usuarios ADD COLUMN usuario TEXT");
            jdbc.update("UPDATE usuarios SET usuario = 'professor' || rowid WHERE usuario IS NULL OR usuario = ''");
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_usuarios_usuario ON usuarios(usuario)");
        }
        if (!columns.isEmpty() && !columns.contains("password_hash")) {
            jdbc.execute("ALTER TABLE usuarios ADD COLUMN password_hash TEXT");
        }
        List<String> updatedColumns = jdbc.query("PRAGMA table_info(usuarios)",
            (rs, row) -> rs.getString("name"));
        if (updatedColumns.contains("usuario")) {
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_usuarios_usuario ON usuarios(usuario)");
        }
    }

    private void migrarPerfilGoogle() {
        List<String> columns = jdbc.query("PRAGMA table_info(usuarios)",
            (rs, row) -> rs.getString("name"));
        if (!columns.contains("google_sub")) {
            return;
        }
        jdbc.execute("PRAGMA foreign_keys=OFF");
        jdbc.execute("ALTER TABLE usuarios RENAME TO usuarios_legado");
        jdbc.execute("CREATE TABLE usuarios (email TEXT PRIMARY KEY, nome TEXT NOT NULL)");
        jdbc.update("INSERT OR IGNORE INTO usuarios (email, nome) "
            + "SELECT email, nome FROM usuarios_legado WHERE email IS NOT NULL AND nome IS NOT NULL");
        jdbc.execute("DROP TABLE usuarios_legado");
        jdbc.execute("PRAGMA foreign_keys=ON");
    }

    private boolean temModeloDeDisciplinas() {
        List<String> tabelas = jdbc.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'salas'",
            (rs, row) -> rs.getString("name"));
        if (tabelas.isEmpty()) {
            return false;
        }
        return jdbc.query("PRAGMA table_info(salas)",
            (rs, row) -> "disciplina".equals(rs.getString("name"))).contains(true);
    }
}