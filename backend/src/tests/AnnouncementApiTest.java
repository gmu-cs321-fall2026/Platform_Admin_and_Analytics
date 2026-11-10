import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnnouncementApiTest {

    private static final HttpClient client =
            HttpClient.newHttpClient();

    private static String baseUrl;

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext(
                "/api/admin/announcements",
                AnnouncementApi::handle
        );

        server.start();

        baseUrl = "http://127.0.0.1:"
                + server.getAddress().getPort()
                + "/api/admin/announcements";

        try {
            testSuccessfulAnnouncement();
            testDuplicatePrevention();
            testInvalidAudience();
            testRequestIdentifierConflict();
            testBroadcastRecording();

            System.out.println(
                    "All Announcement API tests passed!"
            );

        } finally {
            server.stop(0);
        }
    }

    private static HttpResponse<String> submit(
            String requestId,
            String title,
            String message,
            String audience
    ) throws Exception {

        String body =
                "requestId=" + encode(requestId)
                + "&title=" + encode(title)
                + "&message=" + encode(message)
                + "&audience=" + encode(audience);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(body)
                )
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private static void testSuccessfulAnnouncement()
            throws Exception {

        HttpResponse<String> response = submit(
                "test-success-1",
                "Career Fair",
                "The career fair begins Monday.",
                "STUDENTS"
        );

        assert response.statusCode() == 201;
        assert response.body().contains("\"success\":true");
        assert response.body().contains("\"audience\":\"STUDENTS\"");
        assert response.body().contains("\"sender\":\"demo-admin\"");
        assert response.body().contains("\"sentAt\":");

        System.out.println(
                "testSuccessfulAnnouncement passed"
        );
    }

    private static void testDuplicatePrevention()
            throws Exception {

        HttpResponse<String> first = submit(
                "test-duplicate-1",
                "Workshop",
                "Register for the workshop.",
                "EVERYONE"
        );

        HttpResponse<String> second = submit(
                "test-duplicate-1",
                "Workshop",
                "Register for the workshop.",
                "EVERYONE"
        );

        assert first.statusCode() == 201;
        assert second.statusCode() == 200;

        String firstReference =
                extractReference(first.body());

        String secondReference =
                extractReference(second.body());

        assert firstReference.equals(secondReference);
        assert second.body().contains("\"duplicate\":true");

        System.out.println(
                "testDuplicatePrevention passed"
        );
    }

    private static void testInvalidAudience()
            throws Exception {

        HttpResponse<String> response = submit(
                "test-invalid-1",
                "Announcement",
                "Test message",
                "INVALID"
        );

        assert response.statusCode() == 400;

        System.out.println(
                "testInvalidAudience passed"
        );
    }

    private static void testRequestIdentifierConflict()
            throws Exception {

        submit(
                "test-conflict-1",
                "First announcement",
                "First message",
                "STUDENTS"
        );

        HttpResponse<String> response = submit(
                "test-conflict-1",
                "Different announcement",
                "Different message",
                "STAFF"
        );

        assert response.statusCode() == 409;

        System.out.println(
                "testRequestIdentifierConflict passed"
        );
    }

    private static void testBroadcastRecording()
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assert response.statusCode() == 200;
        assert response.body().contains("Career Fair");
        assert response.body().contains("test-success-1");
        assert response.body().contains("BROADCAST-");

        System.out.println(
                "testBroadcastRecording passed"
        );
    }

    private static String extractReference(String json) {

        Matcher matcher = Pattern.compile(
                "\"broadcastReference\":\"([^\"]+)\""
        ).matcher(json);

        if (!matcher.find()) {
            throw new AssertionError(
                    "Broadcast reference was not returned."
            );
        }

        return matcher.group(1);
    }
}