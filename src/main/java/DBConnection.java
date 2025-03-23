import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.*;

class DBConnection {

    private static DBConnection instance = null;
    private Connection connection = null;

    public static DBConnection getInstance() {
        if (instance == null) {
            synchronized (DBConnection.class) {
                if (instance == null) {
                    instance = new DBConnection();
                }
            }
        }
        return instance;
    }

    public Connection establishConnection(){

        URL get_db = getClass().getClassLoader().getResource("LUConnect.db");

        try {
            String db_path = null;
            if (get_db != null) {
                db_path = new File(get_db.toURI()).getAbsolutePath();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + db_path);
        } catch (SQLException | URISyntaxException e) {
            e.printStackTrace();
        }
        return connection;
    }

    // Method to authenticate user (check login info)
    public boolean authenticateUser(String username, String password) {
        if (connection == null) {
            System.out.println("No database connection.");
            return false;
        }

        String query = "SELECT password FROM Users WHERE username = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    String encryptedPasswordFromDB = resultSet.getString("password");
                    String decryptedPassword = Security.decrypt(encryptedPasswordFromDB);
                    return decryptedPassword.equals(password);
                }
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Register user in DB
    public boolean registerUser(String username, String password) {
        if (connection == null) {
            System.out.println("No database connection");
            return false;
        }

        String query = "INSERT INTO Users (username, password) VALUES (?, ?)";

        try {
            String encryptedPassword = Security.encrypt(password);

            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, username);
                preparedStatement.setString(2, encryptedPassword);

                int rowsAffected = preparedStatement.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Check if username already exists in DB
    public boolean userExists(String username) {
        if (connection == null) {
            return false;
        }

        String query = "SELECT * FROM Users WHERE username = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Get user ID from username
    public int getUserId(String username) {
        if (connection == null) {
            return -1;
        }

        String get_user_id = "SELECT user_id FROM Users WHERE username = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(get_user_id)) {
            preparedStatement.setString(1, username);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("user_id");
                }
                return -1; // User not found
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    // Store message in message history
    public boolean storeMessage(String messageContent, String username, String recipient_username) {
        if (connection == null) {
            return false;
        }

        int user_id = getUserId(username);
        if (user_id == -1) {
            return false;
        }

        int recipient_id;

        // For messages with "ALL" create id -999
        if (recipient_username.equals("ALL")) {
            recipient_id = -999;
        } else {
            recipient_id = getUserId(recipient_username);
            if (recipient_id == -1) {
                System.out.println("User not found: " + recipient_username);
                return false;
            }
        }

        String query = "INSERT INTO MessageHistory (content, user_id, recipient_id) VALUES (?, ?, ?)";

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, messageContent);
            preparedStatement.setInt(2, user_id);
            preparedStatement.setInt(3, recipient_id);

            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
