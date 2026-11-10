
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnnouncementApi {

    private static final String SENDER = "demo-admin";

    private static final int MAX_BODY_SIZE = 20000;

    private static final Map<String, Broadcast> broadcastsByRequest =
            new LinkedHashMap<>();

    private static final List<Broadcast> broadcasts =
            new ArrayList<>();

    private static class Broadcast {

        String reference;
        String requestId;
        String title;
        String message;
        String audience;
        String sender;
        String sentAt;

        Broadcast(
                String requestId,
                String title,
                String message,
                String audience
        ) {
            this.reference = "BROADCAST-" + UUID.randomUUID();
            this.requestId = requestId;
            this.title = title;
            this.message = message;
            this.audience = audience;
            this.sender = SENDER;
            this.sentAt = Instant.now().toString();
        }

        String toJson(boolean duplicate) {
            return "{"
                    + "\"success\":true,"
                    + "\"broadcastReference\":\"" + escape(reference) + "\","
                    + "\"requestId\":\"" + escape(requestId) + "\","
                    + "\"title\":\"" + escape(title) + "\","
                    + "\"message\":\"" + escape(message) + "\","
                    + "\"audience\":\"" + escape(audience) + "\","
                    + "\"sender\":\"" + escape(sender) + "\","
                    + "\"sentAt\":\"" + escape(sentAt) + "\","
                    + "\"duplicate\":" + duplicate
                    + "}";
        }
    }

    public static void handle(HttpExchange exchange)
            throws IOException {

        String method = exchange.getRequestMethod();

        if (method.equals("GET")) {
            handleGet(exchange);
            return;
        }

        if (!method.equals("POST")) {
            exchange.getResponseHeaders().set(
                    "Allow", "GET, POST"
            );

            sendJson(exchange, 405,
                    "{\"error\":\"Method not allowed.\"}");
            return;
        }

        handlePost(exchange);
    }

    private static void handlePost(HttpExchange exchange)
            throws IOException {

        String body;

        try (InputStream input = exchange.getRequestBody()) {

            byte[] bytes = input.readNBytes(MAX_BODY_SIZE + 1);

            if (bytes.length > MAX_BODY_SIZE) {
                sendJson(exchange, 413,
                        "{\"error\":\"Request is too large.\"}");
                return;
            }

            body = new String(bytes, StandardCharsets.UTF_8);
        }

        Map<String, String> fields = parseForm(body);

        String requestId = fields.get("requestId");
        String title = fields.get("title");
        String message = fields.get("message");
        String audience = fields.get("audience");

        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {

            sendJson(exchange, 400,
                    "{\"error\":\"Invalid request identifier.\"}");
            return;
        }

        if (title == null || title.isBlank()
                || title.length() > 150) {

            sendJson(exchange, 400,
                    "{\"error\":\"A valid announcement title is required.\"}");
            return;
        }

        if (message == null || message.isBlank()
                || message.length() > 5000) {

            sendJson(exchange, 400,
                    "{\"error\":\"A valid announcement message is required.\"}");
            return;
        }

        if (audience == null ||
                !(audience.equals("STUDENTS")
                || audience.equals("STAFF")
                || audience.equals("EVERYONE"))) {

            sendJson(exchange, 400,
                    "{\"error\":\"Invalid announcement audience.\"}");
            return;
        }

        synchronized (broadcastsByRequest) {

            Broadcast existing =
                    broadcastsByRequest.get(requestId);

            if (existing != null) {

                if (!existing.title.equals(title)
                        || !existing.message.equals(message)
                        || !existing.audience.equals(audience)) {

                    sendJson(exchange, 409,
                            "{\"error\":\"Request identifier already used for another announcement.\"}");
                    return;
                }

                sendJson(exchange, 200,
                        existing.toJson(true));

                return;
            }

            Broadcast broadcast = new Broadcast(
                    requestId,
                    title,
                    message,
                    audience
            );

            broadcastsByRequest.put(
                    requestId,
                    broadcast
            );

            broadcasts.add(broadcast);

            sendJson(exchange, 201,
                    broadcast.toJson(false));
        }
    }

    private static void handleGet(HttpExchange exchange)
            throws IOException {

        StringBuilder json = new StringBuilder("[");

        synchronized (broadcastsByRequest) {

            for (int i = 0; i < broadcasts.size(); i++) {

                if (i > 0) {
                    json.append(",");
                }

                json.append(
                        broadcasts.get(i).toJson(false)
                );
            }
        }

        json.append("]");

        sendJson(exchange, 200, json.toString());
    }

    private static Map<String, String> parseForm(String body) {

        Map<String, String> result = new LinkedHashMap<>();

        for (String pair : body.split("&")) {

            String[] parts = pair.split("=", 2);

            String key = URLDecoder.decode(
                    parts[0],
                    StandardCharsets.UTF_8
            );

            String value = parts.length > 1
                    ? URLDecoder.decode(
                            parts[1],
                            StandardCharsets.UTF_8
                    )
                    : "";

            result.put(key, value);
        }

        return result;
    }

    private static String escape(String value) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String json
    ) throws IOException {

        byte[] response = json.getBytes(
                StandardCharsets.UTF_8
        );

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=utf-8"
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );

        exchange.sendResponseHeaders(
                status,
                response.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(response);
        }
    }
}
