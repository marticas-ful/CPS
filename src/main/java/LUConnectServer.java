import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.text.SimpleDateFormat;

public class LUConnectServer {
    private static final int PORT = 9876;
    private static final int MAX_CLIENTS = 3;

    private static DBConnection dbConnection;

    // Keep track of active clients
    private final Map<String, ClientHandler> activeClients = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        dbConnection = DBConnection.getInstance();
        dbConnection.establishConnection();
        new LUConnectServer().startServer();
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleNewConnection(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleNewConnection(Socket clientSocket) {
        new Thread(() -> {
            try {
                // Get the username of the chatter
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);

                writer.println("WELCOME");
                String username = reader.readLine();

                // Start new client handler to handle current client
                ClientHandler handler = new ClientHandler(clientSocket, username, this);
                activeClients.put(username, handler);
                new Thread(handler).start();

                // Display to other users that a new person has entered chat
                broadcastServerMessage(username + " has joined the chat.");

                // Update 'online' list for clients -> shows all other online users
                updateAllUserLists();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Method to format messages in GUI
    public void broadcastMessage(String sender, String message, String recipient) {
        // Store timestamp to later add to database
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String formattedMessage = "[" + timestamp + "] " + sender + ": " + message;

        // Group message
        if ("ALL".equals(recipient)) {
            synchronized (activeClients) {
                for (Map.Entry<String, ClientHandler> entry : activeClients.entrySet()) {
                    ClientHandler handler = entry.getValue();
                    if (!handler.getUsername().equals(sender)) {
                        handler.sendMessage("GROUP:" + formattedMessage);
                    }
                }
            }

        // Private message
        } else {
            // Private message
            ClientHandler recipientHandler = activeClients.get(recipient);
            if (recipientHandler != null) {
                recipientHandler.sendMessage("PRIVATE:" + formattedMessage);
            }
        }
    }

    // Show messages sent by server, e.g., "user has joined the chat"
    public void broadcastServerMessage(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String formattedMessage = "[" + timestamp + "] SERVER: " + message;

        synchronized (activeClients) {
            for (ClientHandler handler : activeClients.values()) {
                handler.sendMessage("SERVER:" + formattedMessage);
            }
        }
    }

    // Sending method so GUI can display active users
    public void sendUserList(ClientHandler requester) {
        StringBuilder userList = new StringBuilder("USERS:");
        synchronized (activeClients) {
            for (String username : activeClients.keySet()) {
                userList.append(username).append(",");
            }
        }

        requester.sendMessage(userList.toString());
    }

    // Method to update everyone's user lists if new user joins or user leaves
    public void updateAllUserLists() {
        // Iterate through the set of active clients
        for (Map.Entry<String, ClientHandler> entry : activeClients.entrySet()) {
            sendUserList(entry.getValue());
        }
    }

    // Remove user who left from lists
    public void removeClient(String username) {
        activeClients.remove(username);
        broadcastServerMessage(username + " has left the chat.");
        updateAllUserLists();

    }
}