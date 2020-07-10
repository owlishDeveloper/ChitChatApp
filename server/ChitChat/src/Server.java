import java.io.IOException;
import java.net.*;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Server {
    private static final int SO_TIMEOUT = 1000 * 60 * 5;
    private static final int PORT_NO = 80;
    private static final ExecutorService executor = Executors.newWorkStealingPool();

    public static void main(String[] args) {

        try (AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(PORT_NO))) {
            ClientsDirectory clientsDirectory = new ClientsDirectory();

            System.out.println(String.format("Server started on %s", server.getLocalAddress()));

            while (!executor.isShutdown()) {
                try {
                    AsynchronousSocketChannel connection = server.accept().get();
                    System.out.println("New client connected;");

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
