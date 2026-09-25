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
                "/admin/users",
                Main::handleDashboard
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

        server.start();

        System.out.println(
                "Admin & Analytics backend running on port 9000"
        );
    }

    private static void handleDashboard(
            HttpExchange exchange
    ) throws IOException {

        serveFile(
                exchange,
                "frontend/src/index.html",
                "text/html"
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