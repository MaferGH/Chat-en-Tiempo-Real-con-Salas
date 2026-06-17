import java.nio.channels.SocketChannel;

/**
 * User.java
 * Representa a un usuario conectado en el servidor.
 */
public class User {

    private final String        username;
    private final SocketChannel channel;
    private       String        currentRoom;   // null si no está en ninguna sala

    public User(String username, SocketChannel channel) {
        this.username    = username;
        this.channel     = channel;
        this.currentRoom = null;
    }

    public String        getUsername()    { return username; }
    public SocketChannel getChannel()     { return channel; }
    public String        getCurrentRoom() { return currentRoom; }

    public void setCurrentRoom(String room) { this.currentRoom = room; }

    public boolean isInRoom() { return currentRoom != null; }

    @Override
    public String toString() { return username; }
}
