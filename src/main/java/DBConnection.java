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
        System.out.println("Resource URL: " + get_db);

        try {
            String db_path = new File(get_db.toURI()).getAbsolutePath();
            connection = DriverManager.getConnection("jdbc:sqlite:" + db_path);
            System.out.println("Database connection exists");
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return connection;
    }

    public void printTables() {
        if (connection == null) {
            System.out.println("No database connection. Please establish a connection first.");
            return;
        }

        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            System.out.println("Tables in the database:");
            while (rs.next()) {
                System.out.println(rs.getString("name"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}