package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// Handles all database queries for the pcs table.
public class PCDAO {

    public List<Object[]> getAllPCs() throws SQLException {
        // Read every PC in pc_number order for a stable grid layout.
        String queryStatement = "SELECT id, pc_number, hourly_rate, status, end_time FROM pcs ORDER BY pc_number";
        List<Object[]> pcRows = new ArrayList<>();
        try (
            Connection connection = DBConnection.openConnection();
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(queryStatement)
        ) {
            // Copy each row into an Object array so the UI needs no model class.
            while (resultSet.next()) {
                Object[] pcRow = new Object[5];
                pcRow[0] = resultSet.getInt("id");
                pcRow[1] = resultSet.getString("pc_number");
                pcRow[2] = resultSet.getBigDecimal("hourly_rate");
                pcRow[3] = resultSet.getString("status");
                pcRow[4] = resultSet.getTimestamp("end_time");
                pcRows.add(pcRow);
            }
        }
        return pcRows;
    }

    public void startSession(int pcId, int hours) throws SQLException {
        // Mark the PC In Use with an end time hours from now.
        String updateStatement = "UPDATE pcs SET status = 'In Use', end_time = TIMESTAMPADD(HOUR, ?, NOW()) WHERE id = ?";
        try (
            Connection connection = DBConnection.openConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(updateStatement)
        ) {
            // Bind the duration first and the target PC second.
            preparedStatement.setInt(1, hours);
            preparedStatement.setInt(2, pcId);
            preparedStatement.executeUpdate();
        }
    }

    public void extendSession(int pcId, int hours) throws SQLException {
        // Push the existing end time forward without touching the status.
        String updateStatement = "UPDATE pcs SET end_time = TIMESTAMPADD(HOUR, ?, end_time) WHERE id = ?";
        try (
            Connection connection = DBConnection.openConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(updateStatement)
        ) {
            // Bind the extra hours first and the target PC second.
            preparedStatement.setInt(1, hours);
            preparedStatement.setInt(2, pcId);
            preparedStatement.executeUpdate();
        }
    }

    public void editSession(int pcId, int hours) throws SQLException {
        // Reset the timer from now, used to correct a wrong Start entry.
        String updateStatement = "UPDATE pcs SET status = 'In Use', end_time = TIMESTAMPADD(HOUR, ?, NOW()) WHERE id = ?";
        try (
            Connection connection = DBConnection.openConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(updateStatement)
        ) {
            // Bind the corrected hours first and the target PC second.
            preparedStatement.setInt(1, hours);
            preparedStatement.setInt(2, pcId);
            preparedStatement.executeUpdate();
        }
    }

    public void stopSession(int pcId) throws SQLException {
        // Free the PC by clearing its end time.
        String updateStatement = "UPDATE pcs SET status = 'Available', end_time = NULL WHERE id = ?";
        try (
            Connection connection = DBConnection.openConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(updateStatement)
        ) {
            // Bind the target PC to the only placeholder.
            preparedStatement.setInt(1, pcId);
            preparedStatement.executeUpdate();
        }
    }

    public Object[] getPCByNumber(String pcNumber) throws SQLException {
        // Look up a single PC row by its number for the customer timer window.
        String queryStatement = "SELECT id, pc_number, hourly_rate, status, end_time FROM pcs WHERE pc_number = ?";
        try (
            Connection connection = DBConnection.openConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(queryStatement)
        ) {
            // Bind the requested PC number to the only placeholder.
            preparedStatement.setString(1, pcNumber);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                // Return null when no row matches so the caller can show an error.
                if (!resultSet.next()) {
                    return null;
                }
                Object[] pcRow = new Object[5];
                pcRow[0] = resultSet.getInt("id");
                pcRow[1] = resultSet.getString("pc_number");
                pcRow[2] = resultSet.getBigDecimal("hourly_rate");
                pcRow[3] = resultSet.getString("status");
                pcRow[4] = resultSet.getTimestamp("end_time");
                return pcRow;
            }
        }
    }

    public void sendNotification(int pcId) throws SQLException {
        // Flag the PC so its UserScreen shows a counter notice on the next poll.
        String updateStatement = "UPDATE pcs SET notify_pending = 1 WHERE id = ?";
        try (
            Connection connection = DBConnection.openConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(updateStatement)
        ) {
            // Bind the target PC to the only placeholder.
            preparedStatement.setInt(1, pcId);
            preparedStatement.executeUpdate();
        }
    }

    public boolean hasPendingNotification(int pcId) throws SQLException {
        // Check whether the counter has flagged this PC since the last poll.
        String queryStatement = "SELECT notify_pending FROM pcs WHERE id = ?";
        try (
            Connection connection = DBConnection.openConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(queryStatement)
        ) {
            // Bind the target PC to the only placeholder.
            preparedStatement.setInt(1, pcId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                // Return true only when the flag is set to 1.
                if (resultSet.next()) {
                    return resultSet.getInt("notify_pending") == 1;
                }
                return false;
            }
        }
    }

    public void clearNotification(int pcId) throws SQLException {
        // Clear the flag after the UserScreen has shown the counter notice.
        String updateStatement = "UPDATE pcs SET notify_pending = 0 WHERE id = ?";
        try (
            Connection connection = DBConnection.openConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(updateStatement)
        ) {
            // Bind the target PC to the only placeholder.
            preparedStatement.setInt(1, pcId);
            preparedStatement.executeUpdate();
        }
    }
}
