import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server.java
 * Servidor de chat con Java NIO (sockets no bloqueantes).
 * Un único Selector administra todos los clientes y salas.
 */
public class Server {

    private static final int    BUFFER_SIZE = 4096;

    private final int              port;
    private       Selector         selector;
    private       ServerSocketChannel serverChannel;

    // Mapas de estado del servidor
    private final Map<SocketChannel, User>     channelUserMap = new ConcurrentHashMap<>();
    private final Map<SocketChannel, String>   pendingBuffers = new ConcurrentHashMap<>();
    private final Map<String, ChatRoom>        rooms          = new ConcurrentHashMap<>();
    // Buffer de escritura pendiente por canal
    private final Map<SocketChannel, Queue<String>> writeQueues = new ConcurrentHashMap<>();

    public Server(int port) {
        this.port = port;
    }

    // ---------------------------------------------------------------
    // Arranque
    // ---------------------------------------------------------------

    public void start() throws IOException {
        selector      = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("[SERVER] Escuchando en puerto " + port);

        while (true) {
            selector.select();
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();

            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                if (!key.isValid()) continue;

                if (key.isAcceptable()) handleAccept();
                else if (key.isReadable()) handleRead(key);
                else if (key.isWritable()) handleWrite(key);
            }
        }
    }

    // ---------------------------------------------------------------
    // Aceptar nueva conexión
    // ---------------------------------------------------------------

    private void handleAccept() throws IOException {
        SocketChannel client = serverChannel.accept();
        if (client == null) return;

        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
        pendingBuffers.put(client, "");
        writeQueues.put(client, new LinkedList<>());

        System.out.println("[SERVER] Nueva conexión: " + client.getRemoteAddress());
    }

    // ---------------------------------------------------------------
    // Leer datos de un canal
    // ---------------------------------------------------------------

    private void handleRead(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        int bytesRead;
        try {
            bytesRead = client.read(buffer);
        } catch (IOException e) {
            disconnect(client, key);
            return;
        }

        if (bytesRead == -1) {
            disconnect(client, key);
            return;
        }

        buffer.flip();
        String received = StandardCharsets.UTF_8.decode(buffer).toString();

        // Acumular en buffer parcial
        String accumulated = pendingBuffers.getOrDefault(client, "") + received;

        // Procesar líneas completas (terminadas en \n)
        String[] lines = accumulated.split("\n", -1);
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) processCommand(client, key, line);
        }
        // La última parte puede ser incompleta
        pendingBuffers.put(client, lines[lines.length - 1]);
    }

    // ---------------------------------------------------------------
    // Escribir datos pendientes
    // ---------------------------------------------------------------

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        Queue<String> queue  = writeQueues.get(client);

        if (queue == null || queue.isEmpty()) {
            // Nada que escribir, volver a esperar lecturas
            key.interestOps(SelectionKey.OP_READ);
            return;
        }

        while (!queue.isEmpty()) {
            String msg   = queue.peek();
            byte[] bytes = (msg + "\n").getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            try {
                client.write(buf);
            } catch (IOException e) {
                disconnect(client, key);
                return;
            }
            if (buf.hasRemaining()) break; // socket lleno, reintentar
            queue.poll();
        }

        if (queue.isEmpty()) {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    // ---------------------------------------------------------------
    // Procesar comando
    // ---------------------------------------------------------------

    private void processCommand(SocketChannel client, SelectionKey key, String raw) {
        String cmd  = Protocol.command(raw);
        String arg1 = Protocol.arg1(raw);
        String arg2 = Protocol.arg2(raw);

        System.out.println("[CMD] " + raw);

        switch (cmd) {

            // Primera línea tras conectar: registro de usuario
            case "REGISTER": {
                String username = arg1.trim();
                if (username.isEmpty()) {
                    send(client, key, Protocol.buildError("Nombre vacío"));
                    return;
                }
                // Verificar nombre duplicado
                for (User u : channelUserMap.values()) {
                    if (u.getUsername().equalsIgnoreCase(username)) {
                        send(client, key, Protocol.buildError("Nombre en uso"));
                        return;
                    }
                }
                User user = new User(username, client);
                channelUserMap.put(client, user);
                send(client, key, Protocol.buildOk("Bienvenido " + username));
                System.out.println("[SERVER] Usuario registrado: " + username);
                break;
            }

            case Protocol.CREATE: {
                User user = channelUserMap.get(client);
                if (user == null) { send(client, key, Protocol.buildError("No registrado")); return; }
                String roomName = arg1.trim();
                if (roomName.isEmpty()) { send(client, key, Protocol.buildError("Nombre de sala vacío")); return; }
                if (rooms.containsKey(roomName)) {
                    send(client, key, Protocol.buildError("Sala ya existe"));
                    return;
                }
                leaveCurrentRoom(client, key, user);
                ChatRoom room = new ChatRoom(roomName);
                rooms.put(roomName, room);
                room.addMember(user);
                user.setCurrentRoom(roomName);
                send(client, key, Protocol.buildOk("Sala creada: " + roomName));
                send(client, key, Protocol.buildRoom(roomName));
                broadcastInfo(room, user, Protocol.buildJoined(user.getUsername()));
                break;
            }

            case Protocol.JOIN: {
                User user = channelUserMap.get(client);
                if (user == null) { send(client, key, Protocol.buildError("No registrado")); return; }
                String roomName = arg1.trim();
                ChatRoom room   = rooms.get(roomName);
                if (room == null) { send(client, key, Protocol.buildError("Sala no existe")); return; }
                leaveCurrentRoom(client, key, user);
                room.addMember(user);
                user.setCurrentRoom(roomName);
                send(client, key, Protocol.buildOk("Unido a: " + roomName));
                send(client, key, Protocol.buildRoom(roomName));
                broadcastInfo(room, user, Protocol.buildJoined(user.getUsername()));
                break;
            }

            case Protocol.LIST: {
                StringBuilder sb = new StringBuilder();
                if (rooms.isEmpty()) {
                    sb.append("(sin salas)");
                } else {
                    for (ChatRoom r : rooms.values()) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(r.getName()).append(":").append(r.size());
                    }
                }
                send(client, key, Protocol.buildRooms(sb.toString()));
                break;
            }

            case Protocol.MESSAGE: {
                User user = channelUserMap.get(client);
                if (user == null) { send(client, key, Protocol.buildError("No registrado")); return; }
                if (!user.isInRoom()) { send(client, key, Protocol.buildError("No estás en una sala")); return; }
                ChatRoom room = rooms.get(user.getCurrentRoom());
                if (room == null) { send(client, key, Protocol.buildError("Sala no encontrada")); return; }
                String text = arg1 + (arg2.isEmpty() ? "" : Protocol.SEPARATOR + arg2);
                broadcastMessage(room, user, text, key);
                break;
            }

            case Protocol.LEAVE: {
                User user = channelUserMap.get(client);
                if (user == null) return;
                leaveCurrentRoom(client, key, user);
                send(client, key, Protocol.buildInfo("Saliste de la sala"));
                break;
            }

            case Protocol.EXIT: {
                disconnect(client, key);
                break;
            }

            default:
                send(client, key, Protocol.buildError("Comando desconocido: " + cmd));
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Envía un mensaje a un canal específico encolándolo. */
    private void send(SocketChannel client, SelectionKey key, String msg) {
        Queue<String> queue = writeQueues.get(client);
        if (queue == null) return;
        queue.add(msg);
        if (key != null && key.isValid()) {
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            selector.wakeup();
        }
    }

    /** Envía a todos los miembros de una sala excepto al emisor. */
    private void broadcastInfo(ChatRoom room, User except, String msg) {
        for (User member : room.getMembers()) {
            if (member != except) {
                SelectionKey k = member.getChannel().keyFor(selector);
                send(member.getChannel(), k, msg);
            }
        }
    }

    /** Envía un mensaje de chat a todos los miembros de la sala (incluido el emisor). */
    private void broadcastMessage(ChatRoom room, User sender, String text, SelectionKey senderKey) {
        String packed = Protocol.buildMsg(sender.getUsername(), text);
        for (User member : room.getMembers()) {
            SelectionKey k = member.getChannel() == sender.getChannel()
                    ? senderKey
                    : member.getChannel().keyFor(selector);
            send(member.getChannel(), k, packed);
        }
    }

    /** Quita al usuario de su sala actual. */
    private void leaveCurrentRoom(SocketChannel client, SelectionKey key, User user) {
        if (!user.isInRoom()) return;
        String roomName = user.getCurrentRoom();
        ChatRoom room   = rooms.get(roomName);
        if (room != null) {
            room.removeMember(user);
            broadcastInfo(room, user, Protocol.buildLeft(user.getUsername()));
            // Eliminar sala si quedó vacía
            if (room.isEmpty()) {
                rooms.remove(roomName);
                System.out.println("[SERVER] Sala eliminada (vacía): " + roomName);
            }
        }
        user.setCurrentRoom(null);
    }

    /** Desconecta a un cliente limpiamente. */
    private void disconnect(SocketChannel client, SelectionKey key) {
        User user = channelUserMap.get(client);
        if (user != null) {
            leaveCurrentRoom(client, key, user);
            System.out.println("[SERVER] Desconectado: " + user.getUsername());
        } else {
            System.out.println("[SERVER] Desconectado canal sin usuario: " + client);
        }
        channelUserMap.remove(client);
        pendingBuffers.remove(client);
        writeQueues.remove(client);
        if (key != null) key.cancel();
        try { client.close(); } catch (IOException ignored) {}
    }

    // ---------------------------------------------------------------
    // Main
    // ---------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        int port = 9090;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        new Server(port).start();
    }
}
