import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    public static void main(String[] args) {

        try (ServerSocket server = new ServerSocket(80)) {
            ConcurrentHashMap<String, OutputStream> usernameToClientOutput = new ConcurrentHashMap<>();

            System.out.println(String.format("Server started on %s", server.getLocalPort()));

            while (true) {
                Socket connection = server.accept();
                System.out.println("New client connected;");


                new ClientConnectionHandler(connection, usernameToClientOutput).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
