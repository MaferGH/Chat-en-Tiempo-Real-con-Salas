import javax.swing.*;
import java.io.IOException;

/**
 * ChatApp.java
 * Punto de entrada. Muestra la ventana de login y arranca el cliente.
 * También puede arrancar el servidor si se pasa --server como argumento.
 */
public class ChatApp {

    public static void main(String[] args) throws Exception {

        // Modo servidor: java ChatApp --server [puerto]
        if (args.length > 0 && args[0].equals("--server")) {
            int port = 9090;
            if (args.length > 1) {
                try { port = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
            }
            System.out.println("[SERVER] Iniciando en puerto " + port + " ...");
            new Server(port).start();
            return;
        }

        // Modo cliente con GUI
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        SwingUtilities.invokeLater(() -> {
            LoginWindow login = new LoginWindow(null);
            login.setVisible(true);

            if (!login.isConfirmed()) return;

            String username = login.getUsername();
            String host     = login.getHost();
            int    port     = login.getPort();

            Client client = new Client(host, port, username);
            try {
                client.connect();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "No se pudo conectar a " + host + ":" + port + "\n" + e.getMessage(),
                        "Error de conexión", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Hilo de red
            Thread networkThread = new Thread(client, "NIO-Client");
            networkThread.setDaemon(true);
            networkThread.start();

            // Ventana principal
            new MainWindow(client, username);
        });
    }
}
