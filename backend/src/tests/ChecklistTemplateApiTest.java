import com.sun.net.httpserver.HttpServer;
import database.Database;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.StringJoiner;

/** Run only against a disposable, migrated PostgreSQL test database. */
public final class ChecklistTemplateApiTest {
    private static final String API = "/api/config/readiness-checklist-templates/default";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        HttpServer app = Main.start(0);
        try {
            request(app, "GET", "/admin/readiness-checklist", "", 200);
            expect(app, "PUT", form(), "{\"id\":\"default\",\"items\":[]}");
            String initial = "{\"id\":\"default\",\"items\":[\"Prepare résumé\",\"Research employers\",\"Practice interviews\"]}";
            expect(app, "PUT", form("Prepare résumé", "Research employers", "Practice interviews"), initial);
            expect(app, "GET", "", initial);

            // Edit the first item, remove the middle item, and append a new item.
            String updated = "{\"id\":\"default\",\"items\":[\"Review résumé\",\"Practice interviews\",\"Bring questions\"]}";
            expect(app, "PUT", form("  Review résumé  ", "Practice interviews", "Bring questions"), updated);
            for (String blank : new String[]{"", " ", "\t\n", "\u2003"}) {
                request(app, "PUT", API, form("Valid", blank), 400);
                expect(app, "GET", "", updated);
            }
            request(app, "PUT", API, "itemCount=2&item0=Missing+second+item", 400);
            request(app, "PUT", API, "itemCount=-1", 400);
            request(app, "PUT", API, "itemCount=201", 400);
            request(app, "PUT", API, form("x".repeat(1001)), 400);
            request(app, "POST", API, "", 405);
            request(app, "GET", API + "/extra", "", 404);
            expect(app, "GET", "", updated);

            // Fail the second insert after the old items were deleted: all changes must roll back.
            try (var connection = Database.open(); var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE admin_readiness_template_items ADD CONSTRAINT checklist_test_failure "
                        + "CHECK (item_text <> 'force-checklist-save-failure')");
                try {
                    String error = request(app, "PUT", API, form("First insert", "force-checklist-save-failure"), 500);
                    check(error.contains("Please try again"), "Missing save error message");
                    expect(app, "GET", "", updated);
                } finally {
                    statement.execute("ALTER TABLE admin_readiness_template_items DROP CONSTRAINT checklist_test_failure");
                }
            }

            // Retrieval is the readiness tracker's contract: a fresh, ordered list for a new checklist.
            String snapshotForNewChecklist = request(app, "GET", API, "", 200);
            check(snapshotForNewChecklist.equals(updated), "Consumer received the wrong template order");
            app.stop(0);
            app = Main.start(0);
            expect(app, "GET", "", updated);
            expect(app, "PUT", form(), "{\"id\":\"default\",\"items\":[]}");
            expect(app, "GET", "", "{\"id\":\"default\",\"items\":[]}");
            check(snapshotForNewChecklist.equals(updated), "An existing consumer snapshot changed");

            String escaped = "{\"id\":\"default\",\"items\":[\"Ask \\\"why\\\"\",\"Line one\\nLine two\"]}";
            expect(app, "PUT", form("Ask \"why\"", "Line one\nLine two"), escaped);
            expect(app, "GET", "", escaped);
            System.out.println("Readiness checklist API tests passed, including item order, rollback, and restart persistence.");
        } finally {
            app.stop(0);
            try (var connection = Database.open(); var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM admin_readiness_template_items WHERE template_id = 'default'");
            }
        }
    }

    private static String form(String... items) {
        StringJoiner fields = new StringJoiner("&");
        fields.add("itemCount=" + items.length);
        for (int index = 0; index < items.length; index++) {
            fields.add("item" + index + "=" + URLEncoder.encode(items[index], StandardCharsets.UTF_8));
        }
        return fields.toString();
    }

    private static void expect(HttpServer app, String method, String body, String expected) throws Exception {
        String actual = request(app, method, API, body, 200);
        check(expected.equals(actual), "Expected " + expected + " but got " + actual);
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
