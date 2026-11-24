import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import database.Database;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class AnalyticsApi {
    private static final int PAGE_SIZE = 50;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @FunctionalInterface
    interface ModerationCount {
        long read() throws Exception;
    }

    private final String eventsUrl;
    private final ModerationCount moderationCount;

    public AnalyticsApi() {
        this(System.getenv("EVENTS_API_URL"), AnalyticsApi::countModerationDecisions);
    }

    AnalyticsApi(String eventsUrl, ModerationCount moderationCount) {
        this.eventsUrl = eventsUrl;
        this.moderationCount = moderationCount;
    }

    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().equals("/api/admin/analytics/overview")) {
            AdminHttp.sendError(exchange, 404, "Not found.");
            return;
        }
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET");
            AdminHttp.sendError(exchange, 405, "Use GET to view usage statistics.");
            return;
        }
        try {
            LocalDate startDate = null;
            LocalDate endDate = null;
        
            String query = exchange.getRequestURI().getRawQuery();
        
            if (query != null) {
                for (String parameter : query.split("&")) {
                    String[] parts = parameter.split("=", 2);
        
                    if (parts.length != 2) continue;
        
                    String value = URLDecoder.decode(
                            parts[1], StandardCharsets.UTF_8);
        
                    if (value.isBlank()) continue;
        
                    if (parts[0].equals("startDate")) {
                        startDate = LocalDate.parse(value);
                    }
        
                    if (parts[0].equals("endDate")) {
                        endDate = LocalDate.parse(value);
                    }
                }
            }
        
            if (startDate != null && endDate != null
                    && startDate.isAfter(endDate)) {
                AdminHttp.sendError(exchange, 400,
                        "Start date cannot be after the end date.");
                return;
            }
        
            AdminHttp.sendJson(exchange, 200,
                    overview(startDate, endDate).toString());
        
        } catch (DateTimeParseException | IllegalArgumentException error) {
            AdminHttp.sendError(exchange, 400,
                    "Enter valid start and end dates.");
        }
        
    }

    JsonObject overview() {
        return overview(null, null);
    }
    
    JsonObject overview(LocalDate startDate, LocalDate endDate) {    
        JsonObject result = new JsonObject();
        // Shared Core has no implemented all-account count endpoint. Its draft
        // /users/search contract includes active accounts only.
        result.add("newAccounts", com.google.gson.JsonNull.INSTANCE);
        result.add("eventsCreated", com.google.gson.JsonNull.INSTANCE);
        result.add("eventRegistrations", com.google.gson.JsonNull.INSTANCE);
        result.add("moderationDecisions", com.google.gson.JsonNull.INSTANCE);

        boolean filtered = startDate != null || endDate != null;

        // Only use overall event totals when no date filter is applied.
        if (!filtered) {
            readEvents(result);
         }
            
        try {
            long count = filtered
                    ? countModerationDecisions(startDate, endDate)
                    : moderationCount.read();
            
            if (count < 0) throw new IllegalStateException("Invalid moderation count.");
            result.addProperty("moderationDecisions", count);
        } catch (Exception error) {
            System.err.println("Usage statistics: moderation count is unavailable.");
        }
        return result;
    }

    private void readEvents(JsonObject result) {
        if (eventsUrl == null || eventsUrl.isBlank()) return;
        try {
            URI base = URI.create(eventsUrl);
            if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                    || base.getHost() == null || base.getQuery() != null
                    || base.getFragment() != null || base.getUserInfo() != null) {
                throw new IllegalArgumentException("Invalid Events API URL.");
            }
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            JsonObject first = readPage(base, 1, deadline);
            long total = nonNegativeLong(first.get("totalCount"));
            result.addProperty("eventsCreated", total);
            long pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
            Set<Long> seen = new HashSet<>();
            long registrations = 0;
            for (long page = 1; page <= pages; page++) {
                JsonObject response = page == 1 ? first : readPage(base, page, deadline);
                if (nonNegativeLong(response.get("totalCount")) != total
                        || nonNegativeLong(response.get("page")) != page
                        || nonNegativeLong(response.get("pageSize")) != PAGE_SIZE) {
                    throw new IllegalStateException("Event pagination changed.");
                }
                JsonArray items = response.getAsJsonArray("items");
                long expected = Math.min(PAGE_SIZE, total - (page - 1) * PAGE_SIZE);
                if (items == null || items.size() != expected) {
                    throw new IllegalStateException("Incomplete event page.");
                }
                for (JsonElement item : items) {
                    JsonObject event = item.getAsJsonObject();
                    if (!seen.add(nonNegativeLong(event.get("id")))) {
                        throw new IllegalStateException("Duplicate event across pages.");
                    }
                    registrations = Math.addExact(registrations,
                            nonNegativeLong(event.get("registeredCount")));
                }
            }
            result.addProperty("eventRegistrations", registrations);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            System.err.println("Usage statistics: Events API request was interrupted.");
        } catch (Exception error) {
            System.err.println("Usage statistics: Events API counts could not be fully loaded.");
        }
    }

    private static JsonObject readPage(URI base, long page, long deadline) throws Exception {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IOException("Events API timed out.");
        URI uri = URI.create(base.toString().replaceAll("/+$", "")
                + "/events?page=" + page + "&pageSize=" + PAGE_SIZE);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(3).toNanos())))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Events API failed.");
        if (response.body().length() > 1_048_576) throw new IOException("Events API page is too large.");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static long nonNegativeLong(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing numeric count.");
        }
        long count = value.getAsBigDecimal().longValueExact();
        if (count < 0 || count > 9_007_199_254_740_991L) {
            throw new IllegalArgumentException("Invalid count.");
        }
        return count;
    }

    static long countModerationDecisions() throws Exception {
        try (var connection = Database.open();
             var statement = connection.prepareStatement(
                     "SELECT count(*) FROM admin_moderation_decisions WHERE review_status = 'RESOLVED'");
             var rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }

    static long countModerationDecisions(
            LocalDate startDate, LocalDate endDate) throws Exception {
    
        String sql = "SELECT count(*) FROM admin_moderation_decisions "
                + "WHERE review_status = 'RESOLVED'";
    
        if (startDate != null) {
            sql += " AND (decided_at AT TIME ZONE 'UTC')::date >= ?";
        }
    
        if (endDate != null) {
            sql += " AND (decided_at AT TIME ZONE 'UTC')::date <= ?";
        }
    
        try (var connection = Database.open();
             var statement = connection.prepareStatement(sql)) {
    
            int index = 1;
    
            if (startDate != null) {
                statement.setObject(index++, startDate);
            }
    
            if (endDate != null) {
                statement.setObject(index, endDate);
            }
    
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
