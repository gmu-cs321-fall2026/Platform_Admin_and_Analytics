import com.sun.net.httpserver.HttpExchange;
import database.Database;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringJoiner;
import java.util.UUID;

/** Saved wording retrieved by the Notifications and Communications subsystem. */
final class NotificationTemplateApi {
    private static final String PATH = "/api/config/notification-templates";

    private NotificationTemplateApi() {}

    static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        boolean collection = path.equals(PATH);
        if (!collection && !path.matches(PATH + "/[0-9a-fA-F-]{36}")) {
            AdminHttp.sendError(exchange, 404, "Notification template not found.");
            return;
        }
        String method = exchange.getRequestMethod();
        if (!(collection ? method.equals("GET") : method.equals("GET") || method.equals("PUT"))) {
            exchange.getResponseHeaders().set("Allow", collection ? "GET" : "GET, PUT");
            AdminHttp.sendError(exchange, 405, "Method not allowed.");
            return;
        }
        try {
            if (collection) {
                AdminHttp.sendJson(exchange, 200, list());
                return;
            }
            UUID id = UUID.fromString(path.substring(PATH.length() + 1));
            String template = method.equals("PUT") ? update(exchange, id) : find(id);
            if (template == null) {
                AdminHttp.sendError(exchange, 404, "Notification template not found.");
            } else {
                AdminHttp.sendJson(exchange, 200, template);
            }
        } catch (IllegalArgumentException exception) {
            AdminHttp.sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            AdminHttp.sendError(exchange, 500, "Could not load or save notification templates. Please try again.");
        }
    }

    private static String list() throws SQLException {
        StringJoiner templates = new StringJoiner(",", "{\"templates\":[", "]}");
        try (var connection = Database.open(); var statement = connection.prepareStatement(
                "SELECT id, name, message FROM admin_notification_templates ORDER BY name, id");
             var rows = statement.executeQuery()) {
            while (rows.next()) templates.add(toJson(rows));
        }
        return templates.toString();
    }

    private static String find(UUID id) throws SQLException {
        try (var connection = Database.open(); var statement = connection.prepareStatement(
                "SELECT id, name, message FROM admin_notification_templates WHERE id = ?")) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? toJson(rows) : null;
            }
        }
    }

    private static String update(HttpExchange exchange, UUID id) throws IOException, SQLException {
        var fields = AdminHttp.readForm(exchange, 65_536);
        String message = fields.getOrDefault("message", "");
        if (message.isBlank() || message.length() > 5_000) {
            throw new IllegalArgumentException("Template messages cannot be blank and must be 5000 characters or fewer.");
        }
        // Keep line breaks and spacing exactly as previewed; update existing records only.
        try (var connection = Database.open(); var statement = connection.prepareStatement("""
                UPDATE admin_notification_templates SET message = ? WHERE id = ?
                RETURNING id, name, message
                """)) {
            statement.setString(1, message);
            statement.setObject(2, id);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? toJson(rows) : null;
            }
        }
    }

    private static String toJson(ResultSet row) throws SQLException {
        return "{\"id\":" + AuditLog.quote(row.getString("id"))
                + ",\"name\":" + AuditLog.quote(row.getString("name"))
                + ",\"message\":" + AuditLog.quote(row.getString("message")) + "}";
    }
}
