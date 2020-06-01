import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.WebSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {
    public static void main(String[] args) throws NoSuchAlgorithmException {

        try (ServerSocket server = new ServerSocket(80)) {

            System.out.println(String.format("Server started on %s", server.getLocalPort()));

            Socket connection = server.accept(); // block the current thread until the connection is made
            System.out.println("A client connected.");

            InputStream input = connection.getInputStream();
            OutputStream output = connection.getOutputStream();

            try (Scanner scanner = new Scanner(input, "UTF-8")) {
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
                                    (match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")))
                            + "\r\n\r\n").getBytes("UTF-8");
                    output.write(response, 0, response.length);
                }
                System.out.println("Performed WebSocket handshake.");

                do {
                    data = scanner.nextLine();
                    System.out.println(String.format("Received data: %s", data));
                } while (true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
