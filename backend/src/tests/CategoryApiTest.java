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
public final class CategoryApiTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String API = "/api/config/event-categories";

    public static void main(String[] args) throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String prefix = "story5-" + first;
        HttpServer app = null;
        try {
            insert(first, prefix + " Workshop");
            insert(second, prefix + " Networking");
            app = Main.start(0);
            expect(app, "GET", "/admin/event-categories", "", 200, "Event Categories");
            expect(app, "GET", API, "", 200, first.toString());
            expect(app, "PUT", API + "/" + first, form("  " + prefix + " Updated  ", "Résumé & interviews\nBring questions."),
                    200, "\"id\":\"" + first + "\"");
            expect(app, "GET", API + "/" + first, "", 200, "Résumé & interviews\\nBring questions.");
            for (String blank : new String[]{"", "  ", "\t\n", "\u2003"}) {
                expect(app, "PUT", API + "/" + first, form(blank, "Rejected"), 400, "error");
            }
            expect(app, "PUT", API + "/" + first,
                    form("  " + (prefix + " Networking").toUpperCase() + "  ", "Rejected"), 409, "already exists");
            expect(app, "PUT", API + "/" + first, form(prefix + " Updated", ""), 200, "\"description\":\"\"");
            expect(app, "PUT", API + "/" + UUID.randomUUID(), form("Missing", ""), 404, "not found");
            expect(app, "POST", API, "", 405, "not allowed");
            expect(app, "PUT", API + "/" + first, form("x".repeat(201), ""), 400, "error");
            expect(app, "PUT", API + "/" + first, form("Valid", "x".repeat(5001)), 400, "error");
            try (var connection = Database.open(); var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE admin_event_categories ADD CONSTRAINT story5_test_failure "
                        + "CHECK (name <> 'story5-force-save-failure')");
                try {
                    expect(app, "PUT", API + "/" + first, form("story5-force-save-failure", "Lost edit?"), 500, "Please try again");
                } finally {
                    statement.execute("ALTER TABLE admin_event_categories DROP CONSTRAINT story5_test_failure");
                }
            }
            expect(app, "GET", API + "/" + first, "", 200, prefix + " Updated");
            app.stop(0);
            app = Main.start(0);
            String saved = expect(app, "GET", API + "/" + first, "", 200, prefix + " Updated");
            check(saved.contains(first.toString()) && saved.contains("\"description\":\"\""), "Saved ID or description changed after restart");
            System.out.println("Story 5 category API checks passed, including PostgreSQL persistence after server restart.");
        } finally {
            if (app != null) app.stop(0);
            try (var connection = Database.open();
                 var statement = connection.prepareStatement("DELETE FROM admin_event_categories WHERE id IN (?, ?)")) {
                statement.setObject(1, first);
                statement.setObject(2, second);
                statement.executeUpdate();
            }
        }
    }

    private static void insert(UUID id, String name) throws Exception {
        try (var connection = Database.open();
             var statement = connection.prepareStatement("INSERT INTO admin_event_categories (id, name, description) VALUES (?, ?, 'Original')")) {
            statement.setObject(1, id);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    private static String form(String name, String description) {
        return "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&description=" + URLEncoder.encode(description, StandardCharsets.UTF_8);
    }

    private static String expect(HttpServer app, String method, String path, String body, int status, String text) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getAddress().getPort() + path))
                .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/x-www-form-urlencoded")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == status, method + " " + path + " expected " + status + ": " + response.body());
        check(response.body().contains(text), "Missing " + text + " in " + response.body());
        return response.body();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
