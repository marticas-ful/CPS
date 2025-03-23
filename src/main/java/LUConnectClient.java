import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LUConnectClient extends JFrame {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9876;

    // Initializing main colours
    private static final Color BACKGROUND_COLOR = new Color(180, 40, 40); // Red background
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color INPUT_BG_COLOR = new Color(240, 240, 240);
    private static final Color HEADER_COLOR = new Color(180, 40, 40);
    private static final Color GREY = new Color(128, 128, 128);

    // For server connection
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private String username;
    private String password;
    private static DBConnection dbConnection;

    // Components
    private JTextPane chatArea;
    private JTextPane privateArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton openDirectoryButton;
    private JComboBox<String> recipientCombo;
    private JLabel statusLabel;
    private JPanel waitingPanel;
    private JLabel waitTimeLabel;
    private JList<String> onlineUsersList;
    private DefaultListModel<String> usersListModel;
    private Thread messageHandler;
    private boolean connected = false;
    private JProgressBar progressBar;

    // Sound control components
    private JToggleButton muteButton;
    private boolean soundIsMuted = false;

    public static void main(String[] args) {
        dbConnection = DBConnection.getInstance();
        dbConnection.establishConnection();

        SwingUtilities.invokeLater(() -> {
            loginScreen();
        });
    }

    public LUConnectClient(String username, String password) throws URISyntaxException {
        this.username = username;
        this.password = password;

        // Set up UI
        setTitle("LUConnect - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initializeComponents();
        setupLayout();

        // Connect to server
        connectToServer();
        setVisible(true);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect));
    }

    // Initialize components for waitlist and chat screen
    private void initializeComponents() throws URISyntaxException {
        Font chatFont = new Font("Arial", Font.PLAIN, 14);

        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setFont(chatFont);
        chatArea.setBackground(new Color(250, 240, 240));
        chatArea.setForeground(Color.BLACK);
        chatArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        privateArea = new JTextPane();
        privateArea.setEditable(false);
        privateArea.setFont(chatFont);
        privateArea.setBackground(new Color(240, 240, 250));
        privateArea.setForeground(Color.BLACK);
        privateArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        messageField = new JTextField();
        messageField.addActionListener(e -> sendMessage());
        messageField.setFont(new Font("Arial", Font.PLAIN, 14));
        messageField.setBackground(INPUT_BG_COLOR);
        messageField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        sendButton.setBackground(GREY);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Arial", Font.BOLD, 14));
        sendButton.setFocusPainted(false);
        sendButton.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));

        muteButton = new JToggleButton("Mute");
        muteButton.addActionListener(e -> toggleMuteButton());
        muteButton.setFont(new Font("Arial", Font.BOLD, 14));
        muteButton.setFocusPainted(false);
        muteButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        muteButton.setBackground(new Color(240, 240, 240));

        openDirectoryButton = new JButton("Files");
        openDirectoryButton.addActionListener(e -> handleFiles());
        openDirectoryButton.setFont(new Font("Arial", Font.BOLD, 14));
        openDirectoryButton.setFocusPainted(false);
        openDirectoryButton.setBackground(GREY);
        openDirectoryButton.setForeground(Color.WHITE);

        recipientCombo = new JComboBox<>();
        recipientCombo.addItem("ALL");
        recipientCombo.setFont(new Font("Arial", Font.PLAIN, 14));
        recipientCombo.setBackground(Color.WHITE);

        usersListModel = new DefaultListModel<>();
        onlineUsersList = new JList<>(usersListModel);
        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && onlineUsersList.getSelectedValue() != null) {
                recipientCombo.setSelectedItem(onlineUsersList.getSelectedValue());
            }
        });
        onlineUsersList.setFont(new Font("Arial", Font.PLAIN, 14));
        onlineUsersList.setBackground(new Color(250, 240, 240));
        onlineUsersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (isSelected) {
                    c.setBackground(new Color(180, 40, 40));
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(new Color(250, 240, 240));
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });

        statusLabel = new JLabel("Connecting to server...");
        statusLabel.setForeground(TEXT_COLOR);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        waitingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        waitingPanel.setBackground(new Color(0, 0, 0, 180));
        waitTimeLabel = new JLabel("Waiting for connection slot...");
        waitTimeLabel.setForeground(Color.WHITE);
        waitTimeLabel.setFont(new Font("Arial", Font.BOLD, 16));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setBackground(new Color(220, 220, 220));
        progressBar.setForeground(new Color(180, 40, 40));
        progressBar.setPreferredSize(new Dimension(200, 20));

        waitingPanel.add(waitTimeLabel);
        waitingPanel.add(Box.createVerticalStrut(10));
        waitingPanel.add(progressBar);
        waitingPanel.setVisible(false);
    }

    // Setup layout with components
    private void setupLayout() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BACKGROUND_COLOR);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(HEADER_COLOR);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("LUConnect");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusLabel, BorderLayout.EAST);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlPanel.setBackground(HEADER_COLOR);
        controlPanel.add(muteButton);
        headerPanel.add(controlPanel, BorderLayout.CENTER);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerSize(5);
        mainSplitPane.setBorder(null);
        mainSplitPane.setBackground(BACKGROUND_COLOR);

        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBackground(GREY);
        JLabel usersLabel = new JLabel("Online Users", SwingConstants.CENTER);
        usersLabel.setFont(new Font("Arial", Font.BOLD, 16));
        usersLabel.setForeground(TEXT_COLOR);
        usersLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        JScrollPane usersScrollPane = new JScrollPane(onlineUsersList);
        usersScrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel directoryButtonPanel = new JPanel(new BorderLayout());
        directoryButtonPanel.setBackground(GREY);
        directoryButtonPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        directoryButtonPanel.add(openDirectoryButton, BorderLayout.CENTER);

        usersPanel.add(usersLabel, BorderLayout.NORTH);
        usersPanel.add(usersScrollPane, BorderLayout.CENTER);
        usersPanel.add(directoryButtonPanel, BorderLayout.SOUTH);
        usersPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 10));

        JSplitPane chatSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        chatSplitPane.setDividerSize(5);
        chatSplitPane.setBorder(null);
        chatSplitPane.setBackground(GREY);

        JPanel groupChatPanel = new JPanel(new BorderLayout());
        groupChatPanel.setBackground(GREY);

        JLabel groupLabel = new JLabel("Group Chat", SwingConstants.CENTER);
        groupLabel.setFont(new Font("Arial", Font.BOLD, 16));
        groupLabel.setForeground(TEXT_COLOR);
        groupLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        JScrollPane groupScrollPane = new JScrollPane(chatArea);
        groupScrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        groupChatPanel.add(groupLabel, BorderLayout.NORTH);
        groupChatPanel.add(groupScrollPane, BorderLayout.CENTER);
        groupChatPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));

        JPanel privateChatPanel = new JPanel(new BorderLayout());
        privateChatPanel.setBackground(GREY);

        JLabel privateLabel = new JLabel("Private Messages", SwingConstants.CENTER);
        privateLabel.setFont(new Font("Arial", Font.BOLD, 16));
        privateLabel.setForeground(TEXT_COLOR);
        privateLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        JScrollPane privateScrollPane = new JScrollPane(privateArea);
        privateScrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        privateChatPanel.add(privateLabel, BorderLayout.NORTH);
        privateChatPanel.add(privateScrollPane, BorderLayout.CENTER);
        privateChatPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));
        chatSplitPane.setTopComponent(groupChatPanel);
        chatSplitPane.setBottomComponent(privateChatPanel);
        chatSplitPane.setDividerLocation(350);

        mainSplitPane.setLeftComponent(chatSplitPane);
        mainSplitPane.setRightComponent(usersPanel);
        mainSplitPane.setDividerLocation(700);

        mainPanel.add(mainSplitPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(HEADER_COLOR);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel recipientPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        recipientPanel.setBackground(HEADER_COLOR);

        JLabel recipientLabel = new JLabel("Send to:");
        recipientLabel.setForeground(TEXT_COLOR);
        recipientLabel.setFont(new Font("Arial", Font.BOLD, 14));

        recipientPanel.add(recipientLabel);
        recipientPanel.add(recipientCombo);

        inputPanel.add(recipientPanel, BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        JPanel glassPanel = new JPanel(new GridBagLayout());
        glassPanel.setOpaque(false);
        glassPanel.add(waitingPanel);
        setGlassPane(glassPanel);

        add(mainPanel);
    }

    // Method to handle inputted files
    private void handleFiles(){
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select File");

        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "DOCX, PDF, JPEG", "docx", "pdf", "jpeg");
        fileChooser.setFileFilter(filter);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String fileName = selectedFile.getName();
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

            if (extension.equals("jpeg") || extension.equals("docx") || extension.equals("pdf")) {
                String recipient = (String) recipientCombo.getSelectedItem();
                if (recipient == null || recipient.equals("ALL")) {
                    JOptionPane.showMessageDialog(this, "Please select a specific user for file transfer.", "No User Selected", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                try {
                    byte[] fileBytes = java.nio.file.Files.readAllBytes(selectedFile.toPath());
                    String encodedFile = java.util.Base64.getEncoder().encodeToString(fileBytes);
                    writer.println("FILE:" + recipient + ":" + fileName + ":" + encodedFile);
                    dbConnection.storeMessage(fileName, username, recipient);
                    JOptionPane.showMessageDialog(this, "File sent: " + fileName, "File Sent", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error reading file: " + ex.getMessage(), "File Sending Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Invalid file type- use .docx, .pdf and .jpeg only", "Invalid File", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void toggleMuteButton() {
        soundIsMuted = muteButton.isSelected();
        muteButton.setText(soundIsMuted ? "Unmute" : "Mute");
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_HOST, SERVER_PORT);
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String welcome = reader.readLine();
                if ("WELCOME".equals(welcome)) {
                    // Send username:password
                    writer.println(username + ":" + password);

                    messageHandler = new Thread(this::receiveMessages);
                    messageHandler.start();
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Connection failed: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                });
            }
        }).start();
    }

    // Continuously read incoming messages from server
    private void receiveMessages() {
        try {
            String message;
            while ((message = reader.readLine()) != null) {
                final String msg = message;
                SwingUtilities.invokeLater(() -> processMessage(msg));
            }
        } catch (IOException e) {
            if (connected) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Disconnected from server");
                    statusLabel.setForeground(Color.RED);
                    enableChat(false);
                });
            }
        }
    }

    // Format messages to send
    private void addFormattedMessage(JTextPane pane, String message) {
        try {
            Document doc = pane.getDocument();
            doc.insertString(doc.getLength(), message + "\n", null);
            pane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void playNotificationSound(String resourceName) {
        if (!soundIsMuted) {
            try {
                URL get_sound = getClass().getClassLoader().getResource(resourceName);
                String sound_path = new File(get_sound.toURI()).getAbsolutePath();
                NotificationTone.playNotificationTone(sound_path);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Process messages from server
    private void processMessage(String message) {
        if (message.startsWith("CONNECTED")) {

            statusLabel.setText("Connected to server");
            statusLabel.setForeground(Color.GREEN);
            getGlassPane().setVisible(false);
            enableChat(true);
            connected = true;

            // Get user list from users
            writer.println("USERS");

        } else if (message.startsWith("USERS:")) {

            updateUserList(message.substring(6));

        } else if (message.startsWith("GROUP:")) {

            playNotificationSound("incoming_message.wav");
            addFormattedMessage(chatArea, message.substring(6));

        } else if (message.startsWith("PRIVATE:")) {

            playNotificationSound("incoming_message.wav");
            addFormattedMessage(privateArea, message.substring(8));

        } else if (message.startsWith("SERVER:")) {

            addFormattedMessage(chatArea, "SERVER: " + message.substring(7));

        } else if (message.startsWith("WAITING:")) {

            String waitTime = message.substring(8);
            statusLabel.setText("Waiting to connect");
            waitTimeLabel.setText("Estimated wait time: " + waitTime);
            getGlassPane().setVisible(true);
            enableChat(false);

        } else if (message.startsWith("FILE:")) {

            String[] parts = message.split(":", 4);
            if (parts.length < 4) {
                addFormattedMessage(chatArea, "Received invalid file message.");
                return;
            }
            String sender = parts[1];
            String fileName = parts[2];
            String fileData = parts[3];

            try {
                byte[] data = java.util.Base64.getDecoder().decode(fileData);
                File receivedDir = new File("RecievedFiles");
                if (!receivedDir.exists()) {
                    receivedDir.mkdir();
                }
                File outFile = new File(receivedDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(data);
                }
                addFormattedMessage(privateArea, "File received from " + sender + ": " + fileName + ". Stored in directory ReceivedFiles");

            } catch (IOException e) {
                addFormattedMessage(privateArea, "Error saving received file: " + fileName);
            }

        } else if (message.startsWith("ERROR:")) {

            JOptionPane.showMessageDialog(this,
                    message.substring(6), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);

        }
    }

    // Method to update user list for client
    private void updateUserList(String userListStr) {
        String selectedItem = (String) recipientCombo.getSelectedItem();

        recipientCombo.removeAllItems();
        recipientCombo.addItem("ALL");

        usersListModel.clear();

        if (!userListStr.isEmpty()) {
            String[] users = userListStr.split(",");
            for (String user : users) {
                if (!user.equals(username)) {
                    recipientCombo.addItem(user);
                    usersListModel.addElement(user);
                }
            }
        }

        if (selectedItem != null) {
            boolean found = false;
            for (int i = 0; i < recipientCombo.getItemCount(); i++) {
                if (selectedItem.equals(recipientCombo.getItemAt(i))) {
                    recipientCombo.setSelectedItem(selectedItem);
                    found = true;
                    break;
                }
            }
            if (!found) {
                recipientCombo.setSelectedItem("ALL");
            }
        } else {
            recipientCombo.setSelectedItem("ALL");
        }
    }

    // Send message to server to be processed + store in DB
    private void sendMessage() {
        if (!connected) return;

        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        String recipient = (String) recipientCombo.getSelectedItem();
        if (recipient == null) recipient = "ALL";

        writer.println("MSG:" + recipient + ":" + message);

        playNotificationSound("outgoing_message.wav");

        // Display in the appropriate area - group or personal
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String formattedMessage = "[" + timestamp + "] Me: " + message;

        if ("ALL".equals(recipient)) {
            addFormattedMessage(chatArea, formattedMessage);
        } else {
            addFormattedMessage(privateArea, "To " + recipient + ": " + formattedMessage);
        }
        dbConnection.storeMessage(message, username, recipient);
        // Clear message field
        messageField.setText("");
    }

    // Enables/disables input fields based on connection
    private void enableChat(boolean enable) {
        messageField.setEnabled(enable);
        sendButton.setEnabled(enable);
        recipientCombo.setEnabled(enable);
    }

    // Notify server if user leaves and closes the socket connection
    private void disconnect() {
        if (writer != null) {
            writer.println("QUIT");
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Silently ignore close errors during shutdown
        }
    }

    // Login screen
    private static void loginScreen() {
        JDialog loginDialog = new JDialog((Frame)null, "LUConnect Login", true);
        loginDialog.setSize(350, 250);
        loginDialog.setLocationRelativeTo(null);
        loginDialog.setLayout(new BorderLayout());

        JPanel formPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        formPanel.setBackground(new Color(240, 240, 240));

        JLabel usernameLabel = new JLabel("Username:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Password:");
        JPasswordField passwordField = new JPasswordField();

        formPanel.add(usernameLabel);
        formPanel.add(usernameField);
        formPanel.add(passwordLabel);
        formPanel.add(passwordField);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBackground(new Color(240, 240, 240));

        JButton loginButton = new JButton("Login");
        JButton registerButton = new JButton("Register");

        loginButton.setBackground(new Color(180, 40, 40));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);

        registerButton.setBackground(GREY);
        registerButton.setForeground(Color.WHITE);
        registerButton.setFocusPainted(false);

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(240, 240, 240));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
        statusPanel.add(statusLabel, BorderLayout.CENTER);

        loginDialog.add(formPanel, BorderLayout.NORTH);
        loginDialog.add(buttonPanel, BorderLayout.CENTER);
        loginDialog.add(statusPanel, BorderLayout.SOUTH);

        loginButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                statusLabel.setText("Username and password are required.");
                return;
            }

            if (dbConnection.authenticateUser(username, password)) {
                loginDialog.dispose();
                try {
                    new LUConnectClient(username, password);
                } catch (URISyntaxException ex) {
                    ex.printStackTrace();
                }
            } else {
                statusLabel.setText("Invalid username or password.");
            }
        });

        registerButton.addActionListener(e -> {
            loginDialog.dispose();
            registrationScreen();
        });

        loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        loginDialog.setVisible(true);
    }

    // Registration screen
    private static void registrationScreen() {
        JDialog registerDialog = new JDialog((Frame)null, "LUConnect Registration", true);
        registerDialog.setSize(350, 280);
        registerDialog.setLocationRelativeTo(null);
        registerDialog.setLayout(new BorderLayout());

        JPanel formPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        formPanel.setBackground(new Color(240, 240, 240));

        JLabel usernameLabel = new JLabel("Username:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Password:");
        JPasswordField passwordField = new JPasswordField();
        JLabel confirmLabel = new JLabel("Confirm Password:");
        JPasswordField confirmField = new JPasswordField();

        formPanel.add(usernameLabel);
        formPanel.add(usernameField);
        formPanel.add(passwordLabel);
        formPanel.add(passwordField);
        formPanel.add(confirmLabel);
        formPanel.add(confirmField);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBackground(new Color(240, 240, 240));

        JButton registerButton = new JButton("Register");
        JButton backButton = new JButton("Back to Login");

        registerButton.setBackground(new Color(180, 40, 40));
        registerButton.setForeground(Color.WHITE);
        registerButton.setFocusPainted(false);

        backButton.setBackground(GREY);
        backButton.setForeground(Color.WHITE);
        backButton.setFocusPainted(false);

        buttonPanel.add(registerButton);
        buttonPanel.add(backButton);

        JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(240, 240, 240));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
        statusPanel.add(statusLabel, BorderLayout.CENTER);

        registerDialog.add(formPanel, BorderLayout.NORTH);
        registerDialog.add(buttonPanel, BorderLayout.CENTER);
        registerDialog.add(statusPanel, BorderLayout.SOUTH);

        registerButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String confirmPassword = new String(confirmField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                statusLabel.setText("Username and password are required.");
                return;
            }

            if (!password.equals(confirmPassword)) {
                statusLabel.setText("Passwords do not match.");
                return;
            }

            if (dbConnection.userExists(username)) {
                statusLabel.setText("Username already exists.");
                return;
            }

            if (dbConnection.registerUser(username, password)) {
                registerDialog.dispose();
                try {
                    new LUConnectClient(username, password);
                } catch (URISyntaxException ex) {
                    ex.printStackTrace();
                }
            } else {
                statusLabel.setText("Registration failed. Please try again.");
            }
        });

        backButton.addActionListener(e -> {
            registerDialog.dispose();
            loginScreen();
        });

        registerDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        registerDialog.setVisible(true);
    }
}
