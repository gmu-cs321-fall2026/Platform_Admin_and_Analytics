
import com.sun.net.httpserver.HttpExchange;
import database.Database;

import java.io.IOException;
import java.sql.SQLException;
import java.util.StringJoiner;

final class SettingsApi {

    private static final String PATH = "/api/admin/settings";

    private SettingsApi() {}

    static void handle(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestURI().getPath().equals(PATH)) {
            AdminHttp.sendError(exchange, 404, "Not found.");
            return;
        }

        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET");
            AdminHttp.sendError(exchange, 405, "Method not allowed.");
            return;
        }
        
        // Demo-only authorization.
        // Production access must use authenticated user roles.
        String demoAdminId = System.getenv("DEMO_ADMIN_ID");

        if (!"demo-admin".equals(demoAdminId)) {
            AdminHttp.sendError(
                exchange,
                403,
                "Platform Admin access required."
            );
            return;
        }

        try {
            AdminHttp.sendJson(exchange, 200, loadSettings());
        } catch (SQLException exception) {
            AdminHttp.sendError(
                exchange,
                500,
                "Could not load platform settings. Please try again."
            );
        }
    }

       private static String loadSettings() throws SQLException {
        StringJoiner categories = new StringJoiner(",", "[", "]");
        StringJoiner readiness = new StringJoiner(",", "[", "]");
        StringJoiner notifications = new StringJoiner(",", "[", "]");

        try (var connection = Database.open()) {
            try (var statement = connection.prepareStatement(
                    "SELECT id, name, description "
                            + "FROM admin_event_categories "
                            + "ORDER BY lower(name), id");
                 var rows = statement.executeQuery()) {

                while (rows.next()) {
                    categories.add(
                            "{\"id\":" + AuditLog.quote(rows.getString("id"))
                                    + ",\"name\":" + AuditLog.quote(rows.getString("name"))
                                    + ",\"description\":"
                                    + AuditLog.quote(rows.getString("description"))
                                    + "}"
                    );
                }
            }

            String checklistSql = """
                    SELECT template.id,
                           COALESCE(
                               jsonb_agg(item.item_text ORDER BY item.position)
                                   FILTER (WHERE item.position IS NOT NULL),
                               '[]'::jsonb
                           ) AS items
                    FROM admin_readiness_templates template
                    LEFT JOIN admin_readiness_template_items item
                        ON item.template_id = template.id
                    GROUP BY template.id
                    ORDER BY template.id
                    """;

            try (var statement = connection.prepareStatement(checklistSql);
                 var rows = statement.executeQuery()) {

                while (rows.next()) {
                    readiness.add(
                            "{\"id\":" + AuditLog.quote(rows.getString("id"))
                                    + ",\"items\":" + rows.getString("items")
                                    + "}"
                    );
                }
            }

            try (var statement = connection.prepareStatement(
                    "SELECT id, name, message "
                            + "FROM admin_notification_templates "
                            + "ORDER BY name, id");
                 var rows = statement.executeQuery()) {

                while (rows.next()) {
                    notifications.add(
                            "{\"id\":" + AuditLog.quote(rows.getString("id"))
                                    + ",\"name\":" + AuditLog.quote(rows.getString("name"))
                                    + ",\"message\":"
                                    + AuditLog.quote(rows.getString("message"))
                                    + "}"
                    );
                }
            }
        }

        return "{\"eventCategories\":" + categories
                + ",\"readinessTemplates\":" + readiness
                + ",\"notificationTemplates\":" + notifications
                + "}";
    }
}
