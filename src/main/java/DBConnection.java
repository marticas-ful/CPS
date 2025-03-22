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

        String query = "SELECT * FROM Users WHERE username = ? AND password = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, password);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next(); // Returns true if a matching user was found
            }
        } catch (SQLException e) {
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

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, password);

            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Check if username already exists in DB
    public boolean userExists(String username) {
        if (connection == null) {
            System.out.println("No database connection.");
            return false;
        }

        String query = "SELECT * FROM Users WHERE username = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next(); // Returns true if the username exists
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Get user ID from username
    public int getUserId(String username) {
        if (connection == null) {
            System.out.println("No database connection.");
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
    public boolean storeMessage(String messageContent, String username) {
        if (connection == null) {
            System.out.println("No database connection.");
            return false;
        }

        int userId = getUserId(username);
        if (userId == -1) {
            System.out.println("User not found: " + username);
            return false;
        }

        String query = "INSERT INTO MessageHistory (contents, user_id) VALUES (?, ?)";

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, messageContent);
            preparedStatement.setInt(2, userId);

            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

}
