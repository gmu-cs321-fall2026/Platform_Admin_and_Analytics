import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class UserApi {
    private static final Path ROLE_FILE = Path.of("data", "user-roles.properties");
    private static final List<User> users = createUsers();

    private static List<User> createUsers() {
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
        if (Files.exists(ROLE_FILE)) {
            Properties saved = new Properties();
            try (var input = Files.newInputStream(ROLE_FILE)) {
                saved.load(input);
                for (User user : users) {
                    String role = saved.getProperty(String.valueOf(user.getId()));
                    if (User.isValidRole(role)) user.setRole(role);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not load saved user roles", e);
            }
        }
        return users;
    }

    public static synchronized void handleUsers(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (path.equals("/api/users")) {
            if (!method.equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            sendResponse(exchange, 200, usersToJson(users));
            return;
        }
        if (!path.matches("/api/users/[0-9]+/role")) {
            sendResponse(exchange, 404, "{\"error\":\"Not found\"}");
            return;
        }
        if (!method.equals("PUT")) {
            exchange.getResponseHeaders().set("Allow", "PUT");
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String id = path.split("/")[3];
        User user = users.stream().filter(u -> String.valueOf(u.getId()).equals(id)).findFirst().orElse(null);
        if (user == null) {
            sendResponse(exchange, 404, "{\"error\":\"User not found\"}");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(65);
        String role = new String(body, StandardCharsets.UTF_8).trim();
        if (body.length > 64 || !User.isValidRole(role)) {
            sendResponse(exchange, 400, "{\"error\":\"Choose STUDENT, STAFF, or ADMIN\"}");
            return;
        }
        Properties saved = new Properties();
        for (User current : users) {
            saved.setProperty(String.valueOf(current.getId()), current == user ? role : current.getRole());
        }
        try {
            Files.createDirectories(ROLE_FILE.getParent());
            Path temporary = ROLE_FILE.resolveSibling("user-roles.properties.tmp");
            try (var output = Files.newOutputStream(temporary)) {
                saved.store(output, "Saved demo user roles");
            }
            Files.move(temporary, ROLE_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            sendResponse(exchange, 500, "{\"error\":\"Could not save role. Please try again.\"}");
            return;
        }
        user.setRole(role);
        sendResponse(exchange, 200, usersToJson(List.of(user)));
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

    private static void sendResponse(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
