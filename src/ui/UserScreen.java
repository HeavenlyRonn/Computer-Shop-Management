package ui;

import database.PCDAO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

// Customer countdown timer with translucent popups and system tray behavior.
// Preview button is for demo purposes only and would be hidden in production.
public class UserScreen extends JFrame {

    private String pcNumber;
    private int pcId;
    private BigDecimal hourlyRate;

    private JLabel timerLabel;
    private JLabel hintLabel;
    private JButton previewButton;

    private Timer refreshTimer;
    private boolean thirtyMinuteReminderShown = false;
    private boolean fiveMinWarningShown = false;
    private boolean timeUpShown = false;

    private TrayIcon trayIcon;
    private boolean trayIconVisible = false;

    private final PCDAO pcDataAccess = new PCDAO();

    public UserScreen(String pcNumber) {
        // Configure the small customer timer window with the PC number in the title.
        super("Computer Shop Management System \u2014 " + pcNumber);
        setSize(340, 220);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(new Color(240, 240, 240));
        setLayout(new BorderLayout());
        this.pcNumber = pcNumber;

        // Resolve this PC's database row before building anything else.
        try {
            Object[] pcRow = pcDataAccess.getPCByNumber(pcNumber);
            if (pcRow == null) {
                JOptionPane.showMessageDialog(this, "PC not found in database.");
                dispose();
                return;
            }
            pcId = (Integer) pcRow[0];
            hourlyRate = (BigDecimal) pcRow[2];
        } catch (SQLException sqlException) {
            // Print the failure and close, the timer cannot run without the row.
            sqlException.printStackTrace();
            JOptionPane.showMessageDialog(this, "PC not found in database.");
            dispose();
            return;
        }

        // North bar holds the small preview link aligned to the right.
        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        northPanel.setBackground(new Color(240, 240, 240));
        previewButton = new JButton("Preview \u25BE");
        // Style the button to look like a small link for demo use.
        previewButton.setBorderPainted(false);
        previewButton.setContentAreaFilled(false);
        previewButton.setFocusPainted(false);
        previewButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        previewButton.setForeground(Color.BLUE);
        northPanel.add(previewButton);
        add(northPanel, BorderLayout.NORTH);

        // Center panel shows the huge countdown text.
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(new Color(240, 240, 240));
        timerLabel = new JLabel("00:00:00", JLabel.CENTER);
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 48));
        centerPanel.add(timerLabel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // South hint explains that closing keeps the timer running.
        hintLabel = new JLabel("Closing this window keeps the timer running in the background.", JLabel.CENTER);
        hintLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        hintLabel.setForeground(Color.GRAY);
        add(hintLabel, BorderLayout.SOUTH);

        // Preview menu offers the three popup styles with dummy values, no database writes.
        JPopupMenu previewMenu = new JPopupMenu();
        JMenuItem reminderItem = new JMenuItem("Show 30-min Reminder");
        JMenuItem warningItem = new JMenuItem("Show 5-min Warning");
        JMenuItem timeUpItem = new JMenuItem("Show Time\u2019s Up");
        previewMenu.add(reminderItem);
        previewMenu.add(warningItem);
        previewMenu.add(timeUpItem);
        reminderItem.addActionListener(actionEvent -> showReminderPopup(30L * 60L * 1000L));
        warningItem.addActionListener(actionEvent -> showFiveMinuteWarning());
        timeUpItem.addActionListener(actionEvent -> showTimeUpPopup());
        previewButton.addActionListener(actionEvent -> previewMenu.show(previewButton, 0, previewButton.getHeight()));

        // Hide to tray on close so the timer keeps running in the background.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (SystemTray.isSupported() && trayIconVisible) {
                    // Hide the window, the refresh timer keeps firing while hidden.
                    setVisible(false);
                } else {
                    // No tray available, fall back to a normal exit.
                    System.exit(0);
                }
            }
        });

        configureSystemTray();

        // Poll the database every second for countdown and notification updates.
        refreshTimer = new Timer(1000, actionEvent -> refreshTimerTick());
        refreshTimer.start();
    }

    private void refreshTimerTick() {
        try {
            // Re-fetch this PC's latest status and end time on every tick.
            Object[] pcRow = pcDataAccess.getPCByNumber(pcNumber);
            if (pcRow == null) {
                return;
            }
            String currentStatus = (String) pcRow[3];
            Timestamp endTime = (Timestamp) pcRow[4];
            long currentTimeMillis = System.currentTimeMillis();
            if ("Available".equals(currentStatus) || endTime == null) {
                // No active session, show idle text and reset all one-time flags.
                timerLabel.setText("00:00:00");
                thirtyMinuteReminderShown = false;
                fiveMinWarningShown = false;
                timeUpShown = false;
            } else if ("In Use".equals(currentStatus)) {
                long remainingMillis = endTime.getTime() - currentTimeMillis;
                if (remainingMillis <= 0) {
                    // Timer expired, show zero and fire the final popup only once.
                    timerLabel.setText("0:00:00");
                    if (!timeUpShown) {
                        showTimeUpPopup();
                        timeUpShown = true;
                    }
                } else {
                    timerLabel.setText(formatDuration(remainingMillis));
                    // Fire the 5-minute warning only once per session.
                    if (remainingMillis <= 5L * 60L * 1000L && !fiveMinWarningShown) {
                        showFiveMinuteWarning();
                        fiveMinWarningShown = true;
                    }
                    // Fire the 30-minute reminder once when 30 minutes remain, not before or again.
                    if (!thirtyMinuteReminderShown
                            && remainingMillis <= 30L * 60L * 1000L
                            && remainingMillis > 5L * 60L * 1000L) {
                        showReminderPopup(remainingMillis);
                        thirtyMinuteReminderShown = true;
                    }
                }
            }
            // Check for a counter notice even when the timer is idle.
            if (pcDataAccess.hasPendingNotification(pcId)) {
                long notificationRemaining = 0;
                if (endTime != null && "In Use".equals(currentStatus)) {
                    notificationRemaining = endTime.getTime() - System.currentTimeMillis();
                    if (notificationRemaining < 0) {
                        notificationRemaining = 0;
                    }
                }
                showNotificationPopup(notificationRemaining);
                pcDataAccess.clearNotification(pcId);
            }
        } catch (SQLException sqlException) {
            // Keep ticking through database errors so the timer recovers by itself.
            sqlException.printStackTrace();
        }
    }

    private String formatDuration(long remainingMillis) {
        // Convert milliseconds to h:mm:ss with no leading zero on hours.
        long hours = remainingMillis / 3600000L;
        long minutes = (remainingMillis % 3600000L) / 60000L;
        long seconds = (remainingMillis % 60000L) / 1000L;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    private String formatDurationLong(long remainingMillis) {
        // Friendly wording without seconds for reminder and notice popups.
        if (remainingMillis < 60000L) {
            return "less than a minute";
        }
        long totalMinutes = remainingMillis / 60000L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        String hourPart = "";
        String minutePart = "";
        if (hours == 1) {
            hourPart = "1 hour";
        } else if (hours > 1) {
            hourPart = hours + " hours";
        }
        if (minutes == 1) {
            minutePart = "1 minute";
        } else if (minutes > 1 || hours == 0) {
            minutePart = minutes + " minutes";
        }
        if (!hourPart.isEmpty() && !minutePart.isEmpty()) {
            return hourPart + " " + minutePart;
        } else if (!hourPart.isEmpty()) {
            return hourPart;
        }
        return minutePart;
    }

    private void showTranslucentPopup(String popupTitle, String popupMessage, int autoCloseSeconds) {
        // Shared bottom-right popup used by all four notification types.
        JDialog popupDialog = new JDialog(this);
        popupDialog.setUndecorated(true);
        popupDialog.setAlwaysOnTop(true);
        popupDialog.setSize(320, 140);
        // Anchor the popup 20px from the bottom-right screen edges.
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        popupDialog.setLocation(screenSize.width - popupDialog.getWidth() - 20, screenSize.height - popupDialog.getHeight() - 20);
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        contentPanel.setBackground(new Color(255, 250, 205));
        // North row holds the title on the left and a small X on the right.
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(new Color(255, 250, 205));
        JLabel titleLabel = new JLabel(popupTitle);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        JButton closeButton = new JButton("X");
        closeButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(actionEvent -> popupDialog.dispose());
        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(closeButton, BorderLayout.EAST);
        contentPanel.add(titlePanel, BorderLayout.NORTH);
        // Wrap long messages in HTML so they fit inside the fixed dialog size.
        JLabel messageLabel = new JLabel("<html><body style='width:280px'>" + popupMessage + "</body></html>");
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        contentPanel.add(messageLabel, BorderLayout.CENTER);
        popupDialog.add(contentPanel);
        popupDialog.setVisible(true);
        try {
            // Apply translucency after showing, fall back to opaque when unsupported.
            popupDialog.setOpacity(0.85f);
        } catch (UnsupportedOperationException unsupportedException) {
            // Leave the dialog fully opaque on platforms without translucency.
        }
        // Auto-dismiss after the requested number of seconds.
        Timer closeTimer = new Timer(autoCloseSeconds * 1000, actionEvent -> popupDialog.dispose());
        closeTimer.setRepeats(false);
        closeTimer.start();
    }

    private void showReminderPopup(long remainingMillis) {
        // Periodic reminder showing hours and minutes left, no seconds.
        showTranslucentPopup("Time Reminder", "You have " + formatDurationLong(remainingMillis) + " left.", 5);
    }

    private void showFiveMinuteWarning() {
        // Final warning shortly before the session expires.
        showTranslucentPopup("Time Reminder", "5 minutes remaining. Please save your work.", 5);
    }

    private void showTimeUpPopup() {
        // Expiry notice directing the customer to the counter.
        showTranslucentPopup("Time\u2019s Up", "Your time is up. Please proceed to the counter.", 5);
    }

    private void showNotificationPopup(long remainingMillis) {
        // Counter notice, shows less than a minute when no session is active.
        showTranslucentPopup("Notice from Counter", "You have " + formatDurationLong(remainingMillis) + " remaining.", 5);
    }

    private void configureSystemTray() {
        // Only configure the tray icon when the platform supports it.
        if (!SystemTray.isSupported()) {
            return;
        }
        try {
            // Draw a tiny 16x16 icon at runtime so no image file is needed.
            BufferedImage trayImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D imageGraphics = trayImage.createGraphics();
            imageGraphics.setColor(new Color(44, 62, 80));
            imageGraphics.fillRoundRect(0, 0, 16, 16, 4, 4);
            imageGraphics.setColor(Color.WHITE);
            imageGraphics.setFont(new Font("SansSerif", Font.BOLD, 9));
            imageGraphics.drawString("PC", 2, 12);
            imageGraphics.dispose();
            trayIcon = new TrayIcon(trayImage, "Computer Shop Management System \u2014 " + pcNumber);
            trayIcon.setImageAutoSize(true);
            // Right-click menu offers Show Timer and Exit actions.
            PopupMenu trayMenu = new PopupMenu();
            MenuItem showItem = new MenuItem("Show Timer");
            MenuItem exitItem = new MenuItem("Exit");
            showItem.addActionListener(actionEvent -> {
                setVisible(true);
                toFront();
            });
            exitItem.addActionListener(actionEvent -> System.exit(0));
            trayMenu.add(showItem);
            trayMenu.add(exitItem);
            trayIcon.setPopupMenu(trayMenu);
            // Double-click restores the hidden timer window.
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent mouseEvent) {
                    if (mouseEvent.getClickCount() == 2) {
                        setVisible(true);
                        toFront();
                    }
                }
            });
            SystemTray.getSystemTray().add(trayIcon);
            trayIconVisible = true;
        } catch (java.awt.AWTException trayException) {
            // Leave the tray hidden and fall back to normal close behavior.
            trayException.printStackTrace();
            trayIconVisible = false;
        }
    }

    public static void main(String[] args) {
        // Standalone test entry point showing the PC1 timer window.
        SwingUtilities.invokeLater(() -> new UserScreen("PC1").setVisible(true));
    }
}
