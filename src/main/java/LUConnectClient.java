import javax.swing.*;
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
    private JList<String> onlineUsersList;
    private DefaultListModel<String> usersListModel;
    private Thread messageHandler;
    private boolean connected = false;

    // Components
    private JTextPane chatArea;
    private JTextPane privateArea;
    private JTextField messageField;
    private JButton sendButton;
    private JComboBox<String> recipientCombo;
    private JLabel statusLabel;
    private JPanel waitingPanel;
    private JLabel waitTimeLabel;


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String username = JOptionPane.showInputDialog(null,
                    "Enter your username:", "LUConnect Login", JOptionPane.QUESTION_MESSAGE);

            if (username != null && !username.trim().isEmpty()) {
                new LUConnectClient(username);
            }
        });
    }

    public LUConnectClient(String username) {
        this.username = username;

        // Set up GUI
        setTitle("LUConnect - " + username);
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initializeComponents();
        setupLayout();

        // Connect to server
        connectToServer();
        setVisible(true);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect));
    }

    // Initialize all GUI components
    private void initializeComponents() {
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
        sendButton.setBackground(new Color(128, 128, 128));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Arial", Font.BOLD, 14));
        sendButton.setFocusPainted(false);
        sendButton.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));

        recipientCombo = new JComboBox<>();
        recipientCombo.addItem("ALL");
        recipientCombo.setFont(new Font("Arial", Font.PLAIN, 14));
        recipientCombo.setBackground(Color.WHITE);

        usersListModel = new DefaultListModel<>();
        onlineUsersList = new JList<>(usersListModel);
        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Add online users to online user list seen by client
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

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Main split pane
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerSize(5);
        mainSplitPane.setBorder(null);
        mainSplitPane.setBackground(BACKGROUND_COLOR);

        // Online users list
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBackground(Color.GRAY);  // Set the background to grey
        JLabel usersLabel = new JLabel("Online Users", SwingConstants.CENTER);
        usersLabel.setFont(new Font("Arial", Font.BOLD, 16));
        usersLabel.setForeground(TEXT_COLOR);
        usersLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        JScrollPane usersScrollPane = new JScrollPane(onlineUsersList);
        usersScrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        usersPanel.add(usersLabel, BorderLayout.NORTH);
        usersPanel.add(usersScrollPane, BorderLayout.CENTER);
        usersPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 10));

        JSplitPane chatSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        chatSplitPane.setDividerSize(5);
        chatSplitPane.setBorder(null);
        chatSplitPane.setBackground(Color.GRAY);  // Set the background to grey

        // Group chat panel
        JPanel groupChatPanel = new JPanel(new BorderLayout());
        groupChatPanel.setBackground(Color.GRAY);  // Set the background to grey

        JLabel groupLabel = new JLabel("Group Chat", SwingConstants.CENTER);
        groupLabel.setFont(new Font("Arial", Font.BOLD, 16));
        groupLabel.setForeground(TEXT_COLOR);
        groupLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        JScrollPane groupScrollPane = new JScrollPane(chatArea);
        groupScrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        groupChatPanel.add(groupLabel, BorderLayout.NORTH);
        groupChatPanel.add(groupScrollPane, BorderLayout.CENTER);
        groupChatPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));

        // Private chat panel
        JPanel privateChatPanel = new JPanel(new BorderLayout());
        privateChatPanel.setBackground(Color.GRAY);  // Set the background to grey

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

        // Message input
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

    }


    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_HOST, SERVER_PORT);
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Receive from server if connection unsuccessful
                String welcome = reader.readLine();
                if ("WELCOME".equals(welcome)) {
                    writer.println(username);

                    // Start message handling
                    messageHandler = new Thread(this::receiveMessages);
                    messageHandler.start();
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Connection failed: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                });
                e.printStackTrace();
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

    // Process messages from server
    private void processMessage(String message) {
        // Account for all messages from server
        if (message.startsWith("CONNECTED")) {
            statusLabel.setText("Connected to server");
            statusLabel.setForeground(Color.GREEN);
            getGlassPane().setVisible(false);
            enableChat(true);
            connected = true;

            // Get user list from server
            writer.println("USERS");

        } else if (message.startsWith("USERS:")) {
            updateUserList(message.substring(6));
        } else if (message.startsWith("GROUP:")) {
            addFormattedMessage(chatArea, message.substring(6));
        } else if (message.startsWith("PRIVATE:")) {
            addFormattedMessage(privateArea, message.substring(8));
        } else if (message.startsWith("SERVER:")) {
            addFormattedMessage(chatArea, "SERVER: " + message.substring(7));
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

    // Send message to server to be processed
    private void sendMessage() {
        if (!connected) return;

        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        String recipient = (String) recipientCombo.getSelectedItem();
        if (recipient == null) recipient = "ALL";

        // Send the message to server
        writer.println("MSG:" + recipient + ":" + message);

        // Display in the appropriate area (depending on group or personal)
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String formattedMessage = "[" + timestamp + "] Me: " + message;

        if ("ALL".equals(recipient)) {
            addFormattedMessage(chatArea, formattedMessage);
        } else {
            addFormattedMessage(privateArea, "To " + recipient + ": " + formattedMessage);
        }

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
            e.printStackTrace();
        }
    }
}