import com.sun.net.httpserver.HttpServer;
import database.Database;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Collectors;

/** Run against the disposable database created by the CI workflow. */
public final class AdminRecordsTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String AUDIT = "/api/admin/audit-log";
    private static final String MODERATION = "/api/admin/moderation";
    private static final String ANNOUNCEMENTS = "/api/admin/announcements";

    public static void main(String[] args) throws Exception {
        UUID legacyId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        importLegacyTwice(legacyId);
        String draft = form("id", draftId.toString(), "title", "Career Fair & workshops",
                "message", "Bring a résumé.\nDoors open at 10.", "studentIds", "4,6");

        HttpServer app = Main.start(0);
        try {
            expect(app, "GET", "/admin/announcements", "", "Save Draft");
            expect(app, "GET", AUDIT, "", legacyId.toString());
            expect(app, "PUT", "/api/users/4/role", "STAFF", "\"role\":\"STAFF\"");
            expect(app, "PUT", "/api/users/4/role", "STUDENT", "\"role\":\"STUDENT\"");
            expect(app, "GET", AUDIT + "?action=ROLE_CHANGED", "", "\"targetId\":\"4\"");

            expectStatus(app, "POST", MODERATION + "/MOD-001/decision",
                    form("action", "QUESTION_APPROVED", "reason", "Wrong action"), 400);
            expectStatus(app, "POST", MODERATION + "/MOD-001/decision",
                    form("action", "POST_REMOVED", "reason", " "), 400);
            assertDecisionRollsBack(app);
            expect(app, "POST", MODERATION + "/MOD-001/decision",
                    form("action", "POST_REMOVED", "reason", "Test report reviewed"), "\"status\":\"RESOLVED\"");
            expectStatus(app, "POST", MODERATION + "/MOD-001/decision",
                    form("action", "FLAG_DISMISSED", "reason", "Duplicate"), 409);
            expect(app, "POST", MODERATION + "/MOD-002/decision",
                    form("action", "ACCOUNT_SUSPENDED", "reason", "Account report reviewed"), "\"status\":\"RESOLVED\"");

            expectStatus(app, "PUT", ANNOUNCEMENTS,
                    form("id", draftId.toString(), "title", "Title", "message", "Message", "studentIds", "2"), 400);
            expect(app, "PUT", ANNOUNCEMENTS, draft, "\"dispatchStatus\":\"DRAFT\"");
            expect(app, "PUT", ANNOUNCEMENTS, draft, draftId.toString());
            expect(app, "GET", ANNOUNCEMENTS, "", "Career Fair & workshops");
        } finally {
            app.stop(0);
        }

        app = Main.start(0);
        try {
            String audit = expect(app, "GET", AUDIT, "", "Test report reviewed");
            check(audit.indexOf(legacyId.toString()) == audit.lastIndexOf(legacyId.toString()),
                    "The imported audit entry appeared more than once.");
            check(count("SELECT count(*) FROM admin_audit_entries WHERE action = 'POST_REMOVED' AND target_id = '101'") == 1,
                    "Expected exactly one audit entry for the moderation decision.");
            String queue = expect(app, "GET", MODERATION + "?status=RESOLVED", "", "\"id\":\"MOD-001\"");
            check(queue.contains("Test report reviewed") && queue.contains("\"action\":\"ACCOUNT_SUSPENDED\""),
                    "Saved decision details were missing after restart.");
            String users = expect(app, "GET", "/api/users", "", "\"id\":1,\"name\":\"John Doe\"");
            check(users.substring(0, users.indexOf("},{")).contains("\"status\":\"SUSPENDED\""),
                    "The flagged account's suspension was not restored.");
            String saved = expect(app, "GET", ANNOUNCEMENTS, "", draftId.toString());
            check(saved.indexOf(draftId.toString()) == saved.lastIndexOf(draftId.toString())
                            && saved.contains("\"dispatchStatus\":\"DRAFT\"")
                            && saved.contains("selected_students") && saved.contains("\"senderId\":")
                            && saved.contains("\"createdAt\":") && saved.contains("Bring a résumé."),
                    "Draft metadata was not restored or the retry duplicated the draft.");
        } finally {
            app.stop(0);
        }
        System.out.println("SCRUM-46 admin records integration checks passed.");
    }

    private static void importLegacyTwice(UUID id) throws Exception {
        var file = Files.createTempFile("scrum46-audit", ".tsv");
        try {
            String entry = Arrays.stream(new String[]{
                    id.toString(), "2026-10-01T15:00:00Z", "ROLE_CHANGED", "USER",
                    "6", "Sofia Garcia", "legacy-admin", "", "STAFF", "STUDENT"
            }).map(value -> Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                    .collect(Collectors.joining("\t"));
            Files.writeString(file, entry + "\n");
            AuditLog.importLegacy(file);
            AuditLog.importLegacy(file);
            check(Files.exists(file), "The original audit file should be retained.");
            check(count("SELECT count(*) FROM admin_audit_entries WHERE id = '" + id + "'") == 1,
                    "Legacy import did not preserve one copy of the original ID.");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void assertDecisionRollsBack(HttpServer app) throws Exception {
        execute("""
                ALTER TABLE admin_audit_entries ADD CONSTRAINT test_reject_moderation_reason
                CHECK (reason IS DISTINCT FROM 'scrum-46-rejected-write')
                """);
        try {
            String response = expectStatus(app, "POST", MODERATION + "/MOD-001/decision",
                    form("action", "POST_REMOVED", "reason", "scrum-46-rejected-write"), 500);
            check(response.contains("Could not save the moderation decision"), "Expected a user-facing save error.");
            check(count("SELECT count(*) FROM admin_moderation_decisions WHERE queue_item_id = 'MOD-001'") == 0,
                    "A failed audit write left a partially saved moderation decision.");
        } finally {
            execute("ALTER TABLE admin_audit_entries DROP CONSTRAINT test_reject_moderation_reason");
        }
    }

    private static void execute(String sql) throws Exception {
        try (var connection = Database.open(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long count(String sql) throws Exception {
        try (var connection = Database.open(); var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String form(String... fields) {
        var pairs = new java.util.StringJoiner("&");
        for (int index = 0; index < fields.length; index += 2) {
            pairs.add(fields[index] + "=" + URLEncoder.encode(fields[index + 1], StandardCharsets.UTF_8));
        }
        return pairs.toString();
    }

    private static String expect(HttpServer app, String method, String path, String body, String text)
            throws Exception {
        String response = expectStatus(app, method, path, body, 200);
        check(response.contains(text), method + " " + path + " omitted " + text);
        return response;
    }

    private static String expectStatus(HttpServer app, String method, String path, String body, int status)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getAddress().getPort() + path))
                .timeout(Duration.ofSeconds(8))
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == status, method + " " + path + " returned "
                + response.statusCode() + " instead of " + status + ": " + response.body());
        return response.body();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
