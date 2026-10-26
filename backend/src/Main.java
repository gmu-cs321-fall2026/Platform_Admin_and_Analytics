import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(
                new InetSocketAddress(9000),
                0
        );

        // Admin dashboard
        server.createContext(
                "/admin",
                Main::handleAdminPage
        );

        // CSS
        server.createContext(
                "/admin/style.css",
                exchange -> serveFile(
                        exchange,
                        "frontend/src/style.css",
                        "text/css"
                )
        );

        // JavaScript
        server.createContext(
                "/admin/app.js",
                exchange -> serveFile(
                        exchange,
                        "frontend/src/app.js",
                        "application/javascript"
                )
        );

        server.createContext("/api/users", UserApi::handleUsers);
                server.createContext(
                "/api/admin/audit-log",
                AuditApi::handle
        );

        server.createContext(
                "/admin/audit-log.js",
                exchange -> serveFile(
                        exchange,
                        "frontend/src/audit-log.js",
                        "application/javascript; charset=utf-8"
                )
        );
        // Moderation queue API
        server.createContext(
                "/api/admin/moderation",
                ModerationApi::handleQueue
        );

        // Moderation page
        server.createContext(
                "/admin/moderation",
                ModerationApi::handlePage
        );

        // Moderation JavaScript
        server.createContext(
                "/admin/moderation.js",
                exchange -> serveFile(
                        exchange,
                        "frontend/src/moderation.js",
                        "application/javascript; charset=utf-8"
                )
        );
        server.start();

        System.out.println(
                "Admin & Analytics backend running on port 9000"
        );
    }

        private static void handleAdminPage(
            HttpExchange exchange
    ) throws IOException {

        String requestPath =
                exchange.getRequestURI().getPath();

        boolean dashboard = requestPath.equals("/admin")
                || requestPath.equals("/admin/");

        boolean users = requestPath.equals("/admin/users")
                || requestPath.equals("/admin/users/");

        boolean audit = requestPath.equals("/admin/audit-log")
                || requestPath.equals("/admin/audit-log/");

        if (!dashboard && !users && !audit) {
            byte[] message = "Not found".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
            );

            exchange.sendResponseHeaders(404, message.length);

            try (OutputStream output =
                         exchange.getResponseBody()) {
                output.write(message);
            }

            return;
        }

        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        serveFile(
                exchange,
                audit
                        ? "frontend/src/audit-log.html"
                        : "frontend/src/index.html",
                "text/html; charset=utf-8"
        );
    }

    private static void serveFile(
            HttpExchange exchange,
            String filePath,
            String contentType
    ) throws IOException {

        Path path = Path.of(filePath);

        if (!Files.exists(path)) {

            String response =
                    "File not found: " + filePath;

            exchange.sendResponseHeaders(
                    404,
                    response.length()
            );

            try (OutputStream output =
                         exchange.getResponseBody()) {

                output.write(
                        response.getBytes()
                );
            }

            return;
        }

        byte[] file =
                Files.readAllBytes(path);

        exchange.getResponseHeaders().set(
                "Content-Type",
                contentType
        );

        exchange.sendResponseHeaders(
                200,
                file.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(file);
        }
    }
}