import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

class ClientHandler implements Runnable {
    // 'Runnable' allows clients to connect on own threads

    private final Socket clientSocket;
    private final String username;
    private final LUConnectServer server;
    private PrintWriter writer;

    // Constructor
    public ClientHandler(Socket socket, String username, LUConnectServer server) throws IOException {
        this.clientSocket = socket;
        this.username = username;
        this.server = server;
        this.writer = new PrintWriter(socket.getOutputStream(), true);
    }

    public String getUsername() {
        return username;
    }

    // Send message directly to client through socket
    public void sendMessage(String message) {
        writer.println(message);
    }

    // Main method of 'ClientHandler'
    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            // Send Connected to client so they know they're connected
            writer.println("CONNECTED");

            // Continuously read incoming messages

            String message;
            while ((message = reader.readLine()) != null) {
                // Chat message
                if (message.startsWith("MSG:")) {
                    // Format: MSG:recipient:message
                    String[] parts = message.split(":", 3);
                    if (parts.length == 3) {
                        String recipient = parts[1];
                        String content = parts[2];

                        server.broadcastMessage(username, content, recipient);
                    }
                // Request online user list
                } else if (message.equals("USERS")) {
                    server.sendUserList(this);
                // Disconnect
                } else if (message.equals("QUIT")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Remove client
            server.removeClient(username);
        }
    }
}