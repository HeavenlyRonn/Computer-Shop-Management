package database;

// Central MySQL connection factory for the Computer Shop Management System.
// All DAO classes obtain their database connections through this class.
public class DBConnection {

    private static final String DATABASE_URL = "jdbc:mysql://localhost:3306/comshop_db";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "";

    public static java.sql.Connection openConnection() throws java.sql.SQLException {
        // Open a fresh connection on every call so try-with-resources can close it safely.
        return java.sql.DriverManager.getConnection(DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD);
    }
}
