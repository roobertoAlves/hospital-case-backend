package database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import support.TestDatabase;

class ConnectionPoolIntegrationTest {

    private static Object physical(Connection c) throws SQLException {
        return c.unwrap(Connection.class);
    }

    @Test
    void closedConnectionsAreReused() throws Exception {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        Object first;
        try (Connection c = DBConnection.openConnection()) {
            first = physical(c);
        }
        try (Connection c = DBConnection.openConnection()) {
            assertSame(first, physical(c), "the released connection is handed out again");
            assertTrue(c.getAutoCommit());
        }
    }

    @Test
    void releasingUndoesAnOpenTransaction() throws Exception {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        try (Connection c = DBConnection.openConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TEMPORARY TABLE pool_probe (id INT) ENGINE=InnoDB");
            c.setAutoCommit(false);
            st.execute("INSERT INTO pool_probe VALUES (1)");
        }
        try (Connection c = DBConnection.openConnection(); Statement st = c.createStatement()) {
            assertTrue(c.getAutoCommit(), "the next borrower gets autocommit back");
            try (var rs = st.executeQuery("SELECT COUNT(*) FROM pool_probe")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "the uncommitted insert was rolled back");
            }
            st.execute("DROP TEMPORARY TABLE pool_probe");
        }
    }

    @Test
    void aClosedHandleCannotBeUsed() throws Exception {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        Connection c = DBConnection.openConnection();
        c.close();
        assertTrue(c.isClosed());
        assertThrows(SQLException.class, c::createStatement);
        c.close();
        try (Connection next = DBConnection.openConnection()) {
            assertFalse(next.isClosed());
        }
    }
}
