import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClientsDirectory {
    private HashMap<UUID, ClientInfo> data = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Lock readLock = lock.readLock();
    private Lock writeLock = lock.writeLock();

    public void addConnection(UUID id, OutputStream stream) {
        ClientInfo info = new ClientInfo(stream);
        writeLock.lock();
        try {
            data.put(id, info);
        } finally {
            writeLock.unlock();
        }
    }

    public String uniquifyAndChangeName(UUID id, String newUsername) {
        int i = 1;
        writeLock.lock();
        try {
            Set<String> usernames = getUserlist();
            while (!usernames.add(newUsername)) {
                newUsername = newUsername.concat(String.valueOf(i));
                i++;
            }
            ClientInfo info = data.get(id);
            info.username = newUsername;

            return newUsername;
        } finally {
            writeLock.unlock();
        }
    }

    public void cleanupConnection(UUID id) {
        writeLock.lock();
        try {
            data.remove(id);
        } finally {
            writeLock.unlock();
        }
    }

    public void cleanupConnections(Collection<UUID> ids) {
        writeLock.lock();
        try {
            ids.forEach(id -> data.remove(id));
        } finally {
            writeLock.unlock();
        }
    }

    public void sendToAll(byte[] message) {
        Collection<UUID> closedSockets = new HashSet<>();
        readLock.lock();
        try {
            data.forEach((k, v) -> {
                try {
                    v.webSocketOutput.write(message, 0, message.length);
                } catch (IOException e) {
                    if (e.getMessage().equals("Socket closed")) {
                        closedSockets.add(k);
                    } else {
                        e.printStackTrace();
                    }
                }
            });
        } finally {
            readLock.unlock();
            if (!closedSockets.isEmpty()) {
                cleanupConnections(closedSockets);
            }
        }
    }

    public Set<String> getUserlist() {
        Set<String> usernames = new HashSet<>();
        readLock.lock();
        try {
            data.forEach((k, v) -> usernames.add(v.username));
            return usernames;
        } finally {
            readLock.unlock();
        }
    }

    public String lookupUsername(UUID id) {
        readLock.lock();
        try {
            return data.get(id).username;
        } finally {
            readLock.unlock();
        }
    }

    private static class ClientInfo {
        private String username;
        private final OutputStream webSocketOutput;

        private ClientInfo(OutputStream webSocketOutput) {
            this.webSocketOutput = webSocketOutput;
        }
    }
}
