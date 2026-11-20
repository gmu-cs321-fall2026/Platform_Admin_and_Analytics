import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/** Reads weekly registration counts from the integration-facing registration data source. */
public final class WeeklyRegistrationApi {
    private static final int DEFAULT_WEEKS = 12;
    private static final int MAX_WEEKS = 52;

    private WeeklyRegistrationApi() { }

    public static void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            send(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        int weeks;
        try {
            weeks = requestedWeeks(exchange.getRequestURI().getRawQuery());
        } catch (IllegalArgumentException invalid) {
            send(exchange, 400, "{\"error\":\"Weeks must be between 1 and 52.\"}");
            return;
        }

        try {
            send(exchange, 200, weeklyCountsJson(weeks));
        } catch (SQLException failure) {
            // Do not expose database details or credentials to the browser.
            send(exchange, 500, "{\"error\":\"Unable to load weekly registrations. Please try again later.\"}");
        }
    }

    static int requestedWeeks(String query) {
        if (query == null || query.isBlank()) return DEFAULT_WEEKS;
        for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=", 2);
            if (pair.length == 2 && "weeks".equals(pair[0])) {
                try {
                    int value = Integer.parseInt(pair[1]);
                    if (value < 1 || value > MAX_WEEKS) throw new IllegalArgumentException();
                    return value;
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException(invalid);
                }
            }
        }
        return DEFAULT_WEEKS;
    }

    static String weeklyCountsJson(int weeks) throws SQLException {
        return weeklyCountsJson(weeks, new PostgresRegistrationRepository());
    }

    static String weeklyCountsJson(int weeks, RegistrationRepository repository) throws SQLException {
        StringBuilder json = new StringBuilder("{\"weeks\":[");
        boolean first = true;
        for (RegistrationRepository.WeeklyRegistration registration
                : repository.findWeeklyRegistrations(weeks)) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"weekStart\":\"").append(registration.weekStart())
                    .append("\",\"registrationCount\":")
                    .append(registration.registrationCount()).append('}');
        }
        return json.append("]}").toString();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
