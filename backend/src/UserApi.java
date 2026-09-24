 
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

        users.add(new User(
                1,
                "John Doe",
                "john@gmu.edu",
                "STUDENT",
                "ACTIVE"
        ));

        users.add(new User(
                2,
                "Jane Smith",
                "jane@gmu.edu",
                "ADMIN",
                "ACTIVE"
        ));

        users.add(new User(
                3,
                "Alex Johnson",
                "alex@gmu.edu",
                "STAFF",
                "SUSPENDED"
        ));

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