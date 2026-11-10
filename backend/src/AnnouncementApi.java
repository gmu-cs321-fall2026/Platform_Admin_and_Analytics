import com.sun.net.httpserver.HttpExchange;
import database.Database;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;

final class AnnouncementApi {
    private static final String PATH = "/api/admin/announcements";
    private static final String RECORD_FIELDS =
            "id, sender_id, title, message, audience, dispatch_status, created_at, sent_at, broadcast_reference";

    private AnnouncementApi() {}

    static void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().equals(PATH)) {
            AdminHttp.sendError(exchange, 404, "Not found.");
            return;
        }
        try {
            switch (exchange.getRequestMethod()) {
                case "GET" -> AdminHttp.sendJson(exchange, 200, list());
                case "PUT" -> save(exchange);
                default -> {
                    exchange.getResponseHeaders().set("Allow", "GET, PUT");
                    AdminHttp.sendError(exchange, 405, "Method not allowed.");
                }
            }
        } catch (IllegalArgumentException exception) {
            AdminHttp.sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            AdminHttp.sendError(exchange, 500, "Could not access announcement records. Please try again.");
        }
    }

    private static void save(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> form = AdminHttp.readForm(exchange, 65_536);
        UUID id = UUID.fromString(AdminHttp.required(form, "id", 36));
        String title = AdminHttp.required(form, "title", 200);
        String message = AdminHttp.required(form, "message", 5_000);
        String audience = audienceJson(form.getOrDefault("studentIds", ""));
        String sql = """
                INSERT INTO admin_announcements (id, sender_id, title, message, audience)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (id) DO UPDATE SET
                    title = EXCLUDED.title, message = EXCLUDED.message, audience = EXCLUDED.audience
                WHERE admin_announcements.dispatch_status = 'DRAFT'
                    AND admin_announcements.sender_id = EXCLUDED.sender_id
                RETURNING
                """ + " " + RECORD_FIELDS;

        String record = null;
        try (var connection = Database.open(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setString(2, AuditLog.demoAdminId());
            statement.setString(3, title);
            statement.setString(4, message);
            statement.setString(5, audience);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    record = toJson(rows);
                }
            }
        }
        if (record == null) {
            AdminHttp.sendError(exchange, 409, "This announcement is no longer an editable draft.");
            return;
        }
        AdminHttp.sendJson(exchange, 200, record);
    }

    private static String list() throws SQLException {
        StringJoiner records = new StringJoiner(",", "{\"announcements\":[", "]}");
        String sql = "SELECT " + RECORD_FIELDS
                + " FROM admin_announcements ORDER BY created_at DESC, id DESC";
        try (var connection = Database.open(); var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                records.add(toJson(rows));
            }
        }
        return records.toString();
    }

    private static String audienceJson(String studentIds) {
        if (studentIds.isBlank()) {
            return "{\"kind\":\"all_students\"}";
        }
        var ids = Arrays.stream(studentIds.split(",", -1)).distinct().toList();
        if (!UserApi.areStudentIds(ids)) {
            throw new IllegalArgumentException("Choose valid student accounts for the audience.");
        }
        return ids.stream().map(AuditLog::quote).collect(Collectors.joining(
                ",", "{\"kind\":\"selected_students\",\"studentIds\":[", "]}"));
    }

    private static String toJson(ResultSet row) throws SQLException {
        OffsetDateTime sentAt = row.getObject("sent_at", OffsetDateTime.class);
        return "{\"id\":" + AuditLog.quote(row.getString("id"))
                + ",\"senderId\":" + AuditLog.quote(row.getString("sender_id"))
                + ",\"title\":" + AuditLog.quote(row.getString("title"))
                + ",\"message\":" + AuditLog.quote(row.getString("message"))
                + ",\"audience\":" + row.getString("audience")
                + ",\"dispatchStatus\":" + AuditLog.quote(row.getString("dispatch_status"))
                + ",\"createdAt\":" + AuditLog.quote(row.getObject("created_at", OffsetDateTime.class).toInstant().toString())
                + ",\"sentAt\":" + AuditLog.quote(sentAt == null ? null : sentAt.toInstant().toString())
                + ",\"broadcastReference\":" + AuditLog.quote(row.getString("broadcast_reference")) + "}";
    }
}
