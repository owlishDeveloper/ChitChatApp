import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int SO_TIMEOUT = 1000 * 60 * 5;
    private static final int PORT_NO = 80;
    private static final ExecutorService executor = Executors.newWorkStealingPool();

    public static void main(String[] args) {

        try (ServerSocket server = new ServerSocket(PORT_NO)) {
            ClientsDirectory clientsDirectory = new ClientsDirectory();

            System.out.println(String.format("Server started on %s", server.getLocalPort()));

            while (!executor.isShutdown()) {
                try {
                    final Socket connection = server.accept();
                    System.out.println("New client connected;");
                    connection.setSoTimeout(SO_TIMEOUT);

                    UUID clientId = UUID.randomUUID();
                    Runnable task = new ClientConnectionHandler(connection, clientId, clientsDirectory);

                    executor.execute(task);
                } catch (Throwable e) {
                    if (!executor.isShutdown()) {
                        System.out.println(String.format("Task submission rejected: %s", e));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
