import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

public class Main {

    public static void main(String[] args) throws Exception {

        // Create HTTP server on port 9000
        HttpServer server = HttpServer.create(
                new InetSocketAddress(9000),
                0
        );

        // Connect API endpoint to UserApi
        server.createContext(
                "/admin/users",
                UserApi::handleUsers
        );

        // Start server
        server.start();

        System.out.println(
                "Admin & Analytics backend running on port 9000"
        );
    }
}