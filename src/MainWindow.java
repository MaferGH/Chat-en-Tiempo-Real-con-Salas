import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * MainWindow.java
 * Ventana principal del chat — interfaz simple, sin colores personalizados.
 */
public class MainWindow extends JFrame {

    // Emojis de acceso rápido
    private static final String[] EMOJIS = {
        "😀", "😂", "❤️", "👍", "😎", "🎉", "👋", "🔥", "✅", "😢"
    };

    // Componentes
    private JLabel     lblInfo;
    private JTextArea  chatArea;
    private JTextField tfMessage;
    private JButton    btnSend;
    private JButton    btnCreate;
    private JButton    btnJoin;
    private JButton    btnList;
    private JButton    btnLeave;
    private JButton    btnExit;

    private final Client client;
    private final String username;
    private       String currentRoom = null;

    public MainWindow(Client client, String username) {
        super("Chat NIO  —  " + username);
        this.client   = client;
        this.username = username;
        buildUI();
        wireCallbacks();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { exitApp(); }
        });
        setSize(660, 520);
        setMinimumSize(new Dimension(520, 420));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ---------------------------------------------------------------
    // UI
    // ---------------------------------------------------------------

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(new EmptyBorder(6, 8, 8, 8));

        root.add(buildInfoBar(),  BorderLayout.NORTH);
        root.add(buildChatArea(), BorderLayout.CENTER);
        root.add(buildBottom(),   BorderLayout.SOUTH);

        setContentPane(root);
    }

    /** Barra de información: usuario y sala actual. */
    private JPanel buildInfoBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                new EmptyBorder(4, 8, 4, 8)));

        lblInfo = new JLabel("Usuario: " + username + "    |    Sala: —");
        lblInfo.setFont(lblInfo.getFont().deriveFont(Font.PLAIN, 12f));
        bar.add(lblInfo, BorderLayout.WEST);
        return bar;
    }

    /** Área de mensajes con scroll. */
    private JScrollPane buildChatArea() {
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        chatArea.setMargin(new Insets(6, 8, 6, 8));

        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Conversación",
                TitledBorder.LEFT,
                TitledBorder.TOP));
        return scroll;
    }

    /** Panel inferior: emojis + entrada de texto + botones de acción. */
    private JPanel buildBottom() {
        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.setBorder(new EmptyBorder(6, 0, 0, 0));

        // -- Fila de emojis --
        JPanel emojiRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        emojiRow.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Emojis",
                TitledBorder.LEFT,
                TitledBorder.TOP));
        for (String emoji : EMOJIS) {
            JButton eb = new JButton(emoji);
            eb.setMargin(new Insets(2, 4, 2, 4));
            eb.setFocusPainted(false);
            eb.setToolTipText(emoji);
            eb.addActionListener(e -> insertEmoji(emoji));
            emojiRow.add(eb);
        }
        bottom.add(emojiRow, BorderLayout.NORTH);

        // -- Fila de entrada de texto --
        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        tfMessage = new JTextField();
        tfMessage.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        btnSend = new JButton("Enviar");
        inputRow.add(tfMessage, BorderLayout.CENTER);
        inputRow.add(btnSend,   BorderLayout.EAST);
        bottom.add(inputRow, BorderLayout.CENTER);

        // -- Fila de botones de acción --
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        btnCreate = new JButton("Crear Sala");
        btnJoin   = new JButton("Unirse a Sala");
        btnList   = new JButton("Ver Salas");
        btnLeave  = new JButton("Salir de Sala");
        btnExit   = new JButton("Desconectar");

        actionRow.add(btnCreate);
        actionRow.add(btnJoin);
        actionRow.add(btnList);
        actionRow.add(btnLeave);
        actionRow.add(new JSeparator(SwingConstants.VERTICAL));
        actionRow.add(btnExit);

        bottom.add(actionRow, BorderLayout.SOUTH);
        return bottom;
    }

    // ---------------------------------------------------------------
    // Callbacks
    // ---------------------------------------------------------------

    private void wireCallbacks() {
        client.setOnMessage(msg -> SwingUtilities.invokeLater(() -> appendChat(msg)));

        client.setOnInfo(info -> SwingUtilities.invokeLater(() -> appendSystem(info)));

        client.setOnError(err -> SwingUtilities.invokeLater(() -> {
            appendSystem("[Error] " + err);
            JOptionPane.showMessageDialog(this, err, "Error", JOptionPane.WARNING_MESSAGE);
        }));

        client.setOnOk(ok -> SwingUtilities.invokeLater(() -> appendSystem("[OK] " + ok)));

        // ROOM llega justo después del OK al crear o unirse: contiene el nombre exacto de la sala
        client.setOnRoom(room -> SwingUtilities.invokeLater(() -> {
            currentRoom = room.trim();
            updateInfoBar();
        }));

        client.setOnRooms(rooms -> SwingUtilities.invokeLater(() -> showRoomsList(rooms)));

        client.setOnDisconnect(() -> SwingUtilities.invokeLater(() -> {
            appendSystem("[Sistema] Desconectado del servidor.");
            disableInputs();
        }));

        // Botones
        btnSend  .addActionListener(e -> sendMessage());
        tfMessage.addActionListener(e -> sendMessage());

        btnCreate.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this,
                    "Nombre de la sala:", "Crear Sala", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) client.sendCreate(name.trim());
        });

        btnJoin.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this,
                    "Nombre de la sala:", "Unirse a Sala", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) client.sendJoin(name.trim());
        });

        btnList .addActionListener(e -> client.sendList());

        btnLeave.addActionListener(e -> {
            client.sendLeave();
            currentRoom = null;
            updateInfoBar();
        });

        btnExit.addActionListener(e -> exitApp());
    }

    // ---------------------------------------------------------------
    // Acciones
    // ---------------------------------------------------------------

    private void sendMessage() {
        String text = tfMessage.getText().trim();
        if (text.isEmpty()) return;
        if (currentRoom == null) {
            JOptionPane.showMessageDialog(this,
                    "Únete a una sala primero.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        client.sendMessage(text);
        tfMessage.setText("");
        tfMessage.requestFocus();
    }

    private void insertEmoji(String emoji) {
        tfMessage.setText(tfMessage.getText() + emoji);
        tfMessage.requestFocus();
    }

    private void exitApp() {
        int r = JOptionPane.showConfirmDialog(this,
                "¿Desconectarse y salir?", "Salir", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            client.sendExit();
            client.disconnect();
            dispose();
            System.exit(0);
        }
    }

    // ---------------------------------------------------------------
    // Helpers de UI
    // ---------------------------------------------------------------

    private void appendChat(String msg) {
        // msg: "sender: texto"
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        chatArea.append("[" + time + "] " + msg + "\n");
        scrollToBottom();
    }

    private void appendSystem(String info) {
        chatArea.append("  " + info + "\n");
        scrollToBottom();
    }

    private void showRoomsList(String rooms) {
        if (rooms == null || rooms.isBlank() || rooms.equals("(sin salas)")) {
            JOptionPane.showMessageDialog(this,
                    "No hay salas disponibles.", "Salas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        StringBuilder sb = new StringBuilder("Salas disponibles:\n\n");
        for (String entry : rooms.split(",")) {
            String[] parts = entry.split(":");
            sb.append("  • ").append(parts[0])
              .append("  (").append(parts.length > 1 ? parts[1] : "?").append(" usuario(s))\n");
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "Salas", JOptionPane.PLAIN_MESSAGE);
    }

    private void updateInfoBar() {
        lblInfo.setText("Usuario: " + username + "    |    Sala: " +
                (currentRoom != null ? currentRoom : "—"));
    }

    private void scrollToBottom() {
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void disableInputs() {
        tfMessage.setEnabled(false);
        btnSend  .setEnabled(false);
        btnCreate.setEnabled(false);
        btnJoin  .setEnabled(false);
        btnList  .setEnabled(false);
        btnLeave .setEnabled(false);
    }
}
