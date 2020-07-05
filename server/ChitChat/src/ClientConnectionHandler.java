import com.google.gson.Gson;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientConnectionHandler implements Runnable {
    private final Socket connection;
    private final Gson gson = new Gson();
    private final ClientsDirectory clientsDirectory;
    private final UUID clientId;

    // The first byte is FIN, RSV1, RSV2, RSV3 + 4 bits of opcode.
    // I want FIN to be 1 because I cannot be bothered to deal with frames
    // RSV1, RSV2, RSV3 are always 0
    // I want opcode to be 0001 because this is a chat app and I expect text
    // So the first byte should be 1000 0001 otherwise disconnect
    private static final int MESSAGE_START_BYTE = 129;

    // The second byte is 1 bit to signify if the message is encoded (1) or not (0)
    // Clients always send encoded messages
    // The servers may or may not encode their messages. This server doesn't,
    // so this constant is only used in deserializing.
    private static final int MASK_BIT_IN_SECOND_BYTE = 128;
    // The rest 7 bits of the second byte tell the message length.
    // The maximum possible integer here is 127,
    // so if the message length is 125 or shorter, it's just here...
    private static final int MAXIMUM_ACTUAL_LENGTH_IN_SECOND_BYTE = 125;
    // if message length is a large integer that needs two bytes to be stored,
    // these 7 bytes are set to 126...
    private static final int TWO_BYTE_LENGTH_SIGNIFIER = 126;
    private static final int MAXIMUM_LENGTH_EXPRESSED_IN_TWO_BYTES = 65535;
    //...and if the message length is a really large integer that needs four bytes to be stored,
    // these 7 bytes are set to 127.
    private static final int FOUR_BYTE_LENGTH_SIGNIFIER = 127;

    public ClientConnectionHandler(Socket connection, UUID clientId, ClientsDirectory clientsDirectory) {
        this.connection = connection;
        this.clientsDirectory = clientsDirectory;
        this.clientId = clientId;
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

                // Get the client ID and send it over
                Message m = new Message(clientId);
                byte[] clientIdMessage = serialize(m);
                output.write(clientIdMessage, 0, clientIdMessage.length);

                // Handling messages
                while (!connection.isClosed()) {
                    System.out.println("Listening for messages...");

                    int startByte = input.read();
                    if (startByte != MESSAGE_START_BYTE) {
                        System.out.println(String.format("The message is not text. Closing connection and terminating thread %s", Thread.currentThread().getName()));
                        connection.shutdownInput();
                        connection.shutdownOutput();
                        connection.close();
                        cleanupStateAndNotifyAll();
                        return;
                    }

                    // Determine the length of the message (in bytes)
                    long messageLength = input.read() - MASK_BIT_IN_SECOND_BYTE;
                    if (messageLength == TWO_BYTE_LENGTH_SIGNIFIER) {
                        int messageLength_part1 = input.read();
                        int messageLength_part2 = input.read();
                        messageLength = messageLength_part1 << 8 | messageLength_part2;
                    } else if (messageLength == FOUR_BYTE_LENGTH_SIGNIFIER) {
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
                        case "login":
                            // Add connection to the shared state
                            String ensuredUniqueName = clientsDirectory.addConnection(clientId, output, message.username);
                            compareAndMaybeSendRejectuser(message.username, ensuredUniqueName, output);
                            message.username = ensuredUniqueName;
                            broadcastUserlist();
                            break;

                        case "username":
                            String oldName = clientsDirectory.lookupUsername(clientId);
                            String newUsername = clientsDirectory.uniquifyAndChangeName(clientId, message.username);
                            compareAndMaybeSendRejectuser(message.username, newUsername, output);
                            message.username = oldName;
                            message.newUsername = newUsername;
                            broadcastUserlist();
                            break;

                        case "message":
                            if (message.text.matches("\\s*")) { // don't send empty messages
                                continue;
                            }
                            message.username = clientsDirectory.lookupUsername(clientId);
                            break;
                    }

                    // serialize the message back and send to all clients
                    byte[] sendBack = serialize(message);
                    clientsDirectory.sendToAll(sendBack);
                }

                System.out.println(String.format("Connection is closed on thread %s. Cleaning up...", Thread.currentThread().getName()));
                connection.shutdownInput();
                connection.shutdownOutput();
                cleanupStateAndNotifyAll();

            } catch (Exception e) {
                System.out.println(String.format("Exception on thread %s. Cleaning up...", Thread.currentThread().getName()));
                e.printStackTrace();
                cleanupStateAndNotifyAll();
            }

        } catch (IOException e) {
            System.out.println(String.format("IOException on thread %s. Cleaning up...", Thread.currentThread().getName()));
            e.printStackTrace();
            cleanupStateAndNotifyAll();
        }
    }

    /**
     * Helper method to avoid repeating the same sequence of steps to compare two names,
     * and, if they are different, to warn the user that they chose a name that's already in use.
     *
     * @param oldUsername
     * @param newUsername
     * @param output
     * @throws IOException
     */
    private void compareAndMaybeSendRejectuser(String oldUsername, String newUsername, OutputStream output) throws IOException {
        if (!oldUsername.equals(newUsername)) {
            Message rejectUserMessage = new Message(newUsername, clientId);
            byte[] rejectUserMessageSerialized = serialize(rejectUserMessage);
            output.write(rejectUserMessageSerialized, 0, rejectUserMessageSerialized.length);
        }
    }

    /**
     * Helper method to avoid repeating the same sequence of steps to cleanup the shared state
     * and notify other clients that this user has left
     */
    private void cleanupStateAndNotifyAll() {
        String username = clientsDirectory.lookupUsername(clientId);

        clientsDirectory.cleanupConnection(clientId);

        // notify other clients
        broadcastUserlist();
        if (username != null) {
            Message userleftMessage = new Message(username);
            clientsDirectory.sendToAll(serialize(userleftMessage));
        }
    }

    /**
     * Helper method to avoid repeating the same sequence of steps to broadcast userlist
     */
    private void broadcastUserlist() {
        Set<String> users = clientsDirectory.getUserlist();
        Message userlistMessage = new Message(users);
        clientsDirectory.sendToAll(serialize(userlistMessage));
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
        if (messageLength == TWO_BYTE_LENGTH_SIGNIFIER) {
            runningIndex = 4;
        } else if (messageLength == FOUR_BYTE_LENGTH_SIGNIFIER) {
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
        byte startByte = (byte) MESSAGE_START_BYTE;

        byte[] payload = gson.toJson(message).getBytes(StandardCharsets.UTF_8);

        long messageLength = payload.length;
        // The MSB of the second byte is used for mask
        // Mask will be 0 here because I will not encode messages
        // See more here https://tools.ietf.org/html/rfc6455#section-5.2
        byte[] messageLength_bytes;
        if (messageLength <= MAXIMUM_ACTUAL_LENGTH_IN_SECOND_BYTE) {
            messageLength_bytes = new byte[1];
            messageLength_bytes[0] = (byte) messageLength;
        } else if (messageLength <= MAXIMUM_LENGTH_EXPRESSED_IN_TWO_BYTES) {
            messageLength_bytes = new byte[3];
            messageLength_bytes[0] = (byte) TWO_BYTE_LENGTH_SIGNIFIER;
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

// handshake and other serializing/desrializing stuff should go out to enable testing, especially deserialing
// state machine for the switch (first to be in a logged in state to accept other messages)
//
