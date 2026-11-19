import com.sun.net.httpserver.HttpExchange;
import database.Database;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Saved default template, also retrieved by the readiness tracker for new checklists. */
final class ChecklistTemplateApi {
    private static final String PATH = "/api/config/readiness-checklist-templates/default";

    private ChecklistTemplateApi() {}

    static void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().equals(PATH)) {
            AdminHttp.sendError(exchange, 404, "Checklist template not found.");
            return;
        }
        try {
            switch (exchange.getRequestMethod()) {
                case "GET" -> {
                    String template = read();
                    if (template == null) {
                        AdminHttp.sendError(exchange, 404, "Checklist template not found.");
                    } else {
                        AdminHttp.sendJson(exchange, 200, template);
                    }
                }
                case "PUT" -> save(exchange);
                default -> {
                    exchange.getResponseHeaders().set("Allow", "GET, PUT");
                    AdminHttp.sendError(exchange, 405, "Method not allowed.");
                }
            }
        } catch (IllegalArgumentException exception) {
            AdminHttp.sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            AdminHttp.sendError(exchange, 500, "Could not load or save the checklist template. Please try again.");
        }
    }

    private static String read() throws SQLException {
        // One query gives consumers a consistent snapshot of the template and its order.
        String sql = """
                SELECT item.position, item.item_text
                FROM admin_readiness_templates template
                LEFT JOIN admin_readiness_template_items item ON item.template_id = template.id
                WHERE template.id = 'default' ORDER BY item.position
                """;
        try (var connection = Database.open(); var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            if (!rows.next()) return null;
            List<String> items = new ArrayList<>();
            do {
                if (rows.getObject("position") != null) items.add(rows.getString("item_text"));
            } while (rows.next());
            return toJson(items);
        }
    }

    private static void save(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> fields = AdminHttp.readForm(exchange, 1_048_576);
        int count;
        try {
            count = Integer.parseInt(fields.getOrDefault("itemCount", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Submit a valid checklist item count.");
        }
        if (count < 0 || count > 200 || fields.size() != count + 1) {
            throw new IllegalArgumentException("Submit up to 200 checklist items in order.");
        }
        List<String> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String item = fields.getOrDefault("item" + index, "").strip();
            if (item.isBlank() || item.length() > 1_000) {
                throw new IllegalArgumentException("Checklist items cannot be blank and must be 1000 characters or fewer.");
            }
            items.add(item);
        }

        try (var connection = Database.open()) {
            connection.setAutoCommit(false);
            try {
                // Serialize replacements so concurrent requests cannot mix their items.
                try (var lock = connection.prepareStatement(
                        "SELECT id FROM admin_readiness_templates WHERE id = 'default' FOR UPDATE");
                     var rows = lock.executeQuery()) {
                    if (!rows.next()) {
                        connection.rollback();
                        AdminHttp.sendError(exchange, 404, "Checklist template not found.");
                        return;
                    }
                }
                try (var delete = connection.prepareStatement(
                        "DELETE FROM admin_readiness_template_items WHERE template_id = 'default'")) {
                    delete.executeUpdate();
                }
                try (var insert = connection.prepareStatement("""
                        INSERT INTO admin_readiness_template_items (template_id, position, item_text)
                        VALUES ('default', ?, ?)
                        """)) {
                    for (int index = 0; index < items.size(); index++) {
                        insert.setInt(1, index);
                        insert.setString(2, items.get(index));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
        AdminHttp.sendJson(exchange, 200, toJson(items));
    }

    private static String toJson(List<String> items) {
        return items.stream().map(AuditLog::quote)
                .collect(Collectors.joining(",", "{\"id\":\"default\",\"items\":[", "]}"));
    }
}
