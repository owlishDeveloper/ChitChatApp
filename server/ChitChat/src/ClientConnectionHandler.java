import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientConnectionHandler extends Thread {
    private final Socket connection;
    private final Gson gson = new Gson();
    private final ClientsDirectory clientsDirectory;

    public ClientConnectionHandler(Socket connection, ClientsDirectory clientsDirectory) {
        this.connection = connection;
        this.clientsDirectory = clientsDirectory;
    }

    @Override
    public void run() {
        System.out.println(String.format("Running connection on thread %s", Thread.currentThread().getName()));
        UUID clientId = UUID.fromString(Thread.currentThread().getName());

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

                // Add connection to the shared state
                clientsDirectory.addConnection(clientId, output);

                // Get the client ID and send it over
                System.out.println(String.format("Set new thread name: %s", Thread.currentThread().getName()));
                Message m = new Message(clientId);
                byte[] clientIdMessage = serialize(m);
                output.write(clientIdMessage, 0, clientIdMessage.length);

                // Deserializing messages
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

                    // Read and decode the message
                    byte[] decodedMessage = new byte[Math.toIntExact(messageLength)];
                    for (int i = 0; i < messageLength; i++) {
                        decodedMessage[i] = (byte) (input.read() ^ key[i & 0x3]);
                    }

                    // Deserialize
                    String messageString = new String(decodedMessage);
                    System.out.println(String.format("Received message: %s", messageString));
                    Message message = gson.fromJson(messageString, Message.class);

                    // Process
                    switch (message.type) {
                        case "username":
                            String newUsername = clientsDirectory.uniquifyAndChangeName(clientId, message.username);
                            message.setUsername(clientsDirectory.lookupUsername(clientId));
                            Set<String> users = clientsDirectory.getUserlist();
                            Message userlistMessage = new Message(users);
                            byte [] userlistMessageBytes = serialize(userlistMessage);
                            clientsDirectory.sendToAll(userlistMessageBytes);
                            break;
                        case "message":
                            message.setUsername(clientsDirectory.lookupUsername(clientId));
                            break;
                    }

                    // serialize the message back and send to all clients
                    byte[] sendBack = serialize(message);
                    clientsDirectory.sendToAll(sendBack);
                }

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper function to convert a WebSocket message into a Message object
     * Does not process encoded messages. For testing purposes only
     *
     * @param bytes - WebSocket message
     * @return Message object
     */
    private Message deserialize(byte[] bytes) {
        System.out.println(String.format("Start byte in dserialize is %s", (int)bytes[0]));

        long messageLength = bytes[1];
        int runningIndex = 2;
        if (messageLength == 126) {
            runningIndex = 4;
        } else if (messageLength == 127) {
            runningIndex = 6;
        }

        // Deserialize
        String messageString = new String(Arrays.copyOfRange(bytes, runningIndex, bytes.length));
        System.out.println(String.format("Test deserialize: %s", messageString));
        Message message = gson.fromJson(messageString, Message.class);

        return message;
    }

    /**
     * Helper function to convert a Message object into valid full WebSocket message
     * in the form of array of bytes ready to send over network.
     *
     * @param message - Message object to serialize
     * @return byte[] - WebSocket message ready to be sent
     */
    private byte[] serialize(Message message) {
        byte startByte = (byte) 129; // 1000 0001 - not a frame, text message

        byte[] payload = gson.toJson(message).getBytes(StandardCharsets.UTF_8);

        long messageLength = payload.length;
        // The MSB of the second byte is used for mask
        // Mask will be 0 here because I will not encode messages
        // See more here https://tools.ietf.org/html/rfc6455#section-5.2
        byte[] messageLength_bytes;
        if (messageLength <= 125) {
            messageLength_bytes = new byte[1];
            messageLength_bytes[0] = (byte) messageLength;
        } else if (messageLength >= 126 && messageLength <= 65535) {
            messageLength_bytes = new byte[3];
            messageLength_bytes[0] = (byte) 126;
            messageLength_bytes[2] = (byte) messageLength;
            messageLength >>>= 8;
            messageLength_bytes[1] = (byte) messageLength;
        } else {
            messageLength_bytes = new byte[9];
            messageLength_bytes[0] = (byte) 127;
            messageLength_bytes[8] = (byte) messageLength;
            messageLength >>>= 8;
            messageLength_bytes[7] = (byte) messageLength;
            messageLength >>>= 8;
            messageLength_bytes[6] = (byte) messageLength;
            messageLength >>>= 8;
            messageLength_bytes[5] = (byte) messageLength;
            messageLength >>>= 8;
            messageLength_bytes[4] = (byte) messageLength;
            messageLength >>>= 8;
            messageLength_bytes[3] = (byte) messageLength;
            messageLength >>>= 8;
            messageLength_bytes[2] = (byte) messageLength;
            messageLength >>>= 8;
            messageLength_bytes[1] = (byte) messageLength;
        }

        byte[] messageBytes = new byte[1 + messageLength_bytes.length + payload.length];

        messageBytes[0] = startByte;
        System.arraycopy(messageLength_bytes,
                0,
                messageBytes,
                1,
                messageLength_bytes.length);
        System.arraycopy(payload,
                0,
                messageBytes,
                messageLength_bytes.length + 1,
                payload.length);

        return messageBytes;
    }

    public Boolean connected() {
        return connection.isConnected();
    }
}
