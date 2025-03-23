import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.net.Socket;

class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final String username;
    private final LUConnectServer server;
    private PrintWriter writer;

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

                } else if (message.equals("USERS")) {

                    server.sendUserList(this);

                } else if (message.startsWith("FILE:")) {
                    // Expected format: FILE:recipient:filename:base64data
                    String[] parts = message.split(":", 4);
                    if (parts.length < 4) {
                        sendMessage("SERVER:Invalid file transfer format.");
                        continue;
                    }
                    String recipient = parts[1];
                    String fileName = parts[2];
                    String fileData = parts[3];

                    String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
                    if (!(extension.equals("pdf") || extension.equals("jpeg") || extension.equals("jpg") || extension.equals("docx"))) {
                        sendMessage("SERVER:Unsupported file type for file " + fileName);
                        continue;
                    }

                    // Save file to "ServerFiles" directory
                    File serverDir = new File("ServerFiles");
                    if (!serverDir.exists()) {
                        serverDir.mkdir();
                    }

                    File outFile = new File(serverDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] data = java.util.Base64.getDecoder().decode(fileData);
                        fos.write(data);

                    } catch (IOException e) {
                        e.printStackTrace();
                        sendMessage("SERVER:Failed to save file " + fileName);
                        continue;
                    }

                    // Forward the file to receiving user
                    ClientHandler targetHandler = server.getClientHandler(recipient);
                    if (targetHandler != null) {
                        targetHandler.sendMessage("FILE:" + username + ":" + fileName + ":" + fileData);
                    } else {
                        sendMessage("SERVER:User " + recipient + " is not available for file transfer.");
                    }

                }else if (message.equals("QUIT")) {
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
