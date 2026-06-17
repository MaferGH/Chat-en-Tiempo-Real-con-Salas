import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Client.java
 * Capa de red del cliente usando Java NIO no bloqueante.
 * Corre en su propio hilo y notifica a la UI mediante callbacks.
 */
public class Client implements Runnable {

    private static final int BUFFER_SIZE = 4096;

    private final String   host;
    private final int      port;
    private final String   username;

    private Selector      selector;
    private SocketChannel channel;

    private final Queue<String> writeQueue   = new LinkedList<>();
    private       String        partialBuffer = "";

    // Callbacks hacia la interfaz gráfica
    private Consumer<String> onMessage;    // MSG recibido
    private Consumer<String> onInfo;       // INFO / JOINED / LEFT
    private Consumer<String> onRooms;      // respuesta LIST
    private Consumer<String> onError;      // ERROR del servidor
    private Consumer<String> onOk;         // confirmaciones OK
    private Consumer<String> onRoom;       // sala activa confirmada (ROOM)
    private Runnable         onDisconnect;

    private volatile boolean running = false;

    public Client(String host, int port, String username) {
        this.host     = host;
        this.port     = port;
        this.username = username;
    }

    // ---- Setters de callbacks ----
    public void setOnMessage   (Consumer<String> cb) { this.onMessage    = cb; }
    public void setOnInfo      (Consumer<String> cb) { this.onInfo       = cb; }
    public void setOnRooms     (Consumer<String> cb) { this.onRooms      = cb; }
    public void setOnError     (Consumer<String> cb) { this.onError      = cb; }
    public void setOnOk        (Consumer<String> cb) { this.onOk         = cb; }
    public void setOnRoom      (Consumer<String> cb) { this.onRoom       = cb; }
    public void setOnDisconnect(Runnable         cb) { this.onDisconnect = cb; }

    // ---------------------------------------------------------------
    // Conexión
    // ---------------------------------------------------------------

    public void connect() throws IOException {
        selector = Selector.open();
        channel  = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(host, port));
        channel.register(selector, SelectionKey.OP_CONNECT);
        running = true;
    }

    @Override
    public void run() {
        try {
            while (running) {
                selector.select(500);
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) continue;
                    if (key.isConnectable()) handleConnect(key);
                    else if (key.isReadable()) handleRead(key);
                    else if (key.isWritable()) handleWrite(key);
                }
            }
        } catch (IOException e) {
            if (running) notifyDisconnect();
        } finally {
            close();
        }
    }

    // ---------------------------------------------------------------
    // Finalizar conexión
    // ---------------------------------------------------------------

    private void handleConnect(SelectionKey key) throws IOException {
        if (channel.finishConnect()) {
            key.interestOps(SelectionKey.OP_READ);
            // Registrar nombre de usuario
            enqueue("REGISTER" + Protocol.SEPARATOR + username);
            flushQueue(key);
        }
    }

    // ---------------------------------------------------------------
    // Lectura
    // ---------------------------------------------------------------

    private void handleRead(SelectionKey key) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
        int n = channel.read(buf);
        if (n == -1) { notifyDisconnect(); running = false; return; }
        buf.flip();
        String received = StandardCharsets.UTF_8.decode(buf).toString();
        partialBuffer += received;

        String[] lines = partialBuffer.split("\n", -1);
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) dispatch(line);
        }
        partialBuffer = lines[lines.length - 1];
    }

    // ---------------------------------------------------------------
    // Escritura
    // ---------------------------------------------------------------

    private void handleWrite(SelectionKey key) throws IOException {
        flushQueue(key);
    }

    private void flushQueue(SelectionKey key) throws IOException {
        while (!writeQueue.isEmpty()) {
            String msg   = writeQueue.peek();
            byte[] bytes = (msg + "\n").getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            channel.write(buf);
            if (buf.hasRemaining()) {
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                selector.wakeup();
                return;
            }
            writeQueue.poll();
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    // ---------------------------------------------------------------
    // Despachar respuestas del servidor
    // ---------------------------------------------------------------

    private void dispatch(String raw) {
        String cmd  = Protocol.command(raw);
        String arg1 = Protocol.arg1(raw);
        String arg2 = Protocol.arg2(raw);

        switch (cmd) {
            case Protocol.MSG:
                if (onMessage != null) onMessage.accept(arg1 + ": " + arg2);
                break;
            case Protocol.INFO:
                if (onInfo != null) onInfo.accept("ℹ️ " + arg1);
                break;
            case Protocol.USER_JOINED:
                if (onInfo != null) onInfo.accept("➡️ " + arg1 + " se unió a la sala");
                break;
            case Protocol.USER_LEFT:
                if (onInfo != null) onInfo.accept("⬅️ " + arg1 + " abandonó la sala");
                break;
            case Protocol.ROOMS:
                if (onRooms != null) onRooms.accept(arg1);
                break;
            case Protocol.ROOM:
                if (onRoom != null) onRoom.accept(arg1);
                break;
            case Protocol.ERROR:
                if (onError != null) onError.accept(arg1);
                break;
            case Protocol.OK:
                if (onOk != null) onOk.accept(arg1);
                break;
            default:
                if (onInfo != null) onInfo.accept(raw);
        }
    }

    // ---------------------------------------------------------------
    // API pública para enviar comandos
    // ---------------------------------------------------------------

    public void sendCreate(String room)   { sendRaw(Protocol.buildCreate(room)); }
    public void sendJoin(String room)     { sendRaw(Protocol.buildJoin(room)); }
    public void sendList()                { sendRaw(Protocol.buildList()); }
    public void sendMessage(String text)  { sendRaw(Protocol.buildMessage(text)); }
    public void sendLeave()               { sendRaw(Protocol.buildLeave()); }
    public void sendExit()                { sendRaw(Protocol.buildExit()); }

    private void sendRaw(String msg) {
        enqueue(msg);
        if (selector != null) selector.wakeup();
        // Marcar canal como escribible
        if (channel != null) {
            SelectionKey key = channel.keyFor(selector);
            if (key != null && key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                selector.wakeup();
            }
        }
    }

    private synchronized void enqueue(String msg) {
        writeQueue.add(msg);
    }

    // ---------------------------------------------------------------
    // Cierre
    // ---------------------------------------------------------------

    public void disconnect() {
        running = false;
        if (selector != null) selector.wakeup();
    }

    private void close() {
        try { if (channel  != null) channel.close();  } catch (IOException ignored) {}
        try { if (selector != null) selector.close();  } catch (IOException ignored) {}
    }

    private void notifyDisconnect() {
        if (onDisconnect != null) onDisconnect.run();
    }
}
