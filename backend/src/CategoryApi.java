import com.sun.net.httpserver.HttpExchange;
import database.Database;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringJoiner;
import java.util.UUID;

/** Configuration API for existing event categories. */
final class CategoryApi {
    private static final String PATH = "/api/config/event-categories";

    private CategoryApi() {}

    static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        boolean collection = path.equals(PATH);
        if (!collection && !path.matches(PATH + "/[0-9a-fA-F-]{36}")) {
            AdminHttp.sendError(exchange, 404, "Category not found.");
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
            } else {
                UUID id = UUID.fromString(path.substring(PATH.length() + 1));
                String category = method.equals("PUT") ? update(exchange, id) : find(id);
                if (category == null) {
                    AdminHttp.sendError(exchange, 404, "Category not found.");
                } else {
                    AdminHttp.sendJson(exchange, 200, category);
                }
            }
        } catch (IllegalArgumentException exception) {
            AdminHttp.sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                AdminHttp.sendError(exchange, 409, "A category with that name already exists.");
            } else {
                AdminHttp.sendError(exchange, 500, "Could not load or save event categories. Please try again.");
            }
        }
    }

    private static String list() throws SQLException {
        StringJoiner categories = new StringJoiner(",", "{\"categories\":[", "]}");
        try (var connection = Database.open();
             var statement = connection.prepareStatement(
                     "SELECT id, name, description FROM admin_event_categories ORDER BY lower(name), id");
             var rows = statement.executeQuery()) {
            while (rows.next()) categories.add(toJson(rows));
        }
        return categories.toString();
    }

    private static String find(UUID id) throws SQLException {
        try (var connection = Database.open();
             var statement = connection.prepareStatement(
                     "SELECT id, name, description FROM admin_event_categories WHERE id = ?")) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? toJson(rows) : null;
            }
        }
    }

    private static String update(HttpExchange exchange, UUID id) throws IOException, SQLException {
        var fields = AdminHttp.readForm(exchange, 65_536);
        String name = AdminHttp.required(fields, "name", 200);
        String description = fields.getOrDefault("description", "");
        if (description.length() > 5_000) {
            throw new IllegalArgumentException("Description must be 5000 characters or fewer.");
        }
        // UPDATE preserves the ID and cannot create a new category.
        try (var connection = Database.open();
             var statement = connection.prepareStatement("""
                     UPDATE admin_event_categories SET name = ?, description = ? WHERE id = ?
                     RETURNING id, name, description
                     """)) {
            statement.setString(1, name);
            statement.setString(2, description);
            statement.setObject(3, id);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? toJson(rows) : null;
            }
        }
    }

    private static String toJson(ResultSet row) throws SQLException {
        return "{\"id\":" + AuditLog.quote(row.getString("id"))
                + ",\"name\":" + AuditLog.quote(row.getString("name"))
                + ",\"description\":" + AuditLog.quote(row.getString("description")) + "}";
    }
}
