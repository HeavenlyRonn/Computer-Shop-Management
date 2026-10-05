# Computer Shop Management System

A Java Swing desktop application backed by MySQL that manages timed computer rentals in a small computer shop. Staff at the counter start, extend, and stop sessions on six customer PCs, and each customer PC shows a live countdown of its own remaining time.

- Single login screen that routes to an admin dashboard or a customer timer
- Admin dashboard with six live PC tiles and session controls
- Start / Edit / Extend / Stop actions on every PC
- Notify button that pushes a popup to a specific customer PC
- Live countdown timer with reminder, warning, and time-up popups
- System tray support so the customer timer keeps running when hidden
- Running in-memory sales total for the current shift

## 1. Overview

A computer shop rents PCs by the hour. The shop needs to know which PCs are free, how much time each customer has left, and how much money the current shift has collected. Paper logs and guesswork cause disputes and idle machines.

This system is used by two people. The admin (staff at the counter) controls every PC's timer from one dashboard. The customer (seated at a numbered PC) sees only a small countdown window for their own machine.

There are two roles but one program. The same application is installed on the counter PC and on every customer PC. What opens after login depends on the credentials typed: `admin` opens the dashboard, `PC1`–`PC99` opens that PC's timer. One codebase means one installer, one database configuration, and no separate client/server deployment to maintain.

## 2. Scope and Limitations

### In Scope

- Admin login validated against the database (`users` table)
- PC login by pattern (`PC1`/`UserPC1` through `PC99`/`UserPC99`, case-insensitive)
- Admin dashboard with six PC tiles showing status, countdown, and hourly rate
- Start / Edit / Extend / Stop session actions per PC
- Notify button that pushes a popup to a specific PC
- Live countdown timer on the customer PC, refreshed every second
- Popups at 30 minutes remaining, 5 minutes remaining, and time-up
- System tray hide/restore on the customer PC (timer keeps running while hidden)
- In-memory running sales total for the current shift

### Out of Scope (Limitations)

- No session history or reports (the sales total resets when the app closes)
- No customer records or member accounts
- No POS integration, no printing receipts
- No remote control of customer PCs (popups only inform; nothing shuts down, sleeps, or logs off)
- No account management for admin users (single seeded admin)
- No password hashing (passwords stored as plain text — noted as a future improvement)
- No multi-branch or multi-shop support
- No sound alerts

## 3. System Architecture

The login screen is a router. It checks admin credentials against the database first; if that fails, it checks the PC pattern in code. A match opens either `AdminScreen` or `UserScreen` and closes the login window.

Both screens share the same MySQL database directly. There is no networking layer, no application server, and no client-server protocol — every installed copy connects to `comshop_db` with JDBC.

Because there is no push channel, both screens poll. Each runs a `javax.swing.Timer` that re-queries the database every 1 second: the admin dashboard refreshes all six tiles, and the customer window refreshes its own countdown and checks for pending notifications.

Code is layered by package: `ui/` holds the screens, `database/` holds all SQL (the DAO classes), `Main.java` is the entry point, and `ui/components/` is reserved for shared UI helpers.

```text
Main.java
    |
    v
LoginScreen
    |
    +--> AdminScreen ---> PCDAO ---> MySQL (comshop_db)
    |
    +--> UserScreen  ---> PCDAO ---> MySQL (comshop_db)
```

## 4. Database Design

Database name: `comshop_db`.

```sql
CREATE TABLE users (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(50) NOT NULL,
    role     VARCHAR(20) NOT NULL DEFAULT 'admin'
);

CREATE TABLE pcs (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    pc_number      VARCHAR(10)   NOT NULL UNIQUE,
    hourly_rate    DECIMAL(10,2) NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'Available',
    end_time       DATETIME      NULL,
    notify_pending TINYINT(1)    NOT NULL DEFAULT 0
);
```

Seeded data:

```sql
INSERT INTO users (username, password, role)
VALUES ('admin', 'admin123', 'admin');

INSERT INTO pcs (pc_number, hourly_rate, status, end_time) VALUES
('PC1', 20.00, 'Available', NULL),
('PC2', 20.00, 'Available', NULL),
('PC3', 20.00, 'Available', NULL),
('PC4', 25.00, 'Available', NULL),
('PC5', 25.00, 'Available', NULL),
('PC6', 30.00, 'Available', NULL);
```

Design decisions:

- **Only two tables.** The system tracks PCs and one login, nothing else, so two tables hold everything it needs.
- **No sessions table.** Timer state lives on the `pcs` row itself (`status` + `end_time`). There is no history requirement, so a separate session record per rental would add writes and joins with no reader.
- **No members table.** Customer identity is unnecessary because the customer watches their own timer; nothing in any screen or report is keyed by who sat down.
- **`notify_pending` is a column on `pcs`, not its own table.** It is a single boolean flag per PC with a strict one-producer (admin sets it) / one-consumer (that PC's timer clears it) lifecycle, so a separate table would only add a join to every 1-second poll.

The schema is in third normal form (3NF): each table has a single auto-increment primary key, every non-key column depends on that key (a PC's rate, status, end time, and flag describe the PC; a user's password and role describe the user), and there are no transitive dependencies or repeating groups.

## 5. Login Rules

| Input                                                        | Result                          |
|--------------------------------------------------------------|---------------------------------|
| admin / admin123                                             | AdminScreen                     |
| PC1 / UserPC1 (PC1 through PC99, case-insensitive)           | UserScreen for that PC          |
| anything else                                                | "Invalid username or password." |

- Admin credentials go through the database via `UserDAO.validateLogin`, which returns the `role` column on a match and `null` otherwise.
- PC logins are validated in code with the regex `PC([1-9]|[1-9][0-9])` against the uppercased username, plus the rule that the uppercased password must equal `"USER"` plus the uppercased username. `PC0` and `PC100` are rejected by the regex.
- PC logins are NOT rows in the `users` table. They are hardware units (numbered machines), not accounts, so no database row is needed to recognize them.

## 6. User Flow — Real-World Scenario

1. Customer walks in and asks for an available PC.
2. Staff says "PC5".
3. Customer pays (e.g., PHP 50 = 2 hours at PHP 25/hour).
4. Staff opens AdminScreen, clicks PC5, clicks Start, picks 2 hours.
5. `PCDAO.startSession` runs: `status = 'In Use'`, `end_time = NOW() + 2 hours`.
6. The UserScreen on PC5 polls the database, sees `end_time`, and starts counting down from 2:00:00.
7. At 30 minutes remaining: a translucent popup appears on PC5.
8. At 5 minutes remaining: a final warning popup appears.
9. At 0: the timer shows 0:00:00 and a "Time's Up" popup fires.
10. Customer either pays again (staff clicks Extend, which adds hours) or leaves (staff clicks Stop, which sets status back to Available).
11. Admin can also click Notify at any time to send a popup to a specific PC.

## 7. Screen-by-Screen Explanation

### 7.1 LoginScreen

A 420x320 centered window titled "Computer Shop Management System — Login". It holds a bold header label, a Username field (15 columns), a Password field (15 columns), a full-width Login button, and a smaller Exit button below it. Pressing Enter anywhere triggers Login, since it is the window's default button.

Typing credentials and clicking Login (or pressing Enter) runs the login rules from Section 5: admin opens the dashboard, a PC pattern opens that PC's timer, anything else shows "Invalid username or password." and stays on the screen. The Exit button closes the application.

### 7.2 AdminScreen

A 900x620 centered dashboard titled "Computer Shop Management System — Admin Dashboard". The top shows an "Admin Dashboard" heading. The center is a 2-by-3 grid of six PC tiles, one per row in the `pcs` table ordered by PC number. Each tile shows the PC number (bold), the live status ("Available", a countdown like `1:23:45`, or "Time's up"), and the rate (`₱20.00/hr`). Available tiles are green, In Use tiles are red, and the selected tile gets a thicker black border.

Clicking a tile selects it; clicking the same tile again or clicking empty grid space deselects it. The bottom bar holds five buttons and the shift sales label ("Today's sales: ₱0.00"):

- **Start** — needs an Available selected PC. A dialog offers 1–6 hours with a live total (`hours × rate`). On OK, the timer starts from now and the total is added to sales.
- **Edit** — needs an In Use selected PC. Same hours dialog titled Edit. On OK, the timer resets from now (a correction, so sales do not change).
- **Extend** — needs an In Use selected PC. The dialog shows the current countdown and an hours picker with a live added amount. On OK, the hours are added onto the existing end time and added to sales.
- **Stop** — needs an In Use selected PC. A confirm dialog quotes the remaining time. On Yes, the PC returns to Available and the selection clears.
- **Notify** — works on any selected PC. It flags the row and confirms "PCx has been notified."; that PC's timer shows a popup within one second. Tiles and sales are untouched.

Every second, a timer re-reads all PCs and repaints tile texts and colors in place.

### 7.3 UserScreen

A 340x220 centered window titled with its PC number (e.g., "Computer Shop Management System — PC5"). The top-right has a small "Preview ▾" link that opens a demo menu with the three popup styles (for testing only). The center shows a huge 48pt countdown (`h:mm:ss`, or `00:00:00` with no active session). The bottom notes that closing keeps the timer running.

Every second it re-reads its own row: Available shows `00:00:00` and resets its reminder flags; In Use shows the live countdown and fires each popup once per session (30-minute reminder, 5-minute warning, time-up notice), plus any counter notification the admin pushed.

Clicking X does not exit — it hides the window to the system tray, where a PC icon offers "Show Timer" (also on double-click) and "Exit". The timer keeps polling while hidden, so popups still appear. If the tray is unsupported, X exits instead.

## 8. Code Walkthrough

### Main.java

- Purpose: Application entry point that always opens the login screen.
- Key methods: `main(args)` — shows `LoginScreen` on the Swing event thread via `SwingUtilities.invokeLater`.

### database/DBConnection.java

- Purpose: Central MySQL connection factory used by every DAO.
- Key methods: `openConnection()` — returns a fresh `DriverManager` connection to `comshop_db` (caller closes it via try-with-resources).
- Design choice: a new connection per call keeps DAO methods stateless and safe to use with try-with-resources; credentials are `SCREAMING_SNAKE_CASE` constants in one place.

### database/UserDAO.java

- Purpose: All `users`-table queries; used only by the admin login.
- Key methods: `validateLogin(username, password)` — runs the role lookup with `?` placeholders and returns the role or `null`.
- Design choice: `try-with-resources` on connection, statement, and result set guarantees cleanup; `null` return doubles as "no match" and "database error" (the error is printed for debugging).

### database/PCDAO.java

- Purpose: All `pcs`-table queries; the only class that touches PC timers and flags.
- Methods and their SQL:
  - `getAllPCs` — `SELECT id, pc_number, hourly_rate, status, end_time FROM pcs ORDER BY pc_number`; returns one `Object[]` per row.
  - `startSession(pcId, hours)` — `UPDATE pcs SET status = 'In Use', end_time = TIMESTAMPADD(HOUR, ?, NOW()) WHERE id = ?`.
  - `extendSession(pcId, hours)` — `UPDATE pcs SET end_time = TIMESTAMPADD(HOUR, ?, end_time) WHERE id = ?` (status untouched).
  - `editSession(pcId, hours)` — same SQL as `startSession` (resets the timer from now).
  - `stopSession(pcId)` — `UPDATE pcs SET status = 'Available', end_time = NULL WHERE id = ?`.
  - `getPCByNumber(pcNumber)` — `SELECT ... FROM pcs WHERE pc_number = ?`; single row or `null`.
  - `sendNotification(pcId)` — `UPDATE pcs SET notify_pending = 1 WHERE id = ?`.
  - `hasPendingNotification(pcId)` — `SELECT notify_pending FROM pcs WHERE id = ?`; true iff `1`.
  - `clearNotification(pcId)` — `UPDATE pcs SET notify_pending = 0 WHERE id = ?`.
- Design choice: `List<Object[]>` rows avoid a model class; every statement uses `?` placeholders and try-with-resources; SQL lives only here, never in the screens.

### ui/LoginScreen.java

- Purpose: Unified login form that routes to the dashboard or a PC timer.
- Key methods: `LoginScreen()` (constructor — builds the GridBag form, wires buttons, sets Login as the default button); `handleLogin()` — database check for admin, regex check for PCs, error dialog otherwise; `main(args)` — standalone launcher.
- Design choice: admin is checked against the database first so only the seeded admin can reach the dashboard; PC credentials never touch the database; the default-button setup means Enter always means Login, never Exit.

### ui/AdminScreen.java

- Purpose: Counter dashboard with the six-PC grid, session buttons, and shift sales.
- Key methods: `AdminScreen()` (constructor); `buildPcTiles()` (creates tiles and click forwarding once); `refreshPcTiles()` (1-second DB refresh of texts/colors); `refreshTileBorders()` (single source for selection borders); `selectPc(...)` (toggle select/deselect); `findSelectedRow()` (cached status lookup); `formatRemainingTime(...)` (millis to `h:mm:ss`); `buildCountdownText(...)` (countdown or "Time's up" for dialogs); `updateSalesDisplay()`; `handleStartAction()`, `handleEditAction()`, `handleExtendAction()`, `handleStopAction()`, `handleNotifyAction()`; `main(args)`.
- Design choice: tiles are built once and updated in place to avoid flicker; selection is id/number/rate fields (no model class); sales is a plain `double` that dies with the process, per spec.

### ui/UserScreen.java

- Purpose: Customer countdown window with popups and tray behavior.
- Key methods: `UserScreen(pcNumber)` (constructor — resolves its row, builds the UI, sets up the tray, starts the 1-second timer); `refreshTimerTick()` (polls its row, updates the label, fires one-time popups, handles notifications); `formatDuration(...)` (millis to `h:mm:ss`); `formatDurationLong(...)` (millis to words like "1 hour 23 minutes"); `showTranslucentPopup(...)` (shared bottom-right dialog, 0.85 opacity with opaque fallback, auto-close, X button); `showReminderPopup(...)`, `showFiveMinuteWarning()`, `showTimeUpPopup()`, `showNotificationPopup(...)`; `configureSystemTray()`; `main(args)`.
- Popup system: all four popups share one undecorated, always-on-top 320x140 dialog (yellow background, black border, title plus X, HTML-wrapped message) parked 20px from the screen's bottom-right corner, auto-closing after 5 seconds.
- Once-only flags: `thirtyMinuteReminderShown` fires when remaining time first drops to 30 minutes or less (but above 5); `fiveMinWarningShown` fires at 5 minutes or less; `timeUpShown` fires at zero. All reset when the PC returns to Available, so the next session warns again.
- System tray: a runtime-drawn 16x16 icon (no image file) with Show Timer/Exit menu; X hides the window while the timer keeps polling; double-click or Show Timer restores it.

## 9. Key Concepts Used

- **JDBC (Java Database Connectivity).** The standard Java API for talking to relational databases. The app uses it to open MySQL connections, send SQL, and read result rows — no framework or ORM involved.
- **PreparedStatement and why it prevents SQL injection.** A statement with `?` placeholders where values are bound separately, so user input is never pasted into SQL text. A username like `' OR '1'='1` is treated as a literal string, not code.
- **try-with-resources and automatic resource cleanup.** Declaring connections, statements, and result sets in `try (...)` closes them automatically, even on exceptions. This prevents connection leaks that would eventually lock up the database.
- **DAO pattern (Data Access Object) and separation of concerns.** All SQL lives in `UserDAO`/`PCDAO`; screens only call methods like `startSession`. Either layer can change (new dialog, new query) without touching the other.
- **Event Dispatch Thread (EDT) and SwingUtilities.invokeLater.** Swing requires all UI work on one dedicated thread. Entry points wrap window creation in `invokeLater` so construction always happens there, avoiding race conditions and frozen windows.
- **Swing timers for polling.** A `javax.swing.Timer` fires an action on the EDT every N milliseconds (here 1000). Each tick re-queries the database and repaints, which keeps every machine in sync without any networking code.
- **Normalization and 3NF (brief).** Organizing tables so each fact is stored once: a primary key per table, non-key columns describing only that key, no duplicated or derivable data. The two-table design meets third normal form because PC facts live on the PC row and user facts on the user row.

## 10. How to Run

Prerequisites:

- Java JDK 17 or later
- XAMPP (or MySQL) with database `comshop_db` created
- `mysql-connector-j` jar inside `lib/`

Steps:

1. Start MySQL in XAMPP.
2. Open phpMyAdmin and run the CREATE TABLE + INSERT statements from Section 4.
3. Open the project folder in VS Code.
4. Run `src/Main.java`.
5. Log in as `admin`/`admin123` or `PC1`/`UserPC1`.
6. (Optional) To simulate multiple PCs, open additional terminals or VS Code windows and run `Main.java` again with a different PC login.

```bash
javac -cp "lib/mysql-connector-j-26.7.0.jar" -d bin src/Main.java src/database/*.java src/ui/*.java
java -cp "bin:lib/mysql-connector-j-26.7.0.jar" Main
```

## 11. Future Improvements

- Hash admin passwords
- Add a session history table for reports
- Add a daily sales report screen
- Add member accounts with prepaid load
- Add sound alerts for the popups
- Add a proper installer / jpackage build
- Replace polling with WebSocket or similar push (for multi-branch)

## 12. Credits

Developed by <your name here> — <school> — <date>.
