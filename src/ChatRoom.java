import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ChatRoom.java
 * Representa una sala de chat con su nombre y conjunto de usuarios.
 */
public class ChatRoom {

    private final String    name;
    private final Set<User> members;

    public ChatRoom(String name) {
        this.name    = name;
        this.members = new HashSet<>();
    }

    public String getName() { return name; }

    public synchronized void addMember(User user) {
        members.add(user);
    }

    public synchronized void removeMember(User user) {
        members.remove(user);
    }

    public synchronized boolean hasMember(User user) {
        return members.contains(user);
    }

    public synchronized boolean isEmpty() {
        return members.isEmpty();
    }

    public synchronized int size() {
        return members.size();
    }

    /** Devuelve una copia inmutable del conjunto de miembros. */
    public synchronized Set<User> getMembers() {
        return Collections.unmodifiableSet(new HashSet<>(members));
    }

    @Override
    public String toString() { return name + "(" + size() + ")"; }
}
