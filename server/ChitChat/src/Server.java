import java.io.IOException;
import java.net.ServerSocket;

public class Server {
    public static void main(String[] args) {

        try (ServerSocket server = new ServerSocket(80)) {

            System.out.println(String.format("Server started on %s", server.getLocalPort()));

            do {
                new ClientConnection(server).start();
            } while (!server.isClosed());


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
