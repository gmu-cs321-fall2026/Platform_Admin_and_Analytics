import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public final class AuditApi {
    private AuditApi() {
    }

    public static void handle(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestURI().getPath()
                .equals("/api/admin/audit-log")) {
            sendError(exchange, 404, "Not found");
            return;
        }

        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendError(exchange, 405, "The audit log is read-only");
            return;
        }

        try {
            Map<String, String> query = parseQuery(
                    exchange.getRequestURI().getRawQuery()
            );

            String action = query.getOrDefault("action", "");

            if (!action.isEmpty()
                    && !AuditLog.ACTIONS.contains(action)) {
                throw new IllegalArgumentException(
                        "Choose a valid action type"
                );
            }

            LocalDate from = parseDate(query.get("from"));
            LocalDate to = parseDate(query.get("to"));

            if (from != null && to != null && from.isAfter(to)) {
                throw new IllegalArgumentException(
                        "Start date must not be after end date"
                );
            }

            int page = Integer.parseInt(
                    query.getOrDefault("page", "1")
            );

            int pageSize = Integer.parseInt(
                    query.getOrDefault("pageSize", "20")
            );

            if (page < 1 || pageSize < 1 || pageSize > 50) {
                throw new IllegalArgumentException(
                        "Page must be positive; page size must be 1–50"
                );
            }

            List<AuditLog.Entry> entries = AuditLog.readAll();

            // Reverse first so equal timestamps retain newest-write order.
            Collections.reverse(entries);

            List<AuditLog.Entry> filtered = entries.stream()
                    .filter(entry ->
                            action.isEmpty()
                                    || entry.action().equals(action))
                    .filter(entry -> {
                       LocalDate date = entry.timestamp().atZone(ZoneId.of("America/New_York")).toLocalDate();

                        return (from == null || !date.isBefore(from))
                                && (to == null || !date.isAfter(to));
                    })
                    .sorted(Comparator.comparing(
                            AuditLog.Entry::timestamp
                    ).reversed())
                    .collect(Collectors.toList());

            String rows = filtered.stream()
                    .skip((long) (page - 1) * pageSize)
                    .limit(pageSize)
                    .map(AuditLog::toJson)
                    .collect(Collectors.joining(","));

            String json = "{"
                    + "\"entries\":[" + rows + "],"
                    + "\"page\":" + page + ","
                    + "\"pageSize\":" + pageSize + ","
                    + "\"total\":" + filtered.size()
                    + "}";

            send(exchange, 200, json);
        } catch (IllegalArgumentException exception) {
            sendError(
                    exchange,
                    400,
                    "Invalid filters. Use valid action types, "
                            + "dates in YYYY-MM-DD format, and positive page numbers."
            );
        } catch (IOException exception) {
            sendError(
                    exchange,
                    500,
                    "Could not load the audit log. Please try again."
            );
        }
    }

    private static LocalDate parseDate(String value) {
        return value == null || value.isBlank()
                ? null
                : LocalDate.parse(value);
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> query = new HashMap<>();

        if (raw == null || raw.isBlank()) {
            return query;
        }

        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);

            String key = URLDecoder.decode(
                    parts[0],
                    StandardCharsets.UTF_8
            );

            String value = parts.length == 2
                    ? URLDecoder.decode(
                            parts[1],
                            StandardCharsets.UTF_8
                    )
                    : "";

            if (query.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate filter"
                );
            }
        }

        return query;
    }

    private static void sendError(
            HttpExchange exchange,
            int status,
            String message
    ) throws IOException {

        send(
                exchange,
                status,
                "{\"error\":" + AuditLog.quote(message) + "}"
        );
    }

    private static void send(
            HttpExchange exchange,
            int status,
            String json
    ) throws IOException {

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=utf-8"
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );

        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}