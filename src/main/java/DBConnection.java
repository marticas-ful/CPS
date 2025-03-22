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
            String db_path = new File(get_db.toURI()).getAbsolutePath();
            connection = DriverManager.getConnection("jdbc:sqlite:" + db_path);
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
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

            try (ResultSet rs = preparedStatement.executeQuery()) {
                return rs.next(); // Returns true if a matching user was found
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

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);

            int rowsAffected = pstmt.executeUpdate();
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

            try (ResultSet rs = preparedStatement.executeQuery()) {
                return rs.next(); // Returns true if the username exists
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

}
