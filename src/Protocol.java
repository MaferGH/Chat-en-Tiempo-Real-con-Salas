/**
 * Protocol.java
 * Define los comandos y formatos del protocolo de comunicación cliente-servidor.
 */
public class Protocol {

    // Comandos del cliente al servidor
    public static final String CREATE  = "CREATE";
    public static final String JOIN    = "JOIN";
    public static final String LIST    = "LIST";
    public static final String MESSAGE = "MESSAGE";
    public static final String LEAVE   = "LEAVE";
    public static final String EXIT    = "EXIT";

    // Respuestas / eventos del servidor al cliente
    public static final String OK          = "OK";
    public static final String ERROR       = "ERROR";
    public static final String ROOMS       = "ROOMS";
    public static final String MSG         = "MSG";
    public static final String INFO        = "INFO";
    public static final String USER_JOINED = "JOINED";
    public static final String USER_LEFT   = "LEFT";
    /** Confirma al cliente en qué sala quedó tras CREATE o JOIN. */
    public static final String ROOM        = "ROOM";

    public static final String SEPARATOR = "|";
    public static final String SEPARATOR_REGEX = "\\|";

    // ---- Builders (cliente → servidor) ----

    public static String buildCreate(String roomName) {
        return CREATE + SEPARATOR + roomName;
    }

    public static String buildJoin(String roomName) {
        return JOIN + SEPARATOR + roomName;
    }

    public static String buildList() {
        return LIST;
    }

    public static String buildMessage(String text) {
        return MESSAGE + SEPARATOR + text;
    }

    public static String buildLeave() {
        return LEAVE;
    }

    public static String buildExit() {
        return EXIT;
    }

    // ---- Builders (servidor → cliente) ----

    public static String buildOk(String detail) {
        return OK + SEPARATOR + detail;
    }

    public static String buildError(String detail) {
        return ERROR + SEPARATOR + detail;
    }

    public static String buildRooms(String roomList) {
        return ROOMS + SEPARATOR + roomList;
    }

    public static String buildMsg(String sender, String text) {
        return MSG + SEPARATOR + sender + SEPARATOR + text;
    }

    public static String buildInfo(String text) {
        return INFO + SEPARATOR + text;
    }

    public static String buildRoom(String roomName) {
        return ROOM + SEPARATOR + roomName;
    }

    public static String buildJoined(String username) {
        return USER_JOINED + SEPARATOR + username;
    }

    public static String buildLeft(String username) {
        return USER_LEFT + SEPARATOR + username;
    }

    // ---- Parsers ----

    public static String[] parse(String raw) {
        if (raw == null) return new String[0];
        return raw.split(SEPARATOR_REGEX, 3);
    }

    public static String command(String raw) {
        String[] parts = parse(raw);
        return parts.length > 0 ? parts[0] : "";
    }

    public static String arg1(String raw) {
        String[] parts = parse(raw);
        return parts.length > 1 ? parts[1] : "";
    }

    public static String arg2(String raw) {
        String[] parts = parse(raw);
        return parts.length > 2 ? parts[2] : "";
    }
}
