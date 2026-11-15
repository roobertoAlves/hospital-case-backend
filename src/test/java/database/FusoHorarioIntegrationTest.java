package database;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import support.TestDatabase;

/** RNF-14: NOW() do banco e as datas lidas ficam no fuso da aplicacao (-03:00). */
class FusoHorarioIntegrationTest {

    @Test
    void nowDoBancoEstaNoFusoDaAplicacao() throws Exception {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel");
        try (Connection conn = DBConnection.openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT NOW(), @@session.time_zone")) {
            rs.next();
            LocalDateTime banco = rs.getObject(1, LocalDateTime.class);
            LocalDateTime esperado = LocalDateTime.now(ZoneOffset.ofHours(-3));
            long diferenca = Math.abs(Duration.between(banco, esperado).getSeconds());
            assertTrue(diferenca < 120, "NOW()=" + banco + " sessao=" + rs.getString(2) + " esperado~" + esperado);
        }
    }
}
