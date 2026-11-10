import com.sun.net.httpserver.HttpExchange;
import database.Database;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    private static final Map<String, List<String>> ACTIONS = Map.of(
            "FLAGGED_POST", List.of("POST_REMOVED", "USER_WARNED", "FLAG_DISMISSED"),
            "ACCOUNT_FLAG", List.of("ACCOUNT_SUSPENDED", "FLAG_DISMISSED"),
            "RESOURCE", List.of("RESOURCE_APPROVED", "RESOURCE_REJECTED"),
            "PRACTICE_QUESTION", List.of("QUESTION_APPROVED", "QUESTION_REJECTED")
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

    private record Decision(String type, String targetId, String action, String adminId,
                            String reason, Instant decidedAt) {
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

        String path = exchange.getRequestURI().getPath();
        if (path.matches("/api/admin/moderation/[^/]+/decision")) {
            handleDecision(exchange, path.split("/")[4]);
            return;
        }

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

            Map<String, Decision> saved = readDecisions();
            Map<String, Item> queue = new HashMap<>();
            ITEMS.forEach(item -> queue.put(item.id(), item));
            saved.forEach((id, decision) -> queue.putIfAbsent(id, new Item(
                    id, decision.type(), "Saved decision", decision.targetId(),
                    decision.targetId(), decision.decidedAt(), "", "RESOLVED", ""
            )));

            List<Item> filtered = queue.values().stream()
                    .filter(item ->
                            type.isEmpty() || item.type().equals(type))
                    .filter(item ->
                            status.isEmpty() || status(item, saved.get(item.id())).equals(status))
                    .sorted(Comparator.comparing(
                            Item::submittedAt
                    ).reversed().thenComparing(Item::id))
                    .collect(Collectors.toList());

            String entries = filtered.stream()
                    .skip((long) (page - 1) * pageSize)
                    .limit(pageSize)
                    .map(item -> toJson(item, saved.get(item.id())))
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
        } catch (SQLException exception) {
            sendError(exchange, 500, "Could not load moderation decisions. Please try again.");
        }
    }

    private static void handleDecision(HttpExchange exchange, String id) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            sendError(exchange, 405, "Method not allowed.");
            return;
        }
        Item item = ITEMS.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
        if (item == null) {
            sendError(exchange, 404, "Moderation item not found.");
            return;
        }
        if ("RESOLVED".equals(item.status())) {
            sendError(exchange, 409, "This moderation item is already resolved.");
            return;
        }
        try {
            var form = AdminHttp.readForm(exchange, 16_384);
            String action = AdminHttp.required(form, "action", 40);
            String reason = AdminHttp.required(form, "reason", 1_000);
            if (!ACTIONS.get(item.type()).contains(action)) {
                throw new IllegalArgumentException("Choose an action allowed for this moderation item.");
            }
            boolean saved = "ACCOUNT_SUSPENDED".equals(action)
                    ? UserApi.suspendFlaggedAccount(item, reason)
                    : saveDecision(item, AuditLog.newEntry(
                            action, item.type(), item.targetId(), item.title(), reason, "", ""));
            if (!saved) {
                sendError(exchange, 409, "This moderation item is already resolved.");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"RESOLVED\"}");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            sendError(exchange, 500, "Could not save the moderation decision. Please try again.");
        }
    }

    static boolean saveDecision(Item item, AuditLog.Entry entry) throws SQLException {
        String sql = """
                INSERT INTO admin_moderation_decisions
                    (queue_item_id, target_type, target_id, review_status, action, admin_id, reason, decided_at)
                VALUES (?, ?, ?, 'RESOLVED', ?, ?, ?, ?)
                ON CONFLICT (queue_item_id) DO UPDATE SET
                    review_status = EXCLUDED.review_status, action = EXCLUDED.action,
                    admin_id = EXCLUDED.admin_id, reason = EXCLUDED.reason, decided_at = EXCLUDED.decided_at
                WHERE admin_moderation_decisions.review_status = 'PENDING'
                    AND admin_moderation_decisions.target_type = EXCLUDED.target_type
                    AND admin_moderation_decisions.target_id = EXCLUDED.target_id
                """;
        try (var connection = Database.open()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, item.id());
                statement.setString(2, item.type());
                statement.setString(3, item.targetId());
                statement.setString(4, entry.action());
                statement.setString(5, entry.adminId());
                statement.setString(6, entry.reason());
                statement.setObject(7, entry.timestamp().atOffset(ZoneOffset.UTC));
                if (statement.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
                AuditLog.insert(connection, entry);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static Map<String, Decision> readDecisions() throws SQLException {
        String sql = """
                SELECT queue_item_id, target_type, target_id, action, admin_id, reason, decided_at
                FROM admin_moderation_decisions WHERE review_status = 'RESOLVED'
                """;
        Map<String, Decision> saved = new HashMap<>();
        try (var connection = Database.open(); var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                saved.put(rows.getString("queue_item_id"), new Decision(
                        rows.getString("target_type"), rows.getString("target_id"),
                        rows.getString("action"), rows.getString("admin_id"), rows.getString("reason"),
                        rows.getObject("decided_at", OffsetDateTime.class).toInstant()));
            }
        }
        return saved;
    }

    private static String status(Item item, Decision decision) {
        return decision == null ? item.status() : "RESOLVED";
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

    private static String toJson(Item item, Decision decision) {
        // Reuses the JSON escaping helper from your audit-log code.
        String status = status(item, decision);
        String actions = "PENDING".equals(status)
                ? ACTIONS.get(item.type()).stream().map(AuditLog::quote).collect(Collectors.joining(","))
                : "";
        String resolution = decision == null ? "null" : "{"
                + "\"action\":" + AuditLog.quote(decision.action()) + ","
                + "\"adminId\":" + AuditLog.quote(decision.adminId()) + ","
                + "\"reason\":" + AuditLog.quote(decision.reason()) + ","
                + "\"decidedAt\":" + AuditLog.quote(decision.decidedAt().toString()) + "}";
        return "{"
                + "\"id\":" + AuditLog.quote(item.id()) + ","
                + "\"type\":" + AuditLog.quote(item.type()) + ","
                + "\"source\":" + AuditLog.quote(item.source()) + ","
                + "\"targetId\":" + AuditLog.quote(item.targetId()) + ","
                + "\"title\":" + AuditLog.quote(item.title()) + ","
                + "\"submittedAt\":"
                + AuditLog.quote(item.submittedAt().toString()) + ","
                + "\"reason\":" + AuditLog.quote(item.reason()) + ","
                + "\"status\":" + AuditLog.quote(status) + ","
                + "\"allowedActions\":[" + actions + "],"
                + "\"decision\":" + resolution + ","
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
