import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Timer;
import java.util.TimerTask;

public class LUConnectServer {
    private static final int PORT = 9876;
    private static final int MAX_CLIENTS = 3;

    private static DBConnection dbConnection;

    private final Map<String, ClientHandler> activeClients = Collections.synchronizedMap(new HashMap<>());  // Keep track of active clients
    private final List<WaitingClient> waitingQueue = Collections.synchronizedList(new ArrayList<>());     // Keep track of clients in waitlist
    private final SimpleSemaphore connectionSemaphore = new SimpleSemaphore(MAX_CLIENTS);   // Semaphore
    private final Timer timer = new Timer();    // Timer to update wait times

    public static void main(String[] args) {
        dbConnection = DBConnection.getInstance();
        dbConnection.establishConnection();
        new LUConnectServer().startServer();
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Chat Server started on port " + PORT);

            // Start the timer to update wait times every 10 seconds
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    updateWaitTimes();
                }
            }, 0, 10000);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleNewConnection(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            timer.cancel();
        }
    }

    // Handle incoming new connection
    private void handleNewConnection(Socket clientSocket) {
        new Thread(() -> {
            try {
                // Get the username and password of chatter
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);

                writer.println("WELCOME");
                String credentials = reader.readLine(); // username : password
                String[] parts = credentials.split(":", 2);

                if (parts.length != 2) {
                    writer.println("ERROR:Invalid credentials format");
                    clientSocket.close();
                    return;
                }

                String username = parts[0];
                String password = parts[1];

                // Check if username is already active as another client
                if (activeClients.containsKey(username)) {
                    writer.println("ERROR:User already logged in");
                    clientSocket.close();
                    return;
                }

                // Authenticate user in database
                if (!dbConnection.authenticateUser(username, password)) {
                    writer.println("ERROR:Invalid username or password");
                    clientSocket.close();
                    return;
                }

                // Create wait list UI
                WaitingClient waitingClient = new WaitingClient(username, clientSocket, System.currentTimeMillis());

                // Basically, all clients put in waitlist, but if server not full, they're put through immediately
                if (connectionSemaphore.tryAcquire()) {
                    connectClient(waitingClient);
                } else {
                    // Add to waiting queue
                    synchronized (waitingQueue) {
                        waitingQueue.add(waitingClient);
                        writer.println("WAITING:" + estimateWaitTime(waitingQueue.size()));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Formats messages to be sent - group and private messages
    public void broadcastMessage(String sender, String message, String recipient) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String formattedMessage = "[" + timestamp + "] " + sender + ": " + message;

        if ("ALL".equals(recipient)) {
            // Group message
            synchronized (activeClients) {
                for (Map.Entry<String, ClientHandler> entry : activeClients.entrySet()) {
                    ClientHandler handler = entry.getValue();
                    if (!handler.getUsername().equals(sender)) {
                        handler.sendMessage("GROUP:" + formattedMessage);
                    }
                }
            }
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
        String formattedMessage = "[" + timestamp + "]" + message;

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

        if (userList.length() > 6 && userList.charAt(userList.length() - 1) == ',') {
            userList.deleteCharAt(userList.length() - 1);
        }

        requester.sendMessage(userList.toString());
    }

    // Method to update everyone's user lists if new user joins or user leaves
    public void updateAllUserLists() {
        synchronized (activeClients) {
            for (ClientHandler handler : activeClients.values()) {
                sendUserList(handler);
            }
        }
    }

    // Remove user who left from lists
    public void removeClient(String username) {
        activeClients.remove(username);
        connectionSemaphore.release();

        broadcastServerMessage(username + " has left the chat.");
        updateAllUserLists();

        // Check if anyone is waiting
        checkWaitingQueue();
    }

    // Simple semaphore implementation - Inner Class
    private static class SimpleSemaphore {
        private int permits;
        private final Object lock = new Object();

        public SimpleSemaphore(int permits) {
            this.permits = permits;
        }

        public boolean tryAcquire() {
            synchronized (lock) {
                if (permits > 0) {
                    permits--;
                    return true;
                }
                return false;
            }
        }

        public void release() {
            synchronized (lock) {
                permits++;
                lock.notify();
            }
        }
    }

    // Class to manage waiting clients - Inner Class
    private static class WaitingClient {
        final String username;
        final Socket socket;
        final long joinTime;

        WaitingClient(String username, Socket socket, long joinTime) {
            this.username = username;
            this.socket = socket;
            this.joinTime = joinTime;
        }
    }

    // Move a client from waiting list to active user
    private void connectClient(WaitingClient waitingClient) {
        try {
            ClientHandler handler = new ClientHandler(waitingClient.socket, waitingClient.username, this);
            activeClients.put(waitingClient.username, handler);

            // Start the client handler thread
            new Thread(handler).start();

            System.out.println(waitingClient.username + " connected to the chat.");
            broadcastServerMessage(waitingClient.username + " has joined the chat.");

            // Send the current list of users to all clients
            updateAllUserLists();
        } catch (IOException e) {
            System.err.println("Error connecting client: " + e.getMessage());
            connectionSemaphore.release();
        }
    }

    // Updates client about their wait time
    private void updateWaitTimes() {
        synchronized (waitingQueue) {
            if (!waitingQueue.isEmpty()) {
                System.out.println("Current waiting queue size: " + waitingQueue.size());

                // Create a copy of the queue
                List<WaitingClient> clientsCopy = new ArrayList<>(waitingQueue);

                for (int i = 0; i < clientsCopy.size(); i++) {
                    WaitingClient client = clientsCopy.get(i);
                    try {
                        PrintWriter writer = new PrintWriter(client.socket.getOutputStream(), true);
                        int position = i + 1;
                        writer.println("WAITING:" + estimateWaitTime(position));
                    } catch (IOException e) {
                        System.out.println("Client " + client.username + " disconnected while waiting");
                        waitingQueue.remove(client);
                    }
                }
            }
        }
    }

    // Estimate a user's wait time
    private String estimateWaitTime(int position) {
        int waitMins = position * 2;    // Estimate: 2 mins
        return waitMins + " minutes";
    }

    // Check if there are any clients waiting in the queue and connect next one if possible
    public void checkWaitingQueue() {
        synchronized (waitingQueue) {
            if (!waitingQueue.isEmpty()) {
                WaitingClient nextClient = waitingQueue.remove(0);
                connectClient(nextClient);
            }
        }
    }

    // Method to retrieve client handler by username
    public ClientHandler getClientHandler(String username) {
        synchronized (activeClients) {
            return activeClients.get(username);
        }
    }
}
