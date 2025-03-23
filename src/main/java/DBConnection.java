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
    public boolean storeMessage(String messageContent, String username, String recipient_username) {
        if (connection == null) {
            System.out.println("No database connection.");
            return false;
        }

        int user_id = getUserId(username);
        if (user_id == -1) {
            System.out.println("User not found: " + username);
            return false;
        }

        int recipient_id = getUserId(recipient_username);
        if (recipient_id == -1) {
            System.out.println("User not found: " + recipient_username);
            return false;
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

    public void printDatabase() {
        if (connection == null) {
            System.out.println("No database connection.");
            return;
        }

        try {
            DatabaseMetaData metaData = connection.getMetaData();

            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");

                    if (tableName.startsWith("sqlite_")) {
                        continue;
                    }

                    System.out.println("\nTABLE: " + tableName);

                    try (Statement statement = connection.createStatement();
                         ResultSet resultSet = statement.executeQuery("SELECT * FROM " + tableName)) {

                        ResultSetMetaData rsMetaData = resultSet.getMetaData();
                        int columnCount = rsMetaData.getColumnCount();

                        StringBuilder header = new StringBuilder();
                        for (int i = 1; i <= columnCount; i++) {
                            header.append(rsMetaData.getColumnName(i));
                            if (i < columnCount) {
                                header.append(" | ");
                            }
                        }
                        System.out.println(header.toString());
                        System.out.println("-".repeat(header.length()));

                        while (resultSet.next()) {
                            StringBuilder row = new StringBuilder();
                            for (int i = 1; i <= columnCount; i++) {
                                String value = resultSet.getString(i);
                                row.append(value != null ? value : "NULL");
                                if (i < columnCount) {
                                    row.append(" | ");
                                }
                            }
                            System.out.println(row.toString());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
