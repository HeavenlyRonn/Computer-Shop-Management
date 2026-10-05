package ui;

import database.PCDAO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

// Admin dashboard showing every PC tile with live countdowns and timer controls.
// The Notify button flags a PC row so that PC's UserScreen shows a counter popup on its next 1-second poll.
@SuppressWarnings("serial")
public class AdminScreen extends JFrame {

    private static final Color WINDOW_BACKGROUND = new Color(240, 240, 240);
    private static final Color AVAILABLE_BACKGROUND = new Color(200, 240, 200);
    private static final Color IN_USE_BACKGROUND = new Color(240, 200, 200);

    private final PCDAO pcDataAccess = new PCDAO();
    private List<Object[]> pcRowList = new ArrayList<>();
    private final List<JPanel> tilePanelList = new ArrayList<>();
    private final List<JLabel> statusLabelList = new ArrayList<>();
    private JPanel pcGridPanel;

    private int selectedPcId = -1;
    private String selectedPcNumber;
    private BigDecimal selectedPcRate;

    private double todaysSales = 0.00;
    private JLabel salesLabel;
    private Timer refreshTimer;

    public AdminScreen() {
        // Configure the full-size admin window frame.
        super("Computer Shop Management System \u2014 Admin Dashboard");
        setSize(900, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(WINDOW_BACKGROUND);
        setLayout(new BorderLayout());

        // North title bar with left-aligned heading and spacing around it.
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titlePanel.setBackground(WINDOW_BACKGROUND);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel titleLabel = new JLabel("Admin Dashboard");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titlePanel.add(titleLabel);
        add(titlePanel, BorderLayout.NORTH);

        // Center grid holds one tile per PC, loaded once then updated in place.
        pcGridPanel = new JPanel(new GridLayout(2, 3, 12, 12));
        pcGridPanel.setBackground(WINDOW_BACKGROUND);
        pcGridPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        add(pcGridPanel, BorderLayout.CENTER);
        buildPcTiles();

        // South controls with action buttons on the left and sales on the right.
        JPanel controlsPanel = new JPanel(new BorderLayout());
        controlsPanel.setBackground(WINDOW_BACKGROUND);
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonPanel.setBackground(WINDOW_BACKGROUND);
        JButton startButton = new JButton("Start");
        JButton editButton = new JButton("Edit");
        JButton extendButton = new JButton("Extend");
        JButton stopButton = new JButton("Stop");
        JButton notifyButton = new JButton("Notify");
        buttonPanel.add(startButton);
        buttonPanel.add(editButton);
        buttonPanel.add(extendButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(notifyButton);
        controlsPanel.add(buttonPanel, BorderLayout.WEST);
        salesLabel = new JLabel("Today\u2019s sales: \u20B10.00");
        salesLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        controlsPanel.add(salesLabel, BorderLayout.EAST);
        add(controlsPanel, BorderLayout.SOUTH);

        // Wire each button to its handler method.
        startButton.addActionListener(actionEvent -> handleStartAction());
        editButton.addActionListener(actionEvent -> handleEditAction());
        extendButton.addActionListener(actionEvent -> handleExtendAction());
        stopButton.addActionListener(actionEvent -> handleStopAction());
        notifyButton.addActionListener(actionEvent -> handleNotifyAction());

        // Refresh countdowns every second without recreating the tiles.
        refreshTimer = new Timer(1000, actionEvent -> refreshPcTiles());
        refreshTimer.start();
    }

    private void buildPcTiles() {
        // Load the initial rows so tile order matches pc_number order.
        try {
            pcRowList = pcDataAccess.getAllPCs();
        } catch (SQLException sqlException) {
            // Show empty grid on failure, the timer will retry on the next tick.
            sqlException.printStackTrace();
            pcRowList = new ArrayList<>();
        }
        // Create one clickable tile panel per PC row.
        for (Object[] pcRow : pcRowList) {
            int pcId = (Integer) pcRow[0];
            String pcNumber = (String) pcRow[1];
            BigDecimal hourlyRate = (BigDecimal) pcRow[2];
            JPanel tilePanel = new JPanel(new BorderLayout());
            tilePanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            JLabel numberLabel = new JLabel(pcNumber, JLabel.CENTER);
            numberLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
            JLabel statusLabel = new JLabel("Available", JLabel.CENTER);
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
            double rateValue = hourlyRate.doubleValue();
            JLabel rateLabel = new JLabel("\u20B1" + String.format("%.2f/hr", rateValue), JLabel.CENTER);
            rateLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            tilePanel.add(numberLabel, BorderLayout.NORTH);
            tilePanel.add(statusLabel, BorderLayout.CENTER);
            tilePanel.add(rateLabel, BorderLayout.SOUTH);
            // Remember panels and labels for in-place updates during refresh.
            tilePanelList.add(tilePanel);
            statusLabelList.add(statusLabel);
            // Capture row values for the click handler.
            final int clickedPcId = pcId;
            final String clickedPcNumber = pcNumber;
            final BigDecimal clickedPcRate = hourlyRate;
            // Shared click handler so panel and labels all forward to selectPc.
            java.awt.event.MouseAdapter tileClickAdapter = new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent mouseEvent) {
                    selectPc(clickedPcId, clickedPcNumber, clickedPcRate);
                    // Stop the event so the grid background handler does not also fire.
                    mouseEvent.consume();
                }
            };
            tilePanel.addMouseListener(tileClickAdapter);
            numberLabel.addMouseListener(tileClickAdapter);
            statusLabel.addMouseListener(tileClickAdapter);
            rateLabel.addMouseListener(tileClickAdapter);
            pcGridPanel.add(tilePanel);
        }
        // Clicking gaps or the border area of the grid clears the selection.
        pcGridPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent mouseEvent) {
                // Only the grid background itself clears, tile clicks are already consumed.
                if (mouseEvent.getSource() == pcGridPanel) {
                    selectedPcId = -1;
                    selectedPcNumber = null;
                    selectedPcRate = null;
                    refreshTileBorders();
                }
            }
        });
        // Paint the first texts and colors immediately.
        refreshPcTiles();
    }

    private void refreshPcTiles() {
        // Re-read the latest statuses so countdowns stay live.
        try {
            pcRowList = pcDataAccess.getAllPCs();
        } catch (SQLException sqlException) {
            // Keep the old display and keep ticking on database errors.
            sqlException.printStackTrace();
            return;
        }
        // Update each tile label and color without recreating components.
        for (int tileIndex = 0; tileIndex < pcRowList.size() && tileIndex < tilePanelList.size(); tileIndex++) {
            Object[] pcRow = pcRowList.get(tileIndex);
            String currentStatus = (String) pcRow[3];
            Timestamp endTime = (Timestamp) pcRow[4];
            JPanel tilePanel = tilePanelList.get(tileIndex);
            JLabel statusLabel = statusLabelList.get(tileIndex);
            if ("In Use".equals(currentStatus) && endTime != null) {
                // Show the live countdown or Time's up when the timer expired.
                long remainingMillis = endTime.getTime() - System.currentTimeMillis();
                if (remainingMillis <= 0) {
                    statusLabel.setText("Time\u2019s up");
                } else {
                    statusLabel.setText(formatRemainingTime(remainingMillis));
                }
                tilePanel.setBackground(IN_USE_BACKGROUND);
            } else {
                // Available PCs show plain text on a green tile.
                statusLabel.setText("Available");
                tilePanel.setBackground(AVAILABLE_BACKGROUND);
            }
        }
        // Restore the thick border on the selected tile only.
        refreshTileBorders();
    }

    private void refreshTileBorders() {
        // Apply 4px to the selected tile and 2px to the rest.
        for (int tileIndex = 0; tileIndex < pcRowList.size() && tileIndex < tilePanelList.size(); tileIndex++) {
            Object[] pcRow = pcRowList.get(tileIndex);
            int currentPcId = (Integer) pcRow[0];
            JPanel tilePanel = tilePanelList.get(tileIndex);
            if (currentPcId == selectedPcId) {
                tilePanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 4));
            } else {
                tilePanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            }
        }
    }

    private void selectPc(int pcId, String pcNumber, BigDecimal hourlyRate) {
        // Clicking the selected tile again deselects it entirely.
        if (pcId == selectedPcId) {
            selectedPcId = -1;
            selectedPcNumber = null;
            selectedPcRate = null;
        } else {
            // Normal selection remembers the clicked PC for the action buttons.
            selectedPcId = pcId;
            selectedPcNumber = pcNumber;
            selectedPcRate = hourlyRate;
        }
        refreshTileBorders();
    }

    private Object[] findSelectedRow() {
        // Look up the cached row for the selected PC to check its live status.
        for (Object[] pcRow : pcRowList) {
            int currentPcId = (Integer) pcRow[0];
            if (currentPcId == selectedPcId) {
                return pcRow;
            }
        }
        return null;
    }

    private String formatRemainingTime(long remainingMillis) {
        // Convert milliseconds to h:mm:ss with no leading zero on hours.
        long totalSeconds = remainingMillis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours + ":" + String.format("%02d:%02d", minutes, seconds);
    }

    private String buildCountdownText(Timestamp endTime) {
        // Shared helper for dialogs so Extend and Stop show the same text as tiles.
        long remainingMillis = endTime.getTime() - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            return "Time\u2019s up";
        }
        return formatRemainingTime(remainingMillis);
    }

    private void updateSalesDisplay() {
        // Refresh the sales label after every paid Start or Extend.
        salesLabel.setText("Today\u2019s sales: \u20B1" + String.format("%.2f", todaysSales));
    }

    private void handleStartAction() {
        // Start requires a selection and an Available PC.
        if (selectedPcId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a PC first.");
            return;
        }
        Object[] selectedRow = findSelectedRow();
        String currentStatus = selectedRow == null ? "Available" : (String) selectedRow[3];
        if ("In Use".equals(currentStatus)) {
            JOptionPane.showMessageDialog(this, selectedPcNumber + " is already in use.");
            return;
        }
        // Dialog with hours combo, rate display, and a live total label.
        Integer[] hourOptions = {1, 2, 3, 4, 5, 6};
        JComboBox<Integer> hoursComboBox = new JComboBox<>(hourOptions);
        double hourlyRate = selectedPcRate.doubleValue();
        JLabel totalLabel = new JLabel("Total: \u20B1" + String.format("%.2f", hourlyRate * 1));
        hoursComboBox.addActionListener(actionEvent -> {
            int chosenHours = (Integer) hoursComboBox.getSelectedItem();
            totalLabel.setText("Total: \u20B1" + String.format("%.2f", chosenHours * hourlyRate));
        });
        JPanel dialogPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        dialogPanel.add(new JLabel("Hours:"));
        dialogPanel.add(hoursComboBox);
        dialogPanel.add(new JLabel("Rate:"));
        dialogPanel.add(new JLabel("\u20B1" + String.format("%.2f", hourlyRate) + "/hour"));
        dialogPanel.add(totalLabel);
        int dialogResult = JOptionPane.showConfirmDialog(this, dialogPanel, "Start " + selectedPcNumber, JOptionPane.OK_CANCEL_OPTION);
        if (dialogResult != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            // Record the new session then add the payment to today's sales.
            int chosenHours = (Integer) hoursComboBox.getSelectedItem();
            pcDataAccess.startSession(selectedPcId, chosenHours);
            todaysSales += chosenHours * hourlyRate;
            updateSalesDisplay();
            refreshPcTiles();
        } catch (SQLException sqlException) {
            JOptionPane.showMessageDialog(this, sqlException.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleEditAction() {
        // Edit requires a selection and an In Use PC, it corrects mistakes for free.
        if (selectedPcId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a PC first.");
            return;
        }
        Object[] selectedRow = findSelectedRow();
        String currentStatus = selectedRow == null ? "Available" : (String) selectedRow[3];
        if (!"In Use".equals(currentStatus)) {
            JOptionPane.showMessageDialog(this, selectedPcNumber + " is not in use.");
            return;
        }
        // Same hours dialog as Start but titled Edit, no sales change on save.
        Integer[] hourOptions = {1, 2, 3, 4, 5, 6};
        JComboBox<Integer> hoursComboBox = new JComboBox<>(hourOptions);
        double hourlyRate = selectedPcRate.doubleValue();
        JLabel totalLabel = new JLabel("Total: \u20B1" + String.format("%.2f", hourlyRate * 1));
        hoursComboBox.addActionListener(actionEvent -> {
            int chosenHours = (Integer) hoursComboBox.getSelectedItem();
            totalLabel.setText("Total: \u20B1" + String.format("%.2f", chosenHours * hourlyRate));
        });
        JPanel dialogPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        dialogPanel.add(new JLabel("Hours:"));
        dialogPanel.add(hoursComboBox);
        dialogPanel.add(new JLabel("Rate:"));
        dialogPanel.add(new JLabel("\u20B1" + String.format("%.2f", hourlyRate) + "/hour"));
        dialogPanel.add(totalLabel);
        int dialogResult = JOptionPane.showConfirmDialog(this, dialogPanel, "Edit " + selectedPcNumber, JOptionPane.OK_CANCEL_OPTION);
        if (dialogResult != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            // Reset the timer from now without charging again.
            int chosenHours = (Integer) hoursComboBox.getSelectedItem();
            pcDataAccess.editSession(selectedPcId, chosenHours);
            refreshPcTiles();
        } catch (SQLException sqlException) {
            JOptionPane.showMessageDialog(this, sqlException.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleExtendAction() {
        // Extend requires a selection and an In Use PC, extra hours are charged.
        if (selectedPcId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a PC first.");
            return;
        }
        Object[] selectedRow = findSelectedRow();
        if (selectedRow == null || !"In Use".equals((String) selectedRow[3])) {
            JOptionPane.showMessageDialog(this, selectedPcNumber + " is not in use.");
            return;
        }
        // Show the current countdown so staff can tell the customer what is left.
        Timestamp endTime = (Timestamp) selectedRow[4];
        String currentCountdown = endTime == null ? "Available" : buildCountdownText(endTime);
        JLabel currentLabel = new JLabel("Current: " + currentCountdown);
        Integer[] hourOptions = {1, 2, 3, 4, 5, 6};
        JComboBox<Integer> hoursComboBox = new JComboBox<>(hourOptions);
        double hourlyRate = selectedPcRate.doubleValue();
        JLabel addAmountLabel = new JLabel("Add: \u20B1" + String.format("%.2f", hourlyRate * 1));
        hoursComboBox.addActionListener(actionEvent -> {
            int chosenHours = (Integer) hoursComboBox.getSelectedItem();
            addAmountLabel.setText("Add: \u20B1" + String.format("%.2f", chosenHours * hourlyRate));
        });
        JPanel dialogPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        dialogPanel.add(currentLabel);
        dialogPanel.add(new JLabel("Add:"));
        dialogPanel.add(hoursComboBox);
        dialogPanel.add(addAmountLabel);
        int dialogResult = JOptionPane.showConfirmDialog(this, dialogPanel, "Extend " + selectedPcNumber, JOptionPane.OK_CANCEL_OPTION);
        if (dialogResult != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            // Push the end time forward then add the extra payment to sales.
            int chosenHours = (Integer) hoursComboBox.getSelectedItem();
            pcDataAccess.extendSession(selectedPcId, chosenHours);
            todaysSales += chosenHours * hourlyRate;
            updateSalesDisplay();
            refreshPcTiles();
        } catch (SQLException sqlException) {
            JOptionPane.showMessageDialog(this, sqlException.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleStopAction() {
        // Stop requires a selection and an In Use PC, then asks for confirmation.
        if (selectedPcId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a PC first.");
            return;
        }
        Object[] selectedRow = findSelectedRow();
        if (selectedRow == null || !"In Use".equals((String) selectedRow[3])) {
            JOptionPane.showMessageDialog(this, selectedPcNumber + " is not in use.");
            return;
        }
        // Build the confirm message from the live countdown text.
        Timestamp endTime = (Timestamp) selectedRow[4];
        String currentCountdown = endTime == null ? "Available" : buildCountdownText(endTime);
        String confirmMessage;
        if ("Time\u2019s up".equals(currentCountdown)) {
            confirmMessage = "Customer\u2019s time is already up. End session now?";
        } else {
            confirmMessage = "Customer still has " + currentCountdown + " remaining. End session now?";
        }
        int dialogResult = JOptionPane.showConfirmDialog(this, confirmMessage, "Stop " + selectedPcNumber, JOptionPane.YES_NO_OPTION);
        if (dialogResult != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            // Free the PC and clear the selection so another tile must be picked.
            pcDataAccess.stopSession(selectedPcId);
            selectedPcId = -1;
            selectedPcNumber = null;
            selectedPcRate = null;
            refreshPcTiles();
        } catch (SQLException sqlException) {
            JOptionPane.showMessageDialog(this, sqlException.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleNotifyAction() {
        // Notify works on any selected PC and only flags the row for the UserScreen poll.
        if (selectedPcId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a PC first.");
            return;
        }
        try {
            // Flag the PC, its UserScreen picks this up within one second.
            pcDataAccess.sendNotification(selectedPcId);
            JOptionPane.showMessageDialog(this, selectedPcNumber + " has been notified.", "Notify", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException sqlException) {
            JOptionPane.showMessageDialog(this, sqlException.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        // Standalone test entry point for the admin dashboard.
        SwingUtilities.invokeLater(() -> new AdminScreen().setVisible(true));
    }
}
