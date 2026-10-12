 import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class UserApi {

    public static void handleUsers(
            HttpExchange exchange) throws IOException {

        // Only allow GET requests
        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("GET")) {

            sendResponse(
                    exchange,
                    405,
                    "{\"error\":\"Method Not Allowed\"}"
            );

            return;
        }

        // Temporary test data
        // Later this can come from a database
        // or another subsystem's API.
        List<User> users = new ArrayList<>();

        users.add(new User(1, "John Doe", "orbit742@example.com", "STUDENT", "ACTIVE"));
        users.add(new User(2, "Jane Smith", "cedar318@example.com", "ADMIN", "ACTIVE"));
        users.add(new User(3, "Alex Johnson", "comet905@example.com", "STAFF", "SUSPENDED"));
        users.add(new User(4, "Maya Patel", "harbor261@example.com", "STUDENT", "ACTIVE"));
        users.add(new User(5, "Noah Williams", "maple804@example.com", "STAFF", "ACTIVE"));
        users.add(new User(6, "Sofia Garcia", "quartz537@example.com", "STUDENT", "ACTIVE"));
        users.add(new User(7, "Ethan Brown", "river196@example.com", "ADMIN", "SUSPENDED"));
        users.add(new User(8, "Ava Nguyen", "nova623@example.com", "STUDENT", "ACTIVE"));
        users.add(new User(9, "Liam Davis", "pine458@example.com", "STAFF", "ACTIVE"));
        users.add(new User(10, "Isabella Wilson", "lunar079@example.com", "STUDENT", "ACTIVE"));
        users.add(new User(11, "Oliver Chen", "cloud382@example.com", "STUDENT", "SUSPENDED"));
        users.add(new User(12, "Amelia Robinson", "coral651@example.com", "ADMIN", "ACTIVE"));
        users.add(new User(13, "Lucas Martinez", "pixel924@example.com", "STAFF", "ACTIVE"));
        users.add(new User(14, "Charlotte Lee", "meadow173@example.com", "STUDENT", "SUSPENDED"));
        users.add(new User(15, "Elijah Thompson", "cobalt860@example.com", "STUDENT", "ACTIVE"));

        // Convert users to JSON
        String json = usersToJson(users);

        sendResponse(
                exchange,
                200,
                json
        );
    }

    private static String usersToJson(
            List<User> users) {

        StringBuilder json = new StringBuilder();

        json.append("[");

        for (int i = 0; i < users.size(); i++) {

            User user = users.get(i);

            json.append("{");

            json.append("\"id\":")
                    .append(user.getId())
                    .append(",");

            json.append("\"name\":\"")
                    .append(user.getName())
                    .append("\",");

            json.append("\"email\":\"")
                    .append(user.getEmail())
                    .append("\",");

            json.append("\"role\":\"")
                    .append(user.getRole())
                    .append("\",");

            json.append("\"status\":\"")
                    .append(user.getStatus())
                    .append("\"");

            json.append("}");

            // Add comma between users
            if (i < users.size() - 1) {
                json.append(",");
            }
        }

        json.append("]");

        return json.toString();
    }

    private static void sendResponse(
            HttpExchange exchange,
            int statusCode,
            String response)
            throws IOException {

        // Allow the JavaScript frontend to communicate
        // with the Java backend.
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
        );

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );

        byte[] responseBytes =
                response.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(
                statusCode,
                responseBytes.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(responseBytes);
        }
    }
}