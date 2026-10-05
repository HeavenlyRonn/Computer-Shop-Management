package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// Handles all database queries for the users table. Used only by the admin login.
public class UserDAO {

    public String validateLogin(String username, String password) {
        // Look up the role for matching credentials, null means no match.
        String queryStatement = "SELECT role FROM users WHERE username = ? AND password = ?";
        try (
            Connection connection = DBConnection.openConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(queryStatement)
        ) {
            // Bind the supplied credentials to the placeholders to prevent SQL injection.
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, password);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                // Return the role when a row matches, otherwise fall through to null.
                if (resultSet.next()) {
                    return resultSet.getString("role");
                }
                return null;
            }
        } catch (SQLException sqlException) {
            // Print the failure for debugging and treat it as a failed login.
            sqlException.printStackTrace();
            return null;
        }
    }
}
