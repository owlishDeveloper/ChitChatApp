import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Scanner;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientConnectionHandler extends Thread {
    private final Socket connection;

    public ClientConnectionHandler(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        System.out.println(String.format("Running connection on thread %s", Thread.currentThread().getName()));

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

                // Generate a client ID and send it over
                UUID clientId = UUID.randomUUID();
                Thread.currentThread().setName(clientId.toString());
                System.out.println(String.format("Set new thread name: %s", Thread.currentThread().getName()));
                byte clientIdMessageStartByte = (byte) 129; // 1000 0001 - not a frame, text message
                byte[] clientIdMessagePayload = String.format("{\"type\":\"id\",\"id\":\"%s\"}", clientId.toString()).getBytes(StandardCharsets.UTF_8);
                long clientIdMessageLength = clientIdMessagePayload.length;
                byte[] clientIdMessageLength_bytes;
                if (clientIdMessageLength == 255) {
                    clientIdMessageLength_bytes = new byte[8];
                    clientIdMessageLength_bytes[7] = (byte) clientIdMessageLength;
                    clientIdMessageLength >>>= 8;
                    clientIdMessageLength_bytes[6] = (byte) clientIdMessageLength;
                    clientIdMessageLength >>>= 8;
                    clientIdMessageLength_bytes[5] = (byte) clientIdMessageLength;
                    clientIdMessageLength >>>= 8;
                    clientIdMessageLength_bytes[4] = (byte) clientIdMessageLength;
                    clientIdMessageLength >>>= 8;
                    clientIdMessageLength_bytes[3] = (byte) clientIdMessageLength;
                    clientIdMessageLength >>>= 8;
                    clientIdMessageLength_bytes[2] = (byte) clientIdMessageLength;
                    clientIdMessageLength >>>= 8;
                    clientIdMessageLength_bytes[1] = (byte) clientIdMessageLength;
                    clientIdMessageLength >>>= 8;
                    clientIdMessageLength_bytes[0] = (byte) clientIdMessageLength;
                } else if (clientIdMessageLength == 254) {
                    clientIdMessageLength_bytes = new byte[2];
                    clientIdMessageLength_bytes[1] = (byte) clientIdMessageLength;
                    clientIdMessageLength >>>= 8;
                    clientIdMessageLength_bytes[0] = (byte) clientIdMessageLength;
                } else {
                    clientIdMessageLength_bytes = new byte[1];
                    clientIdMessageLength_bytes[0] = (byte) clientIdMessageLength;
                }

                byte[] clientIdMessage = new byte[1 + clientIdMessageLength_bytes.length + clientIdMessagePayload.length];
                clientIdMessage[0] = clientIdMessageStartByte;
                System.arraycopy(clientIdMessageLength_bytes,
                        0,
                        clientIdMessage,
                        1,
                        clientIdMessageLength_bytes.length);
                System.arraycopy(clientIdMessagePayload,
                        0,
                        clientIdMessage,
                        clientIdMessageLength_bytes.length + 1,
                        clientIdMessagePayload.length);

                output.write(clientIdMessage, 0, clientIdMessage.length);

                // Reading messages
                while (true) {
                    System.out.println("Listening for messages...");

                    // The first byte is FIN, RSV1, RSV2, RSV3 + 4 bits of opcode.
                    // I want FIN to be 1 because I cannot be bothered to deal with frames
                    // RSV1, RSV2, RSV3 are always 0
                    // I want opcode to be 0001 because this is a chat app and I expect text
                    // So the first byte should be 1000 0001 otherwise disconnect
                    int startByte = input.read();
                    if (startByte != 129) {
                        connection.close();
                        System.out.println(String.format("The message is not text. Closing connection and terminating thread %s", Thread.currentThread().getName()));
                        return;
                    }

                    // Determine the length of the message (in bytes)
                    long messageLength = input.read() - 128;
                    if (messageLength == 126) {
                        int messageLength_part1 = input.read();
                        int messageLength_part2 = input.read();
                        messageLength = messageLength_part1 << 8 | messageLength_part2;
                    } else if (messageLength == 127) {
                        int messageLength_part1 = input.read();
                        int messageLength_part2 = input.read();
                        int messageLength_part3 = input.read();
                        int messageLength_part4 = input.read();
                        messageLength = messageLength_part1 & 0xFF;
                        messageLength <<= 8;
                        messageLength |= messageLength_part2 & 0xFF;
                        messageLength <<= 8;
                        messageLength |= messageLength_part3 & 0xFF;
                        messageLength <<= 8;
                        messageLength |= messageLength_part4 & 0xFF;
                    }

                    // Next 4 bytes are the masking key to decode the message
                    byte[] key = new byte[] { (byte)input.read(), (byte)input.read(), (byte)input.read(), (byte)input.read() };

                    // Get and decode the message
                    byte[] decodedMessage = new byte[Math.toIntExact(messageLength)];
                    for (int i = 0; i < messageLength; i++) {
                        decodedMessage[i] = (byte) (input.read() ^ key[i & 0x3]);
                    }

                    String message = new String(decodedMessage);

                    System.out.println(String.format("Received message: %s", message));
                }

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Boolean connected() {
        return connection.isConnected();
    }
}
