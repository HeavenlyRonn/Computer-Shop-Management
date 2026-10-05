package ui;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Font;
import database.UserDAO;

// Unified login screen for the Computer Shop Management System.
// Admin credentials are checked against the database, PC logins are checked in code.
@SuppressWarnings("serial")
public class LoginScreen extends JFrame {

    private JTextField usernameField;
    private JPasswordField passwordField;

    public LoginScreen() {
        // Configure the login window frame.
        super("Computer Shop Management System \u2014 Login");
        setSize(420, 320);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());

        // Shared constraints object reused for every component below.
        GridBagConstraints gridConstraints = new GridBagConstraints();
        gridConstraints.insets = new Insets(8, 8, 8, 8);
        gridConstraints.fill = GridBagConstraints.HORIZONTAL;

        // Header title spanning both columns at the top.
        JLabel headerLabel = new JLabel("Computer Shop Management System");
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        headerLabel.setHorizontalAlignment(JLabel.CENTER);
        gridConstraints.gridx = 0;
        gridConstraints.gridy = 0;
        gridConstraints.gridwidth = 2;
        add(headerLabel, gridConstraints);

        // Username row.
        gridConstraints.gridwidth = 1;
        gridConstraints.gridx = 0;
        gridConstraints.gridy = 1;
        add(new JLabel("Username:"), gridConstraints);

        usernameField = new JTextField(15);
        gridConstraints.gridx = 1;
        gridConstraints.gridy = 1;
        add(usernameField, gridConstraints);

        // Password row.
        gridConstraints.gridx = 0;
        gridConstraints.gridy = 2;
        add(new JLabel("Password:"), gridConstraints);

        passwordField = new JPasswordField(15);
        gridConstraints.gridx = 1;
        gridConstraints.gridy = 2;
        add(passwordField, gridConstraints);

        JButton loginButton = new JButton("Login");
        gridConstraints.gridx = 0;
        gridConstraints.gridy = 3;
        gridConstraints.gridwidth = 2;
        add(loginButton, gridConstraints);

        JButton exitButton = new JButton("Exit");
        gridConstraints.gridx = 0;
        gridConstraints.gridy = 4;
        gridConstraints.gridwidth = 2;
        add(exitButton, gridConstraints);

        // Login click or Enter (via the default button) attempts a login.
        loginButton.addActionListener(event -> handleLogin());
        exitButton.addActionListener(event -> System.exit(0));

        // Make Enter anywhere trigger Login, never Exit.
        getRootPane().setDefaultButton(loginButton);
    }

    private void handleLogin() {
        // Read and trim both fields so surrounding spaces do not break login.
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        // Check the database first to see if these are admin credentials.
        String role = new UserDAO().validateLogin(username, password);
        if ("admin".equals(role)) {
            // Admin match found, close login and show the admin dashboard.
            dispose();
            new AdminScreen().setVisible(true);
            return;
        } else if (username.toUpperCase().matches("PC([1-9]|[1-9][0-9])")
                && password.toUpperCase().equals("USER" + username.toUpperCase())) {
            // PC pattern match (PC1-PC99 with password User + name), open the small timer window.
            dispose();
            new UserScreen(username.toUpperCase()).setVisible(true);
            return;
        } else {
            // Anything else is rejected with a popup message.
            JOptionPane.showMessageDialog(this, "Invalid username or password.");
        }
    }

    public static void main(String[] args) {
        // Launch the login screen on the Swing event thread for thread safety.
        SwingUtilities.invokeLater(() -> new LoginScreen().setVisible(true));
    }
}
