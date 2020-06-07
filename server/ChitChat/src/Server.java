import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) {

        try (ServerSocket server = new ServerSocket(80)) {

            System.out.println(String.format("Server started on %s", server.getLocalPort()));

            while (true) {
                Socket connection = server.accept();
                System.out.println("New client connected;");

                new ClientConnectionHandler(connection).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
