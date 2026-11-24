import com.sun.net.httpserver.HttpServer;
import database.Database;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Run only against a disposable, migrated PostgreSQL test database. */
public final class NotificationTemplateApiTest {
    private static final String API = "/api/config/notification-templates";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        HttpServer app = null;
        String updatedMessage = "  Bring your résumé & questions.\nAsk \"why\".  ";
        try {
            insert(first, "Event reminder");
            insert(second, "Checklist reminder");
            app = Main.start(0);
            check(request(app, "GET", "/admin/notification-templates", "", 200).contains("Preview Message"), "Missing editor page");
            String list = request(app, "GET", API, "", 200);
            check(list.contains(first.toString()) && list.contains(second.toString()), "Template selection records are missing");
            String saved = request(app, "PUT", API + "/" + first, form(updatedMessage), 200);
            String expected = "{\"id\":\"" + first + "\",\"name\":\"Event reminder\",\"message\":" + AuditLog.quote(updatedMessage) + "}";
            check(saved.equals(expected), "Saved text differs from the preview or template identity changed");
            check(request(app, "GET", API + "/" + first, "", 200).equals(expected), "Saved template retrieval failed");
            check(request(app, "GET", API + "/" + second, "", 200).contains("Original wording"), "Saving one template changed another");
            for (String blank : new String[]{"", "  ", "\t\n", "\u2003"}) {
                request(app, "PUT", API + "/" + first, form(blank), 400);
            }
            request(app, "PUT", API + "/" + first, form("x".repeat(5001)), 400);
            request(app, "PUT", API + "/" + first, "wrongField=Missing+message", 400);
            request(app, "PUT", API + "/" + UUID.randomUUID(), form("Missing template"), 404);
            request(app, "POST", API, "", 405);
            request(app, "GET", API + "/extra", "", 404);

            try (var connection = Database.open(); var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE admin_notification_templates ADD CONSTRAINT notification_test_failure "
                        + "CHECK (message <> 'force-notification-save-failure')");
                try {
                    String error = request(app, "PUT", API + "/" + first, form("force-notification-save-failure"), 500);
                    check(error.contains("Please try again"), "Missing save failure message");
                    check(request(app, "GET", API + "/" + first, "", 200).equals(expected), "A failed save changed the stored wording");
                } finally {
                    statement.execute("ALTER TABLE admin_notification_templates DROP CONSTRAINT notification_test_failure");
                }
            }

            app.stop(0);
            app = Main.start(0);
            // Notifications and Communications retrieves the saved wording using this GET contract.
            check(request(app, "GET", API + "/" + first, "", 200).equals(expected), "Updated wording was lost after restart");
            check(request(app, "GET", API, "", 200).contains(AuditLog.quote(updatedMessage)), "Template list is stale after restart");
            System.out.println("Notification template API tests passed, including validation, save failures, and restart persistence.");
        } finally {
            if (app != null) app.stop(0);
            try (var connection = Database.open(); var statement = connection.prepareStatement(
                    "DELETE FROM admin_notification_templates WHERE id IN (?, ?)")) {
                statement.setObject(1, first);
                statement.setObject(2, second);
                statement.executeUpdate();
            }
        }
    }

    private static void insert(UUID id, String name) throws Exception {
        try (var connection = Database.open(); var statement = connection.prepareStatement(
                "INSERT INTO admin_notification_templates (id, name, message) VALUES (?, ?, 'Original wording')")) {
            statement.setObject(1, id);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    private static String form(String message) {
        return "message=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
    }

    private static String request(HttpServer app, String method, String path, String body, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getAddress().getPort() + path))
                .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/x-www-form-urlencoded")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == status, method + " " + path + " expected " + status
                + " but got " + response.statusCode() + ": " + response.body());
        return response.body();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
