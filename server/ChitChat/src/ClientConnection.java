import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientConnection extends Thread {
    private Socket connection;

    public ClientConnection(ServerSocket server) {
        System.out.println(String.format("Created a thread %s", Thread.currentThread().getName()));
        try {
            this.connection = server.accept(); // blocks the current thread until the connection is made
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(String.format("Created a thread %s", Thread.currentThread().getName()));
    }

    @Override
    public void run() {
        System.out.println("A client connected.");

        BufferedReader reader;
        try (
                InputStream input = connection.getInputStream();
                OutputStream output = connection.getOutputStream()
        ) {
            try (Scanner scanner = new Scanner(input, StandardCharsets.UTF_8)) {
                // Handshake
                String data = scanner.useDelimiter("\\r\\n\\r\\n").next();
                Matcher get = Pattern.compile("^GET").matcher(data);
                if (get.find()) {
                    Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                    match.find();
                    byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                            + "Connection: Upgrade\r\n"
                            + "Upgrade: websocket\r\n"
                            + "Sec-WebSocket-Accept: "
                            + Base64.getEncoder().encodeToString(
                            MessageDigest.getInstance("SHA-1").digest(
                                    (match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)))
                            + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                    output.write(response, 0, response.length);
                }
                System.out.println("Performed WebSocket handshake.");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            reader = new BufferedReader(new InputStreamReader(input));

            String text;
            do {
                text = reader.readLine();
                System.out.println(String.format("Received data: %s", text));
            } while (connection.isConnected());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Boolean connected() {
        return connection.isConnected();
    }
}
