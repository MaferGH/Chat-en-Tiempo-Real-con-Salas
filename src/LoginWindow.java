import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * LoginWindow.java
 * Ventana de inicio de sesión — interfaz simple, sin colores personalizados.
 */
public class LoginWindow extends JDialog {

    private JTextField tfUsername;
    private JTextField tfHost;
    private JTextField tfPort;
    private JButton    btnConnect;
    private JButton    btnCancel;

    private String  resultUsername;
    private String  resultHost;
    private int     resultPort;
    private boolean confirmed = false;

    public LoginWindow(Frame parent) {
        super(parent, "Conectar al servidor", true);
        buildUI();
        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(new EmptyBorder(16, 20, 12, 20));

        // Título
        JLabel title = new JLabel("Chat NIO", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setBorder(new EmptyBorder(0, 0, 8, 0));
        main.add(title, BorderLayout.NORTH);

        // Formulario
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets  = new Insets(4, 4, 4, 4);
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.anchor  = GridBagConstraints.WEST;

        addRow(form, gc, 0, "Usuario:",         tfUsername = new JTextField(16));
        addRow(form, gc, 1, "IP del servidor:", tfHost     = new JTextField("127.0.0.1"));
        addRow(form, gc, 2, "Puerto:",           tfPort     = new JTextField("9090"));

        main.add(form, BorderLayout.CENTER);

        // Botones
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnCancel  = new JButton("Cancelar");
        btnConnect = new JButton("Conectar");
        btnPanel.add(btnCancel);
        btnPanel.add(btnConnect);
        main.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(main);

        btnConnect.addActionListener(e -> tryConnect());
        btnCancel .addActionListener(e -> { confirmed = false; dispose(); });
        getRootPane().setDefaultButton(btnConnect);
        getRootPane().registerKeyboardAction(
                e -> { confirmed = false; dispose(); },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void addRow(JPanel p, GridBagConstraints gc, int row, String label, JTextField field) {
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0;
        p.add(new JLabel(label), gc);
        gc.gridx = 1; gc.weightx = 1.0;
        p.add(field, gc);
    }

    private void tryConnect() {
        String user = tfUsername.getText().trim();
        String host = tfHost.getText().trim();
        if (user.isEmpty()) { error("Escribe un nombre de usuario."); tfUsername.requestFocus(); return; }
        if (host.isEmpty()) { error("Escribe la IP del servidor.");   tfHost.requestFocus();     return; }
        int port;
        try {
            port = Integer.parseInt(tfPort.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            error("Puerto inválido (1-65535).");
            tfPort.requestFocus();
            return;
        }
        resultUsername = user;
        resultHost     = host;
        resultPort     = port;
        confirmed      = true;
        dispose();
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public boolean isConfirmed() { return confirmed; }
    public String  getUsername() { return resultUsername; }
    public String  getHost()     { return resultHost; }
    public int     getPort()     { return resultPort; }
}
