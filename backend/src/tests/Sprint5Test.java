import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import database.Database;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/** Runs only against CI's disposable database. */
public final class Sprint5Test {
    public static void main(String[] args) throws Exception {
        for (String invalid : new String[] {"startDate=2026-02-30", "startDate=2026-04-02&endDate=2026-04-01",
                "startDate=2026-01-01&startDate=2026-01-02", "unknown=2026-01-01"}) {
            try { StatisticsDateRange.parse(invalid); throw new AssertionError("Invalid filter accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        var range = StatisticsDateRange.parse("startDate=2060-03-14&endDate=2060-03-14");
        check(Duration.between(range.startInclusive(), range.endExclusive()).toHours() == 23,
                "Inclusive Eastern dates must account for daylight saving time");
        String prefix = "sprint5-" + UUID.randomUUID();
        HttpServer app = Main.start(0);
        try {
            for (int index = 0; index < 4; index++) {
                var instant = switch (index) {
                    case 0 -> range.startInclusive().minusNanos(1_000);
                    case 1 -> range.startInclusive();
                    case 2 -> range.endExclusive().minusNanos(1_000);
                    default -> range.endExclusive();
                };
                try (var connection = Database.open(); var statement = connection.prepareStatement("""
                        INSERT INTO admin_moderation_decisions
                        (queue_item_id, target_type, target_id, review_status, action, admin_id, reason, decided_at)
                        VALUES (?, 'FLAGGED_POST', ?, 'RESOLVED', 'FLAG_DISMISSED', 'test-admin', 'Date boundary test', ?)
                        """)) {
                    statement.setString(1, prefix + index);
                    statement.setString(2, prefix);
                    statement.setObject(3, instant);
                    statement.executeUpdate();
                }
            }
            var response = get(app, "/api/admin/analytics/overview?startDate=2060-03-14&endDate=2060-03-14");
            var totals = JsonParser.parseString(response.body()).getAsJsonObject();
            check(response.statusCode() == 200 && totals.get("moderationDecisions").getAsInt() == 2,
                    "Include both boundary dates and exclude records outside them");
            check(totals.get("eventsCreated").isJsonNull(), "Never label unfiltered Events counts as filtered");
            check(get(app, "/api/admin/analytics/overview?startDate=bad").statusCode() == 400, "Reject invalid dates");
            check(get(app, "/api/admin/analytics/weekly-registrations").statusCode() == 503,
                    "Missing registration data must be unavailable, not a fabricated zero");
            check(get(app, "/admin/settings").statusCode() == 200, "Settings page route");
            check(get(app, "/api/admin/settings").statusCode() == 200, "Settings API must match the repaired schema");
            var statusRequest = HttpRequest.newBuilder(uri(app, "/api/users/4/status"))
                    .PUT(HttpRequest.BodyPublishers.ofString("FLAGGED")).build();
            check(HttpClient.newHttpClient().send(statusRequest, HttpResponse.BodyHandlers.ofString()).statusCode() == 400,
                    "Account flags must not be accepted as an access status");
        } finally {
            app.stop(0);
            try (var connection = Database.open(); var statement = connection.prepareStatement(
                    "DELETE FROM admin_moderation_decisions WHERE target_id = ?")) {
                statement.setString(1, prefix);
                statement.executeUpdate();
            }
        }
        System.out.println("Sprint 5 checks passed: date boundaries, DST, unavailable sources, settings, and account status.");
    }
    private static URI uri(HttpServer app, String path) {
        return URI.create("http://127.0.0.1:" + app.getAddress().getPort() + path);
    }
    private static HttpResponse<String> get(HttpServer app, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(app, path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
