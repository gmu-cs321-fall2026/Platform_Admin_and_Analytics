import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class AnalyticsTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger mode = new AtomicInteger();
        List<String> queries = new ArrayList<>();
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/events", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            queries.add(query);
            int page = query.startsWith("page=2&") ? 2 : 1;
            if (mode.get() == 2 || (mode.get() == 3 && page == 2)) {
                AdminHttp.sendError(exchange, 503, "Events database unavailable.");
                return;
            }
            int total = mode.get() == 1 ? 0 : 51;
            List<String> events = new ArrayList<>();
            for (int id = (page - 1) * 50 + 1; id <= Math.min(page * 50, total); id++) {
                String count = mode.get() == 4 ? "null" : "2";
                events.add("{\"id\":" + id + ",\"registeredCount\":" + count
                        + ",\"eventTitle\":\"Private test title\"}");
            }
            AdminHttp.sendJson(exchange, 200, "{\"items\":[" + String.join(",", events)
                    + "],\"page\":" + page + ",\"pageSize\":50,\"totalCount\":" + total + "}");
        });
        upstream.start();
        String origin = "http://127.0.0.1:" + upstream.getAddress().getPort();
        AnalyticsApi api = new AnalyticsApi(origin, () -> 7);
        HttpServer app = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        app.createContext("/api/admin/analytics/overview", api::handle);
        app.start();
        try {
            JsonObject totals = api.overview();
            check(totals.get("eventsCreated").getAsLong() == 51, "Count all event pages.");
            check(totals.get("eventRegistrations").getAsLong() == 102, "Sum all registration counts.");
            check(totals.get("moderationDecisions").getAsLong() == 7, "Use the moderation source.");
            check(totals.get("newAccounts").isJsonNull(), "Do not substitute demo or active-only accounts.");
            check(queries.equals(List.of("page=1&pageSize=50", "page=2&pageSize=50")), "Follow pagination.");
            check(!totals.toString().contains("Private test title"), "Expose aggregate counts only.");

            mode.set(1);
            totals = new AnalyticsApi(origin, () -> 0).overview();
            check(totals.get("eventsCreated").getAsLong() == 0, "An empty event source is zero.");
            check(totals.get("eventRegistrations").getAsLong() == 0, "Empty registrations are zero.");
            check(totals.get("moderationDecisions").getAsLong() == 0, "Empty moderation is zero.");

            mode.set(2);
            totals = api.overview();
            check(totals.get("eventsCreated").isJsonNull(), "A failed event source is unavailable.");
            check(totals.get("eventRegistrations").isJsonNull(), "Failed registrations are unavailable.");
            check(totals.get("moderationDecisions").getAsLong() == 7, "Failure does not hide healthy metrics.");

            mode.set(3);
            totals = api.overview();
            check(totals.get("eventsCreated").getAsLong() == 51, "Keep the returned event total.");
            check(totals.get("eventRegistrations").isJsonNull(), "Do not display a partial registration sum.");

            mode.set(4);
            check(api.overview().get("eventRegistrations").isJsonNull(), "Missing counts are unavailable.");
            check(new AnalyticsApi(null, () -> { throw new java.sql.SQLException("Test failure"); })
                    .overview().get("moderationDecisions").isJsonNull(), "Handle database failure.");

            mode.set(1);
            String url = "http://127.0.0.1:" + app.getAddress().getPort() + "/api/admin/analytics/overview";
            HttpClient client = HttpClient.newHttpClient();
            var response = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            check(response.statusCode() == 200, "The overview route works.");
            check(JsonParser.parseString(response.body()).getAsJsonObject().size() == 4,
                    "Return exactly four aggregate metrics.");
            var post = client.send(HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            check(post.statusCode() == 405, "The statistics route is read-only.");

            // Date filter tests
            var invalidDate = client.send(
                    HttpRequest.newBuilder(URI.create(
                            url + "?startDate=2026-02-30"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            
            check(invalidDate.statusCode() == 400,
                    "Invalid dates must return HTTP 400.");
            
            var reversedDates = client.send(
                    HttpRequest.newBuilder(URI.create(
                            url + "?startDate=2026-11-20&endDate=2026-11-01"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            
            check(reversedDates.statusCode() == 400,
                    "Reversed dates must return HTTP 400.");
            
            var validDates = client.send(
                    HttpRequest.newBuilder(URI.create(
                            url + "?startDate=2026-11-01&endDate=2026-11-20"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            
            check(validDates.statusCode() == 200,
                    "Valid date ranges must return HTTP 200.");
            
            JsonObject filteredTotals = JsonParser
                    .parseString(validDates.body()).getAsJsonObject();
            
            check(filteredTotals.get("eventsCreated").isJsonNull(),
                    "Do not return unfiltered event totals.");
            
            check(filteredTotals.get("eventRegistrations").isJsonNull(),
                    "Do not return unfiltered registration totals.");
            
            var sameDate = client.send(
                    HttpRequest.newBuilder(URI.create(
                            url + "?startDate=2026-11-20&endDate=2026-11-20"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            
            check(sameDate.statusCode() == 200,
                    "Same-day date ranges must be accepted.");
            
            var clearedFilter = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            
            check(clearedFilter.statusCode() == 200,
                    "Clearing the filter must restore the default overview.");
            
            check(JsonParser.parseString(clearedFilter.body())
                    .getAsJsonObject().get("moderationDecisions").getAsLong() == 7,
                    "Unfiltered moderation totals must remain unchanged.");
            
        } finally {
            app.stop(0);
            upstream.stop(0);
        }
        System.out.println("All usage statistics backend checks passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
