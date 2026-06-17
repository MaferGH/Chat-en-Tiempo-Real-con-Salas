import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Message.java
 * Representa un mensaje de chat con remitente, contenido y hora.
 */
public class Message {

    private final String sender;
    private final String content;
    private final LocalTime timestamp;

    public Message(String sender, String content) {
        this.sender    = sender;
        this.content   = content;
        this.timestamp = LocalTime.now();
    }

    public String getSender()  { return sender; }
    public String getContent() { return content; }
    public LocalTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        return String.format("[%s] %s: %s", timestamp.format(fmt), sender, content);
    }
}
