import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

public class Server {
    private static final int SO_TIMEOUT = 1000 * 60 * 5;
    private static final int PORT_NO = 80;

    public static void main(String[] args) {

        try (ServerSocket server = new ServerSocket(PORT_NO)) {
            ClientsDirectory clientsDirectory = new ClientsDirectory();

            System.out.println(String.format("Server started on %s", server.getLocalPort()));

            while (true) {
                Socket connection = server.accept();
                System.out.println("New client connected;");
                connection.setSoTimeout(SO_TIMEOUT); // close connection after 5 mins of inactivity

                UUID clientId = UUID.randomUUID();
                Thread clientConnection = new ClientConnectionHandler(connection, clientId, clientsDirectory);
                clientConnection.setName(clientId.toString());

                clientConnection.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
