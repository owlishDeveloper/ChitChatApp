import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class sharedState {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Lock readLock = lock.readLock();
    private Lock writeLock = lock.writeLock();

    public void addConnection(UUID id, OutputStream stream) {
        writeLock.lock();
        try {
        } finally {
            writeLock.unlock();
        }
    }

    public String uniqifyAndChangeName(String username) {
        writeLock.lock();
        try {
            // 1. ensure unique name - see if there's already this username
            // 2. if there isn't - write it and return username
            // 3. if there is - add a random int to it and goto 1
        } finally {
            writeLock.unlock();
        }
    }

    public void cleanupConnection(UUID id) {
        writeLock.lock();
        try {
            // remove output stream, remove id, remove username
        } finally {
            writeLock.unlock();
        }
    }

    // read lock
    public void sendToAll(byte[] message) {
        readLock.lock();
        try {
            // iterate through output streams and write bytes
        } finally {
            readLock.unlock();
        }
    }

    // read lock
    public Set<String> getUserlist() {
        readLock.lock();
        try {
            // get list of usernames
        } finally {
            readLock.unlock();
        }
    }

    // read lock
    public String lookupUsername(UUID id) {
        readLock.lock();
        try {
            // return username corresponding to this id
        } finally {
            readLock.unlock();
        }
    }
}
