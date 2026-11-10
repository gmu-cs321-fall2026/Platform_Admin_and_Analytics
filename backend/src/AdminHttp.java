import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class AdminHttp {
    private AdminHttp() {}

    static Map<String, String> readForm(HttpExchange exchange, int maxBytes) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("The submitted form is too large.");
        }
        Map<String, String> fields = new HashMap<>();
        for (String pair : new String(bytes, StandardCharsets.UTF_8).split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid form data.");
            }
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            if (value.indexOf('\0') >= 0 || fields.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Invalid form data.");
            }
        }
        return fields;
    }

    static String required(Map<String, String> fields, String name, int maxLength) {
        String value = fields.getOrDefault(name, "").strip();
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Enter a valid " + name + ".");
        }
        return value;
    }

    static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"error\":" + AuditLog.quote(message) + "}");
    }

    static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
