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

/** RNF-14: the database NOW() and the dates read use the application time zone (-03:00). */
class TimezoneIntegrationTest {

    @Test
    void databaseNowIsInApplicationTimezone() throws Exception {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        try (Connection conn = DBConnection.openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT NOW(), @@session.time_zone")) {
            rs.next();
            LocalDateTime database = rs.getObject(1, LocalDateTime.class);
            LocalDateTime expected = LocalDateTime.now(ZoneOffset.ofHours(-3));
            long difference = Math.abs(Duration.between(database, expected).getSeconds());
            assertTrue(difference < 120, "NOW()=" + database + " sessao=" + rs.getString(2) + " esperado~" + expected);
        }
    }
}
