import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class ModerationApi {
    private static final Set<String> TYPES = Set.of(
            "FLAGGED_POST",
            "ACCOUNT_FLAG",
            "RESOURCE",
            "PRACTICE_QUESTION"
    );

    private static final Set<String> STATUSES = Set.of(
            "PENDING",
            "RESOLVED"
    );

    public record Item(
            String id,
            String type,
            String source,
            String targetId,
            String title,
            Instant submittedAt,
            String reason,
            String status,
            String details
    ) {
    }

    // Temporary demo data.
    // Replace this list with data from the owning subsystems later.
    private static final List<Item> ITEMS = List.of(
            new Item(
                    "MOD-001",
                    "FLAGGED_POST",
                    "Community",
                    "101",
                    "Interview discussion",
                    Instant.parse("2026-10-26T18:30:00Z"),
                    "Reported for inappropriate language.",
                    "PENDING",
                    "Demo forum post awaiting moderator review. "
                            + "The report concerns language used in the discussion."
            ),
            new Item(
                    "MOD-002",
                    "ACCOUNT_FLAG",
                    "User Accounts",
                    "1",
                    "John Doe",
                    Instant.parse("2026-10-26T17:45:00Z"),
                    "Reported for repeatedly posting unrelated content.",
                    "PENDING",
                    "Demo account report for user ID 1. "
                            + "Review the report and account activity before "
                            + "taking any action."
            ),
            new Item(
                    "MOD-003",
                    "RESOURCE",
                    "Events & Resources",
                    "201",
                    "Resume preparation guide",
                    Instant.parse("2026-10-26T16:20:00Z"),
                    "",
                    "PENDING",
                    "Demo student-submitted resource describing resume "
                            + "formatting and preparation tips. "
                            + "Awaiting review before publication."
            ),
            new Item(
                    "MOD-004",
                    "PRACTICE_QUESTION",
                    "Mock Interview & Peer Practice",
                    "301",
                    "Describe a time you solved a difficult problem",
                    Instant.parse("2026-10-26T15:10:00Z"),
                    "",
                    "PENDING",
                    "Demo behavioral interview question submitted by a student. "
                            + "Awaiting review before appearing in the question bank."
            ),
            new Item(
                    "MOD-005",
                    "FLAGGED_POST",
                    "Community",
                    "102",
                    "Career fair discussion",
                    Instant.parse("2026-10-25T19:00:00Z"),
                    "Reported as duplicate content.",
                    "RESOLVED",
                    "Demo previously reviewed forum report. "
                            + "Included to demonstrate filtering by review status."
            ),
            new Item(
                    "MOD-006",
                    "RESOURCE",
                    "Events & Resources",
                    "202",
                    "Technical interview checklist",
                    Instant.parse("2026-10-25T14:15:00Z"),
                    "",
                    "RESOLVED",
                    "Demo previously reviewed resource submission. "
                            + "Included to demonstrate resolved items."
            )
    );

    private ModerationApi() {
    }

    public static void handleQueue(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestURI().getPath()
                .equals("/api/admin/moderation")) {
            sendError(exchange, 404, "Not found");
            return;
        }

        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendError(
                    exchange,
                    405,
                    "This endpoint only supports viewing moderation items"
            );
            return;
        }

        try {
            Map<String, String> query = parseQuery(
                    exchange.getRequestURI().getRawQuery()
            );

            String type = query.getOrDefault("type", "");
            String status = query.getOrDefault("status", "");

            if (!type.isEmpty() && !TYPES.contains(type)) {
                throw new IllegalArgumentException(
                        "Choose a valid item type"
                );
            }

            if (!status.isEmpty() && !STATUSES.contains(status)) {
                throw new IllegalArgumentException(
                        "Choose a valid review status"
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
                        "Page must be positive and page size must be 1–50"
                );
            }

            List<Item> filtered = ITEMS.stream()
                    .filter(item ->
                            type.isEmpty() || item.type().equals(type))
                    .filter(item ->
                            status.isEmpty() || item.status().equals(status))
                    .sorted(Comparator.comparing(
                            Item::submittedAt
                    ).reversed())
                    .collect(Collectors.toList());

            String entries = filtered.stream()
                    .skip((long) (page - 1) * pageSize)
                    .limit(pageSize)
                    .map(ModerationApi::toJson)
                    .collect(Collectors.joining(","));

            String response = "{"
                    + "\"items\":[" + entries + "],"
                    + "\"page\":" + page + ","
                    + "\"pageSize\":" + pageSize + ","
                    + "\"total\":" + filtered.size() + ","
                    + "\"demoData\":true"
                    + "}";

            sendJson(exchange, 200, response);
        } catch (IllegalArgumentException exception) {
            sendError(
                    exchange,
                    400,
                    "Choose valid filters, a positive page number, "
                            + "and a page size between 1 and 50."
            );
        }
    }

    public static void handlePage(HttpExchange exchange)
            throws IOException {

        String path = exchange.getRequestURI().getPath();

        if (!path.equals("/admin/moderation")
                && !path.equals("/admin/moderation/")) {
            sendError(exchange, 404, "Not found");
            return;
        }

        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        Path page = Path.of(
                "frontend",
                "src",
                "moderation.html"
        );

        if (!Files.exists(page)) {
            sendError(
                    exchange,
                    404,
                    "The moderation page could not be found"
            );
            return;
        }

        byte[] bytes = Files.readAllBytes(page);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "text/html; charset=utf-8"
        );

        exchange.sendResponseHeaders(200, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
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

    private static String toJson(Item item) {
        // Reuses the JSON escaping helper from your audit-log code.
        return "{"
                + "\"id\":" + AuditLog.quote(item.id()) + ","
                + "\"type\":" + AuditLog.quote(item.type()) + ","
                + "\"source\":" + AuditLog.quote(item.source()) + ","
                + "\"targetId\":" + AuditLog.quote(item.targetId()) + ","
                + "\"title\":" + AuditLog.quote(item.title()) + ","
                + "\"submittedAt\":"
                + AuditLog.quote(item.submittedAt().toString()) + ","
                + "\"reason\":" + AuditLog.quote(item.reason()) + ","
                + "\"status\":" + AuditLog.quote(item.status()) + ","
                + "\"details\":" + AuditLog.quote(item.details())
                + "}";
    }

    private static void sendError(
            HttpExchange exchange,
            int status,
            String message
    ) throws IOException {

        sendJson(
                exchange,
                status,
                "{\"error\":" + AuditLog.quote(message) + "}"
        );
    }

    private static void sendJson(
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