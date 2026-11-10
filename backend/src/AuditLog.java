import database.Database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

public final class AuditLog {
    private static final Path FILE =
            Path.of("data", "admin-audit.tsv");

    public static final Set<String> ACTIONS = Set.of(
            "ROLE_CHANGED",
            "ACCOUNT_SUSPENDED",
            "ACCOUNT_UNSUSPENDED",
            "POST_REMOVED",
            "USER_WARNED",
            "FLAG_DISMISSED",
            "RESOURCE_APPROVED",
            "RESOURCE_REJECTED",
            "QUESTION_APPROVED",
            "QUESTION_REJECTED"
    );

    private static final String INSERT = """
            INSERT INTO admin_audit_entries
                (id, occurred_at, action, target_type, target_id, target_name,
                 admin_id, reason, previous_value, new_value)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private AuditLog() {}

    public record Entry(
            String id,
            Instant timestamp,
            String action,
            String targetType,
            String targetId,
            String targetName,
            String adminId,
            String reason,
            String previousValue,
            String newValue
    ) {
    }

    // Temporary identity for the standalone demo.
    // Replace this with the signed-in admin's ID during integration.
    public static String demoAdminId() {
        String configured =
                System.getenv("DEMO_ADMIN_ID");

        return configured == null || configured.isBlank()
                ? "demo-admin"
                : configured.trim();
    }

    public static List<Entry> readAll() throws IOException {
        String sql = """
                SELECT id, occurred_at, action, target_type, target_id, target_name,
                       admin_id, reason, previous_value, new_value
                FROM admin_audit_entries ORDER BY occurred_at, id
                """;
        List<Entry> entries = new ArrayList<>();
        try (var connection = Database.open(); var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                entries.add(new Entry(
                        rows.getString("id"),
                        rows.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rows.getString("action"),
                        rows.getString("target_type"),
                        rows.getString("target_id"),
                        rows.getString("target_name"),
                        rows.getString("admin_id"),
                        rows.getString("reason"),
                        rows.getString("previous_value"),
                        rows.getString("new_value")
                ));
            }
        } catch (SQLException exception) {
            throw new IOException("Could not read PostgreSQL audit entries", exception);
        }
        return entries;
    }

    static void importLegacy() throws IOException {
        importLegacy(FILE);
    }

    static void importLegacy(Path file) throws IOException {
        List<Entry> entries = readLegacy(file);
        if (entries.isEmpty()) {
            return;
        }
        try (var connection = Database.open()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(INSERT + " ON CONFLICT (id) DO NOTHING")) {
                for (Entry entry : entries) {
                    bind(statement, entry);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IOException("Could not import the saved audit history", exception);
        }
    }

    private static List<Entry> readLegacy(Path file) throws IOException {
        List<Entry> entries = new ArrayList<>();

        if (!Files.exists(file)) {
            return entries;
        }

        for (String line :
                Files.readAllLines(file, StandardCharsets.UTF_8)) {

            if (line.isBlank()) {
                continue;
            }

            try {
                String[] fields = line.split("\t", -1);

                if (fields.length != 10) {
                    throw new IllegalArgumentException(
                            "Invalid audit entry"
                    );
                }

                for (int i = 0; i < fields.length; i++) {
                    fields[i] = decode(fields[i]);
                }

                if (!ACTIONS.contains(fields[2])) {
                    throw new IllegalArgumentException(
                            "Invalid audit action"
                    );
                }

                entries.add(new Entry(
                        UUID.fromString(fields[0]).toString(),
                        Instant.parse(fields[1]),
                        fields[2],
                        fields[3],
                        fields[4],
                        fields[5],
                        fields[6],
                        fields[7],
                        fields[8],
                        fields[9]
                ));
            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "Could not read the audit log",
                        exception
                );
            }
        }

        return entries;
    }

    static Entry userChange(User user, String value, boolean changingStatus, String reason) {
        String previousValue = changingStatus ? user.getStatus() : user.getRole();
        String action = changingStatus
                ? (value.equals("SUSPENDED") ? "ACCOUNT_SUSPENDED" : "ACCOUNT_UNSUSPENDED")
                : "ROLE_CHANGED";
        return newEntry(action, "USER", String.valueOf(user.getId()), user.getName(),
                reason, previousValue, value);
    }

    static Entry newEntry(String action, String targetType, String targetId, String targetName,
                          String reason, String previousValue, String newValue) {
        return new Entry(UUID.randomUUID().toString(), Instant.now(), action, targetType, targetId,
                targetName, demoAdminId(), reason, previousValue, newValue);
    }

    static void recordUserChange(User user, String value, boolean changingStatus) throws IOException {
        try (var connection = Database.open()) {
            insert(connection, userChange(user, value, changingStatus, ""));
        } catch (SQLException exception) {
            throw new IOException("Could not save the audit entry", exception);
        }
    }

    // Replay account changes after loading any older role and status files.
    public static synchronized void restoreUsers(
            List<User> users
    ) throws IOException {

        Map<String, User> byId = new HashMap<>();

        for (User user : users) {
            byId.put(String.valueOf(user.getId()), user);
        }

        for (Entry entry : readAll()) {
            if (!entry.targetType().equals("USER")) {
                continue;
            }

            User user = byId.get(entry.targetId());

            if (user == null) {
                continue;
            }

            try {
                switch (entry.action()) {
                    case "ROLE_CHANGED" ->
                            user.setRole(entry.newValue());

                    case "ACCOUNT_SUSPENDED",
                         "ACCOUNT_UNSUSPENDED" ->
                            user.setStatus(entry.newValue());

                    default -> {
                        // Other actions do not change account fields.
                    }
                }
            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "Invalid saved account change",
                        exception
                );
            }
        }
    }

    static void insert(Connection connection, Entry entry) throws SQLException {
        try (var statement = connection.prepareStatement(INSERT)) {
            bind(statement, entry);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Entry entry) throws SQLException {
        statement.setObject(1, UUID.fromString(entry.id()));
        statement.setObject(2, entry.timestamp().atOffset(ZoneOffset.UTC));
        statement.setString(3, entry.action());
        statement.setString(4, entry.targetType());
        statement.setString(5, entry.targetId());
        statement.setString(6, entry.targetName());
        statement.setString(7, entry.adminId());
        statement.setString(8, entry.reason());
        statement.setString(9, entry.previousValue());
        statement.setString(10, entry.newValue());
    }

    private static String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8
        );
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder json = new StringBuilder("\"");

        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 32) {
                        json.append(String.format(
                                "\\u%04x",
                                (int) character
                        ));
                    } else {
                        json.append(character);
                    }
                }
            }
        }

        return json.append('"').toString();
    }

    public static String toJson(Entry entry) {
        return "{"
                + "\"id\":" + quote(entry.id()) + ","
                + "\"timestamp\":"
                + quote(entry.timestamp().toString()) + ","
                + "\"action\":" + quote(entry.action()) + ","
                + "\"targetType\":" + quote(entry.targetType()) + ","
                + "\"targetId\":" + quote(entry.targetId()) + ","
                + "\"targetName\":" + quote(entry.targetName()) + ","
                + "\"adminId\":" + quote(entry.adminId()) + ","
                + "\"reason\":" + quote(entry.reason()) + ","
                + "\"previousValue\":"
                + quote(entry.previousValue()) + ","
                + "\"newValue\":" + quote(entry.newValue())
                + "}";
    }
}
