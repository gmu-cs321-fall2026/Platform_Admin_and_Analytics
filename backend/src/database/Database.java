package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class Database {
    private Database() {
    }

    // Also used by the connection smoke test.
    public static void main(String[] args) {
        verifyConnection();
    }

    public static Connection open() throws SQLException {
        String url = requiredEnv("DB_URL");
        if (!url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("DB_URL must be a PostgreSQL JDBC URL.");
        }

        Properties options = new Properties();
        options.setProperty("user", requiredEnv("DB_USER"));
        options.setProperty("password", requiredEnv("DB_PASSWORD"));
        options.setProperty("connectTimeout", "5");
        options.setProperty("socketTimeout", "5");
        return DriverManager.getConnection(url, options);
    }

    public static void verifyConnection() {
        try (var connection = open();
             var statement = connection.prepareStatement("SELECT 1");
             var result = statement.executeQuery()) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new SQLException("Database probe returned an unexpected value.");
            }
            System.out.println("PostgreSQL connection verified.");
        } catch (SQLException error) {
            // Keep credentials and the connection URL out of logs.
            throw new IllegalStateException(
                    "PostgreSQL startup check failed (SQL state "
                            + error.getSQLState() + "). Check DB_URL, DB_USER, DB_PASSWORD, "
                            + "and whether PostgreSQL is reachable."
            );
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }
}
