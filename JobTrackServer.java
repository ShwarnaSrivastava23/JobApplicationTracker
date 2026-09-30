
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JobTrackServer {

    // =========================
    // LOGIN SESSIONS
    // =========================
    private static final Map<String, Integer> sessions
            = new ConcurrentHashMap<>();

    private static final Map<String, String> sessionNames
            = new ConcurrentHashMap<>();

    // =========================
    // MAIN
    // =========================
    public static void main(String[] args) throws Exception {

        setupDatabase();

        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0
        );

        server.createContext("/", JobTrackServer::home);
        server.createContext("/login", JobTrackServer::login);
        server.createContext("/signup", JobTrackServer::signup);
        server.createContext("/logout", JobTrackServer::logout);

        server.createContext("/add", JobTrackServer::addApplication);
        server.createContext("/applications", JobTrackServer::getApplications);
        server.createContext("/delete", JobTrackServer::deleteApplication);
        server.createContext("/update", JobTrackServer::updateApplication);
        server.createContext("/history", JobTrackServer::getHistory);

        server.createContext("/style.css", JobTrackServer::serveCss);

        server.start();

        System.out.println("=================================");
        System.out.println("JobTrack Server started!");
        System.out.println("Open: http://localhost:8080/");
        System.out.println("=================================");

        // Keep the server process running.
        Thread.currentThread().join();
    }

    // =========================
    // DATABASE SETUP
    // =========================
    private static void setupDatabase() {

        try (Connection conn = Database.getConnection(); Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        email TEXT UNIQUE NOT NULL,
                        password TEXT NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS applications (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        jobtrack_id TEXT UNIQUE,
                        job_reference_id TEXT,
                        company TEXT NOT NULL,
                        role TEXT NOT NULL,
                        application_date TEXT,
                        status TEXT,
                        location TEXT,
                        job_link TEXT,
                        notes TEXT
                    )
                    """);

            try {
                stmt.execute(
                        "ALTER TABLE applications "
                        + "ADD COLUMN user_id INTEGER"
                );
            } catch (Exception ignored) {
            }

            try {
                stmt.execute(
                        "ALTER TABLE applications "
                        + "ADD COLUMN jobtrack_id TEXT"
                );
            } catch (Exception ignored) {
            }

            try {
                stmt.execute(
                        "ALTER TABLE applications "
                        + "ADD COLUMN job_reference_id TEXT"
                );
            } catch (Exception ignored) {
            }

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS application_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        application_id INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        changed_at TEXT NOT NULL,
                        FOREIGN KEY (application_id)
                        REFERENCES applications(id)
                    )
                    """);

            stmt.execute("""
                    UPDATE applications
                    SET jobtrack_id =
                        'JT-' || printf('%04d', id)
                    WHERE jobtrack_id IS NULL
                       OR jobtrack_id = ''
                    """);

            System.out.println(
                    "Database setup completed successfully!"
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // HOME
    // =========================
    private static void home(HttpExchange exchange)
            throws IOException {

        Integer userId = getLoggedInUserId(exchange);

        if (userId == null) {
            redirect(exchange, "/login");
            return;
        }

        showAddApplicationPage(exchange);
    }

    // =========================
    // LOGIN
    // =========================
    private static void login(HttpExchange exchange)
            throws IOException {

        String query
                = exchange.getRequestURI().getRawQuery();

        String email = getValue(query, "email");
        String password = getValue(query, "password");

        if (!email.isEmpty() && !password.isEmpty()) {

            try (Connection conn = Database.getConnection()) {

                String sql = """
                        SELECT id, name, password
                        FROM users
                        WHERE email = ?
                        """;

                try (PreparedStatement ps
                        = conn.prepareStatement(sql)) {

                    ps.setString(1, email);

                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {

                        int userId
                                = rs.getInt("id");

                        String name
                                = rs.getString("name");

                        String storedPassword
                                = rs.getString("password");

                        String hashedPassword
                                = hashPassword(password);

                        if (storedPassword.equals(hashedPassword)) {

                            String token
                                    = UUID.randomUUID().toString();

                            sessions.put(token, userId);
                            sessionNames.put(token, name);

                            // Give old applications to
                            // the first registered user.
                            assignOldApplications(
                                    conn,
                                    userId
                            );

                            exchange.getResponseHeaders().add(
                                    "Set-Cookie",
                                    "JOBTRACK_SESSION="
                                    + token
                                    + "; Path=/"
                            );

                            redirect(
                                    exchange,
                                    "/applications"
                            );

                            return;
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            showLoginPage(
                    exchange,
                    "Invalid email or password."
            );

            return;
        }

        showLoginPage(exchange, "");
    }

    // =========================
    // SIGN UP
    // =========================
    private static void signup(HttpExchange exchange)
            throws IOException {

        String query
                = exchange.getRequestURI().getRawQuery();

        String name = getValue(query, "name");
        String email = getValue(query, "email");
        String password = getValue(query, "password");

        if (!name.isEmpty()
                && !email.isEmpty()
                && !password.isEmpty()) {

            if (password.length() < 6) {

                showSignupPage(
                        exchange,
                        "Password must contain at least 6 characters."
                );

                return;
            }

            try (Connection conn = Database.getConnection()) {

                String checkSql
                        = "SELECT id FROM users WHERE email = ?";

                try (PreparedStatement check
                        = conn.prepareStatement(checkSql)) {

                    check.setString(1, email);

                    ResultSet rs
                            = check.executeQuery();

                    if (rs.next()) {

                        showSignupPage(
                                exchange,
                                "An account with this email already exists."
                        );

                        return;
                    }
                }

                String sql = """
                        INSERT INTO users
                        (name, email, password)
                        VALUES (?, ?, ?)
                        """;

                int userId;

                try (PreparedStatement ps
                        = conn.prepareStatement(
                                sql,
                                Statement.RETURN_GENERATED_KEYS
                        )) {

                            ps.setString(1, name);
                            ps.setString(2, email);
                            ps.setString(
                                    3,
                                    hashPassword(password)
                            );

                            ps.executeUpdate();

                            ResultSet keys
                                    = ps.getGeneratedKeys();

                            if (!keys.next()) {
                                throw new SQLException(
                                        "Could not create user."
                                );
                            }

                            userId
                                    = keys.getInt(1);
                        }

                        // If this is the first account,
                        // assign existing applications to it.
                        assignOldApplications(
                                conn,
                                userId
                        );

                        String token
                                = UUID.randomUUID().toString();

                        sessions.put(
                                token,
                                userId
                        );

                        sessionNames.put(
                                token,
                                name
                        );

                        exchange.getResponseHeaders().add(
                                "Set-Cookie",
                                "JOBTRACK_SESSION="
                                + token
                                + "; Path=/"
                        );

                        redirect(
                                exchange,
                                "/applications"
                        );

            } catch (Exception e) {

                e.printStackTrace();

                showSignupPage(
                        exchange,
                        "Could not create account: "
                        + e.getMessage()
                );
            }

            return;
        }

        showSignupPage(exchange, "");
    }

    // =========================
    // LOGOUT
    // =========================
    private static void logout(HttpExchange exchange)
            throws IOException {

        String token
                = getSessionToken(exchange);

        if (token != null) {
            sessions.remove(token);
            sessionNames.remove(token);
        }

        exchange.getResponseHeaders().add(
                "Set-Cookie",
                "JOBTRACK_SESSION=; Path=/; Max-Age=0"
        );

        redirect(exchange, "/login");
    }

    // =========================
    // LOGIN PAGE
    // =========================
    private static void showLoginPage(
            HttpExchange exchange,
            String error
    ) throws IOException {

        String errorHtml = "";

        if (!error.isEmpty()) {
            errorHtml
                    = "<div class=\"auth-error\">"
                    + escapeHtml(error)
                    + "</div>";
        }

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>JobTrack - Login</title>
                    <link rel="stylesheet" href="/style.css">
                    <style>
                        body {
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            padding: 24px;
                        }
                        .auth-page {
                            width: 100%;
                            max-width: 430px;
                        }
                        .auth-card {
                            background: white;
                            border: 1px solid #e5e7eb;
                            border-radius: 18px;
                            padding: 34px;
                            box-shadow: 0 15px 40px rgba(15, 23, 42, 0.10);
                        }
                        .brand {
                            text-align: center;
                            margin-bottom: 28px;
                        }
                        .brand-icon {
                            width: 54px;
                            height: 54px;
                            margin: 0 auto 14px;
                            border-radius: 14px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            background: #2563eb;
                            color: white;
                            font-size: 24px;
                            font-weight: 700;
                        }
                        .brand h1 {
                            margin: 0;
                            font-size: 30px;
                        }
                        .brand p {
                            margin-top: 8px;
                            color: #64748b;
                        }
                        .auth-error {
                            background: #fef2f2;
                            color: #b91c1c;
                            border: 1px solid #fecaca;
                            border-radius: 10px;
                            padding: 11px 13px;
                            margin-bottom: 18px;
                            font-size: 14px;
                        }
                        .auth-card input {
                            width: 100%;
                            box-sizing: border-box;
                            margin-bottom: 16px;
                        }
                        .auth-card button {
                            width: 100%;
                            margin-top: 4px;
                        }
                        .auth-footer {
                            text-align: center;
                            margin-top: 22px;
                            color: #64748b;
                        }
                        .auth-footer a {
                            font-weight: 600;
                            text-decoration: none;
                        }
                    </style>
                </head>
                <body>
                    <div class="auth-page">
                        <div class="auth-card">
                            <div class="brand">
                                <div class="brand-icon">JT</div>
                                <h1>JobTrack</h1>
                                <p>Track your job applications in one place.</p>
                            </div>

                            %s

                            <form action="/login" method="GET">
                                <label>Email</label>
                                <input
                                    type="email"
                                    name="email"
                                    placeholder="Enter your email"
                                    autocomplete="email"
                                    required
                                >

                                <label>Password</label>
                                <input
                                    type="password"
                                    name="password"
                                    placeholder="Enter your password"
                                    autocomplete="current-password"
                                    required
                                >

                                <button type="submit">Login</button>
                            </form>

                            <div class="auth-footer">
                                Don't have an account?
                                <a href="/signup">Create Account</a>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """;

        html = html.replace("%s", errorHtml);

        sendHtmlResponse(exchange, html);
    }

    // =========================
    // SIGNUP PAGE
    // =========================
    private static void showSignupPage(
            HttpExchange exchange,
            String error
    ) throws IOException {

        String errorHtml = "";

        if (!error.isEmpty()) {
            errorHtml
                    = "<div class=\"auth-error\">"
                    + escapeHtml(error)
                    + "</div>";
        }

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>JobTrack - Create Account</title>
                    <link rel="stylesheet" href="/style.css">
                    <style>
                        body {
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            padding: 24px;
                        }
                        .auth-page {
                            width: 100%;
                            max-width: 430px;
                        }
                        .auth-card {
                            background: white;
                            border: 1px solid #e5e7eb;
                            border-radius: 18px;
                            padding: 34px;
                            box-shadow: 0 15px 40px rgba(15, 23, 42, 0.10);
                        }
                        .brand {
                            text-align: center;
                            margin-bottom: 28px;
                        }
                        .brand-icon {
                            width: 54px;
                            height: 54px;
                            margin: 0 auto 14px;
                            border-radius: 14px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            background: #2563eb;
                            color: white;
                            font-size: 24px;
                            font-weight: 700;
                        }
                        .brand h1 {
                            margin: 0;
                            font-size: 30px;
                        }
                        .brand p {
                            margin-top: 8px;
                            color: #64748b;
                        }
                        .auth-error {
                            background: #fef2f2;
                            color: #b91c1c;
                            border: 1px solid #fecaca;
                            border-radius: 10px;
                            padding: 11px 13px;
                            margin-bottom: 18px;
                            font-size: 14px;
                        }
                        .auth-card input {
                            width: 100%;
                            box-sizing: border-box;
                            margin-bottom: 16px;
                        }
                        .auth-card button {
                            width: 100%;
                            margin-top: 4px;
                        }
                        .auth-footer {
                            text-align: center;
                            margin-top: 22px;
                            color: #64748b;
                        }
                        .auth-footer a {
                            font-weight: 600;
                            text-decoration: none;
                        }
                        .password-note {
                            display: block;
                            margin-top: -8px;
                            margin-bottom: 16px;
                            color: #64748b;
                            font-size: 12px;
                        }
                    </style>
                </head>
                <body>
                    <div class="auth-page">
                        <div class="auth-card">
                            <div class="brand">
                                <div class="brand-icon">JT</div>
                                <h1>Create Account</h1>
                                <p>Start tracking your job applications.</p>
                            </div>

                            %s

                            <form action="/signup" method="GET">
                                <label>Name</label>
                                <input
                                    type="text"
                                    name="name"
                                    placeholder="Enter your name"
                                    autocomplete="name"
                                    required
                                >

                                <label>Email</label>
                                <input
                                    type="email"
                                    name="email"
                                    placeholder="Enter your email"
                                    autocomplete="email"
                                    required
                                >

                                <label>Password</label>
                                <input
                                    type="password"
                                    name="password"
                                    placeholder="Minimum 6 characters"
                                    autocomplete="new-password"
                                    minlength="6"
                                    required
                                >
                                <span class="password-note">Use at least 6 characters.</span>

                                <button type="submit">Create Account</button>
                            </form>

                            <div class="auth-footer">
                                Already have an account?
                                <a href="/login">Login</a>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """;

        html = html.replace("%s", errorHtml);

        sendHtmlResponse(exchange, html);
    }

    // =========================
    // ADD APPLICATION PAGE
    // =========================
    private static void showAddApplicationPage(
            HttpExchange exchange
    ) throws IOException {

        Integer userId
                = getLoggedInUserId(exchange);

        if (userId == null) {
            redirect(exchange, "/login");
            return;
        }

        String userName
                = getLoggedInUserName(exchange);

        String html = """
                <!DOCTYPE html>
                <html>

                <head>
                    <meta charset="UTF-8">

                    <meta name="viewport"
                          content="width=device-width, initial-scale=1.0">

                    <title>JobTrack - Add Application</title>

                    <link rel="stylesheet"
                          href="/style.css">
                </head>

                <body>

                <div class="container">

                    <div class="top">

                        <div>
                            <h1>JobTrack</h1>

                            <p>
                                Welcome, %s
                            </p>
                        </div>

                        <a
                            class="view-btn"
                            href="/logout"
                        >
                            Logout
                        </a>

                    </div>

                    <div class="info">
                        JobTrack ID will be generated automatically.
                        Example: JT-0001, JT-0002, JT-0003
                    </div>

                    <form action="/add" method="GET">

                        <label>
                            Company Name *
                        </label>

                        <input
                            type="text"
                            name="company"
                            placeholder="Example: Google"
                            required
                        >

                        <label>
                            Job Role *
                        </label>

                        <input
                            type="text"
                            name="role"
                            placeholder="Example: Software Engineer"
                            required
                        >

                        <label>
                            Job ID / Registration ID
                        </label>

                        <input
                            type="text"
                            name="jobReferenceId"
                            placeholder="Example: JOB12345"
                        >

                        <label>
                            Status
                        </label>

                        <select name="status">

                            <option value="Applied">
                                Applied
                            </option>

                            <option value="Shortlisted">
                                Shortlisted
                            </option>

                            <option value="Interview">
                                Interview
                            </option>

                            <option value="Selected">
                                Selected
                            </option>

                            <option value="Rejected">
                                Rejected
                            </option>

                        </select>

                        <label>
                            Location
                        </label>

                        <input
                            type="text"
                            name="location"
                            placeholder="Example: Bangalore"
                        >

                        <label>
                            Job Link
                        </label>

                        <input
                            type="url"
                            name="jobLink"
                            placeholder="https://..."
                        >

                        <label>
                            Notes
                        </label>

                        <textarea
                            name="notes"
                            placeholder="Add any notes about this application..."
                        ></textarea>

                        <button type="submit">
                            Add Application
                        </button>

                        <a
                            class="view-btn"
                            href="/applications"
                        >
                            View Applications
                        </a>

                    </form>

                </div>

                </body>
                </html>
                """.formatted(
                escapeHtml(userName)
        );

        sendHtmlResponse(exchange, html);
    }

    // =========================
    // ADD APPLICATION
    // =========================
    private static void addApplication(
            HttpExchange exchange
    ) throws IOException {

        Integer userId
                = getLoggedInUserId(exchange);

        if (userId == null) {
            redirect(exchange, "/login");
            return;
        }

        try {

            String query
                    = exchange.getRequestURI()
                            .getRawQuery();

            String company
                    = getValue(query, "company");

            String role
                    = getValue(query, "role");

            String jobReferenceId
                    = getValue(query, "jobReferenceId");

            String status
                    = getValue(query, "status");

            String location
                    = getValue(query, "location");

            String jobLink
                    = getValue(query, "jobLink");

            String notes
                    = getValue(query, "notes");

            if (company.isEmpty()
                    || role.isEmpty()) {

                sendHtmlResponse(
                        exchange,
                        "<h2>Company and Job Role are required.</h2>"
                );

                return;
            }

            if (status.isEmpty()) {
                status = "Applied";
            }

            try (Connection conn
                    = Database.getConnection()) {

                int nextId = 1;

                String idSql
                        = "SELECT COALESCE(MAX(id), 0) + 1 "
                        + "FROM applications";

                try (PreparedStatement ps
                        = conn.prepareStatement(idSql)) {

                    ResultSet rs
                            = ps.executeQuery();

                    if (rs.next()) {
                        nextId
                                = rs.getInt(1);
                    }
                }

                String jobTrackId
                        = String.format(
                                "JT-%04d",
                                nextId
                        );

                String sql = """
                        INSERT INTO applications
                        (
                            user_id,
                            jobtrack_id,
                            job_reference_id,
                            company,
                            role,
                            application_date,
                            status,
                            location,
                            job_link,
                            notes
                        )
                        VALUES
                        (
                            ?, ?, ?, ?, ?,
                            date('now'),
                            ?, ?, ?, ?
                        )
                        """;

                int applicationId;

                try (PreparedStatement ps
                        = conn.prepareStatement(
                                sql,
                                Statement.RETURN_GENERATED_KEYS
                        )) {

                            ps.setInt(1, userId);
                            ps.setString(2, jobTrackId);
                            ps.setString(3, jobReferenceId);
                            ps.setString(4, company);
                            ps.setString(5, role);
                            ps.setString(6, status);
                            ps.setString(7, location);
                            ps.setString(8, jobLink);
                            ps.setString(9, notes);

                            ps.executeUpdate();

                            ResultSet keys
                                    = ps.getGeneratedKeys();

                            if (!keys.next()) {
                                throw new SQLException(
                                        "Could not get application ID."
                                );
                            }

                            applicationId
                                    = keys.getInt(1);
                        }

                        addHistory(
                                conn,
                                applicationId,
                                status
                        );

                        redirect(
                                exchange,
                                "/applications"
                        );
            }

        } catch (Exception e) {

            e.printStackTrace();

            sendHtmlResponse(
                    exchange,
                    """
                    <html>
                    <body>

                    <h2>
                        Unable to add application
                    </h2>

                    <p>%s</p>

                    <a href="/">
                        Go Back
                    </a>

                    </body>
                    </html>
                    """.formatted(
                            escapeHtml(
                                    e.getMessage()
                            )
                    )
            );
        }
    }

    // =========================
    // APPLICATIONS
    // =========================
    private static void getApplications(
            HttpExchange exchange
    ) throws IOException {

        Integer userId
                = getLoggedInUserId(exchange);

        if (userId == null) {
            redirect(exchange, "/login");
            return;
        }

        String search
                = getQueryParameter(
                        exchange,
                        "search"
                );

        String statusFilter
                = getQueryParameter(
                        exchange,
                        "status"
                );

        String userName
                = getLoggedInUserName(exchange);

        StringBuilder html
                = new StringBuilder();

        html.append("""
                <!DOCTYPE html>
                <html>

                <head>

                    <meta charset="UTF-8">

                    <meta name="viewport"
                          content="width=device-width, initial-scale=1.0">

                    <title>
                        JobTrack - Applications
                    </title>

                    <link rel="stylesheet"
                          href="/style.css">

                </head>

                <body>

                <div class="container">

                    <div class="top">

                        <div>
                            <h1>
                                JobTrack Applications
                            </h1>

                            <p>
                                Welcome, %s
                            </p>
                        </div>

                        <div>

                            <a
                                class="add-btn"
                                href="/"
                            >
                                + Add Application
                            </a>

                            <a
                                class="view-btn"
                                href="/logout"
                            >
                                Logout
                            </a>

                        </div>

                    </div>

                    <div class="filter-box">

                        <form
                            action="/applications"
                            method="GET"
                        >

                            <input
                                type="text"
                                name="search"
                                placeholder="Search company, role, Job ID or JobTrack ID"
                                value="%s"
                            >

                            <select name="status">

                                <option value="">
                                    All Status
                                </option>

                                <option value="Applied">
                                    Applied
                                </option>

                                <option value="Shortlisted">
                                    Shortlisted
                                </option>

                                <option value="Interview">
                                    Interview
                                </option>

                                <option value="Selected">
                                    Selected
                                </option>

                                <option value="Rejected">
                                    Rejected
                                </option>

                            </select>

                            <button type="submit">
                                Search
                            </button>

                        </form>

                    </div>
                """.formatted(
                escapeHtml(userName),
                escapeHtml(
                        search == null ? "" : search
                )
        ));

        int total = 0;
        int applied = 0;
        int shortlisted = 0;
        int interview = 0;
        int selected = 0;
        int rejected = 0;

        try (Connection conn
                = Database.getConnection()) {

            String dashboardSql = """
                    SELECT
                        COUNT(*) AS total,

                        SUM(
                            CASE
                                WHEN status = 'Applied'
                                THEN 1 ELSE 0
                            END
                        ) AS applied,

                        SUM(
                            CASE
                                WHEN status = 'Shortlisted'
                                THEN 1 ELSE 0
                            END
                        ) AS shortlisted,

                        SUM(
                            CASE
                                WHEN status = 'Interview'
                                THEN 1 ELSE 0
                            END
                        ) AS interview,

                        SUM(
                            CASE
                                WHEN status = 'Selected'
                                THEN 1 ELSE 0
                            END
                        ) AS selected,

                        SUM(
                            CASE
                                WHEN status = 'Rejected'
                                THEN 1 ELSE 0
                            END
                        ) AS rejected

                    FROM applications
                    WHERE user_id = ?
                    """;

            try (PreparedStatement ps
                    = conn.prepareStatement(
                            dashboardSql
                    )) {

                        ps.setInt(1, userId);

                        ResultSet rs
                                = ps.executeQuery();

                        if (rs.next()) {

                            total
                                    = rs.getInt("total");

                            applied
                                    = rs.getInt("applied");

                            shortlisted
                                    = rs.getInt("shortlisted");

                            interview
                                    = rs.getInt("interview");

                            selected
                                    = rs.getInt("selected");

                            rejected
                                    = rs.getInt("rejected");
                        }
                    }

        } catch (Exception e) {
            e.printStackTrace();
        }

        html.append("""
                    <div class="dashboard">

                        <div class="dashboard-card">
                            <div class="number">%d</div>
                            <div class="title">
                                Total Applications
                            </div>
                        </div>

                        <div class="dashboard-card">
                            <div class="number">%d</div>
                            <div class="title">
                                Applied
                            </div>
                        </div>

                        <div class="dashboard-card">
                            <div class="number">%d</div>
                            <div class="title">
                                Shortlisted
                            </div>
                        </div>

                        <div class="dashboard-card">
                            <div class="number">%d</div>
                            <div class="title">
                                Interviews
                            </div>
                        </div>

                        <div class="dashboard-card">
                            <div class="number">%d</div>
                            <div class="title">
                                Selected
                            </div>
                        </div>

                        <div class="dashboard-card">
                            <div class="number">%d</div>
                            <div class="title">
                                Rejected
                            </div>
                        </div>

                    </div>
                """.formatted(
                total,
                applied,
                shortlisted,
                interview,
                selected,
                rejected
        ));

        String sql = """
                SELECT *
                FROM applications
                WHERE user_id = ?
                """;

        if (search != null
                && !search.trim().isEmpty()) {

            sql += """
                    AND
                    (
                        company LIKE ?
                        OR role LIKE ?
                        OR jobtrack_id LIKE ?
                        OR job_reference_id LIKE ?
                    )
                    """;
        }

        if (statusFilter != null
                && !statusFilter.trim().isEmpty()) {

            sql += " AND status = ?";
        }

        sql += " ORDER BY id DESC";

        try (
                Connection conn
                = Database.getConnection(); PreparedStatement ps
                = conn.prepareStatement(sql)) {

            int parameterIndex = 1;

            ps.setInt(
                    parameterIndex++,
                    userId
            );

            if (search != null
                    && !search.trim().isEmpty()) {

                String value
                        = "%" + search.trim() + "%";

                ps.setString(
                        parameterIndex++,
                        value
                );

                ps.setString(
                        parameterIndex++,
                        value
                );

                ps.setString(
                        parameterIndex++,
                        value
                );

                ps.setString(
                        parameterIndex++,
                        value
                );
            }

            if (statusFilter != null
                    && !statusFilter.trim().isEmpty()) {

                ps.setString(
                        parameterIndex,
                        statusFilter
                );
            }

            ResultSet rs
                    = ps.executeQuery();

            boolean found = false;

            while (rs.next()) {

                found = true;

                int id
                        = rs.getInt("id");

                String jobTrackId
                        = rs.getString("jobtrack_id");

                String jobReferenceId
                        = rs.getString(
                                "job_reference_id"
                        );

                String company
                        = rs.getString("company");

                String role
                        = rs.getString("role");

                String applicationDate
                        = rs.getString(
                                "application_date"
                        );

                String status
                        = rs.getString("status");

                String location
                        = rs.getString("location");

                String jobLink
                        = rs.getString("job_link");

                String notes
                        = rs.getString("notes");

                html.append("""
                        <div class="card">

                            <h2>
                                %s - %s
                            </h2>

                            <div class="field">
                                <span class="label">
                                    JobTrack ID:
                                </span>
                                %s
                            </div>

                            <div class="field">
                                <span class="label">
                                    Job ID / Registration ID:
                                </span>
                                %s
                            </div>

                            <div class="field">
                                <span class="label">
                                    Application Date:
                                </span>
                                %s
                            </div>

                            <div class="field">
                                <span class="label">
                                    Status:
                                </span>

                                <span class="status">
                                    %s
                                </span>
                            </div>

                            <div class="field">
                                <span class="label">
                                    Location:
                                </span>
                                %s
                            </div>

                            <div class="field">
                                <span class="label">
                                    Job Link:
                                </span>
                                %s
                            </div>

                            <div class="field">
                                <span class="label">
                                    Notes:
                                </span>
                                %s
                            </div>

                            <div class="buttons">

                                <a
                                    class="history"
                                    href="/history?id=%d"
                                >
                                    View History
                                </a>

                                <a
                                    class="edit"
                                    href="/update?id=%d"
                                >
                                    Edit
                                </a>

                                <button
                                    class="delete"
                                    onclick="deleteApplication(%d)"
                                >
                                    Delete
                                </button>

                            </div>

                        </div>
                        """.formatted(
                        escapeHtml(company),
                        escapeHtml(role),
                        escapeHtml(jobTrackId),
                        escapeHtml(jobReferenceId),
                        escapeHtml(applicationDate),
                        escapeHtml(status),
                        escapeHtml(location),
                        createJobLink(jobLink),
                        escapeHtml(notes),
                        id,
                        id,
                        id
                ));
            }

            if (!found) {

                html.append("""
                        <div class="empty">

                            <h2>
                                No applications found.
                            </h2>

                            <p>
                                Click "+ Add Application"
                                to add your first job.
                            </p>

                        </div>
                        """);
            }

        } catch (Exception e) {

            e.printStackTrace();

            html.append("""
                    <div class="empty">

                        <h2>
                            Error loading applications.
                        </h2>

                        <p>%s</p>

                    </div>
                    """.formatted(
                    escapeHtml(
                            e.getMessage()
                    )
            ));
        }

        html.append("""
                <script>

                    function deleteApplication(id) {

                        if (
                            confirm(
                                "Are you sure you want to delete this application?"
                            )
                        ) {

                            window.location.href =
                                "/delete?id=" + id;
                        }
                    }

                </script>

                </div>

                </body>
                </html>
                """);

        sendHtmlResponse(
                exchange,
                html.toString()
        );
    }

    // =========================
    // HISTORY
    // =========================
    private static void getHistory(
            HttpExchange exchange
    ) throws IOException {

        Integer userId
                = getLoggedInUserId(exchange);

        if (userId == null) {
            redirect(exchange, "/login");
            return;
        }

        String idText
                = getQueryParameter(
                        exchange,
                        "id"
                );

        if (idText == null
                || idText.isEmpty()) {

            sendResponse(
                    exchange,
                    "Application ID missing."
            );

            return;
        }

        int applicationId;

        try {
            applicationId
                    = Integer.parseInt(idText);
        } catch (Exception e) {
            sendResponse(
                    exchange,
                    "Invalid application ID."
            );
            return;
        }

        StringBuilder html
                = new StringBuilder();

        html.append("""
                <!DOCTYPE html>
                <html>

                <head>

                    <meta charset="UTF-8">

                    <meta name="viewport"
                          content="width=device-width, initial-scale=1.0">

                    <title>
                        Application History
                    </title>

                    <link rel="stylesheet"
                          href="/style.css">

                </head>

                <body>

                <div class="container">

                    <div class="card">

                        <h1>
                            Status History
                        </h1>
                """);

        try (Connection conn
                = Database.getConnection()) {

            String sql = """
                    SELECT
                        h.status,
                        h.changed_at

                    FROM application_history h

                    JOIN applications a
                    ON h.application_id = a.id

                    WHERE h.application_id = ?
                    AND a.user_id = ?

                    ORDER BY h.id ASC
                    """;

            try (PreparedStatement ps
                    = conn.prepareStatement(sql)) {

                ps.setInt(1, applicationId);
                ps.setInt(2, userId);

                ResultSet rs
                        = ps.executeQuery();

                boolean found = false;

                while (rs.next()) {

                    found = true;

                    String status
                            = rs.getString("status");

                    String changedAt
                            = rs.getString("changed_at");

                    html.append("""
                            <div class="history-item">

                                <b>Status:</b>
                                %s

                                <br>

                                <b>Changed At:</b>
                                %s

                            </div>
                            """.formatted(
                            escapeHtml(status),
                            escapeHtml(changedAt)
                    ));
                }

                if (!found) {

                    html.append("""
                            <p>
                                No status history found.
                            </p>
                            """);
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

            html.append("""
                    <p>
                        Error loading history:
                        %s
                    </p>
                    """.formatted(
                    escapeHtml(
                            e.getMessage()
                    )
            ));
        }

        html.append("""
                        <a
                            class="back"
                            href="/applications"
                        >
                            Back to Applications
                        </a>

                    </div>

                </div>

                </body>
                </html>
                """);

        sendHtmlResponse(
                exchange,
                html.toString()
        );
    }

    // =========================
    // DELETE
    // =========================
    private static void deleteApplication(
            HttpExchange exchange
    ) throws IOException {

        Integer userId
                = getLoggedInUserId(exchange);

        if (userId == null) {
            redirect(exchange, "/login");
            return;
        }

        String idText
                = getQueryParameter(
                        exchange,
                        "id"
                );

        try {

            int id
                    = Integer.parseInt(idText);

            try (Connection conn
                    = Database.getConnection()) {

                String checkSql = """
                        SELECT id
                        FROM applications
                        WHERE id = ?
                        AND user_id = ?
                        """;

                boolean exists = false;

                try (PreparedStatement ps
                        = conn.prepareStatement(
                                checkSql
                        )) {

                            ps.setInt(1, id);
                            ps.setInt(2, userId);

                            ResultSet rs
                                    = ps.executeQuery();

                            exists = rs.next();
                        }

                        if (!exists) {
                            redirect(
                                    exchange,
                                    "/applications"
                            );
                            return;
                        }

                        try (PreparedStatement ps
                                = conn.prepareStatement(
                                        """
                                     DELETE FROM
                                     application_history
                                     WHERE application_id = ?
                                     """
                                )) {

                                    ps.setInt(1, id);
                                    ps.executeUpdate();
                                }

                                try (PreparedStatement ps
                                        = conn.prepareStatement(
                                                """
                                     DELETE FROM
                                     applications
                                     WHERE id = ?
                                     AND user_id = ?
                                     """
                                        )) {

                                            ps.setInt(1, id);
                                            ps.setInt(2, userId);

                                            ps.executeUpdate();
                                        }
            }

            redirect(
                    exchange,
                    "/applications"
            );

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    "Error deleting application: "
                    + e.getMessage()
            );
        }
    }

    // =========================
    // UPDATE / EDIT
    // =========================
    private static void updateApplication(
            HttpExchange exchange
    ) throws IOException {

        Integer userId
                = getLoggedInUserId(exchange);

        if (userId == null) {
            redirect(exchange, "/login");
            return;
        }

        String idText
                = getQueryParameter(
                        exchange,
                        "id"
                );

        if (idText == null
                || idText.isEmpty()) {

            sendResponse(
                    exchange,
                    "Application ID missing."
            );

            return;
        }

        int id;

        try {
            id = Integer.parseInt(idText);
        } catch (Exception e) {
            sendResponse(
                    exchange,
                    "Invalid application ID."
            );
            return;
        }

        String query
                = exchange.getRequestURI()
                        .getRawQuery();

        String company
                = getValue(query, "company");

        // If company exists, this is the SAVE operation.
        if (!company.isEmpty()) {

            processUpdate(
                    exchange,
                    id,
                    userId,
                    query
            );

            return;
        }

        try (Connection conn
                = Database.getConnection()) {

            String sql = """
                    SELECT *
                    FROM applications
                    WHERE id = ?
                    AND user_id = ?
                    """;

            try (PreparedStatement ps
                    = conn.prepareStatement(sql)) {

                ps.setInt(1, id);
                ps.setInt(2, userId);

                ResultSet rs
                        = ps.executeQuery();

                if (!rs.next()) {

                    sendResponse(
                            exchange,
                            "Application not found."
                    );

                    return;
                }

                String currentCompany
                        = rs.getString("company");

                String currentRole
                        = rs.getString("role");

                String currentReference
                        = rs.getString(
                                "job_reference_id"
                        );

                String currentStatus
                        = rs.getString("status");

                String currentLocation
                        = rs.getString("location");

                String currentLink
                        = rs.getString("job_link");

                String currentNotes
                        = rs.getString("notes");

                String html = """
                        <!DOCTYPE html>
                        <html>

                        <head>

                            <meta charset="UTF-8">

                            <meta name="viewport"
                                  content="width=device-width, initial-scale=1.0">

                            <title>
                                Edit Application
                            </title>

                            <link rel="stylesheet"
                                  href="/style.css">

                        </head>

                        <body>

                        <div class="container">

                            <h1>
                                Edit Application
                            </h1>

                            <form
                                action="/update"
                                method="GET"
                            >

                                <input
                                    type="hidden"
                                    name="id"
                                    value="%d"
                                >

                                <label>
                                    Company Name
                                </label>

                                <input
                                    type="text"
                                    name="company"
                                    value="%s"
                                    required
                                >

                                <label>
                                    Job Role
                                </label>

                                <input
                                    type="text"
                                    name="role"
                                    value="%s"
                                    required
                                >

                                <label>
                                    Job ID / Registration ID
                                </label>

                                <input
                                    type="text"
                                    name="jobReferenceId"
                                    value="%s"
                                >

                                <label>
                                    Status
                                </label>

                                <select name="status">

                                    <option value="Applied" %s>
                                        Applied
                                    </option>

                                    <option value="Shortlisted" %s>
                                        Shortlisted
                                    </option>

                                    <option value="Interview" %s>
                                        Interview
                                    </option>

                                    <option value="Selected" %s>
                                        Selected
                                    </option>

                                    <option value="Rejected" %s>
                                        Rejected
                                    </option>

                                </select>

                                <label>
                                    Location
                                </label>

                                <input
                                    type="text"
                                    name="location"
                                    value="%s"
                                >

                                <label>
                                    Job Link
                                </label>

                                <input
                                    type="url"
                                    name="jobLink"
                                    value="%s"
                                >

                                <label>
                                    Notes
                                </label>

                                <textarea
                                    name="notes"
                                >%s</textarea>

                                <button type="submit">
                                    Save Changes
                                </button>

                                <a
                                    class="back"
                                    href="/applications"
                                >
                                    Cancel
                                </a>

                            </form>

                        </div>

                        </body>
                        </html>
                        """.formatted(
                        id,
                        escapeHtml(currentCompany),
                        escapeHtml(currentRole),
                        escapeHtml(currentReference),
                        "Applied".equals(currentStatus)
                        ? "selected"
                        : "",
                        "Shortlisted".equals(currentStatus)
                        ? "selected"
                        : "",
                        "Interview".equals(currentStatus)
                        ? "selected"
                        : "",
                        "Selected".equals(currentStatus)
                        ? "selected"
                        : "",
                        "Rejected".equals(currentStatus)
                        ? "selected"
                        : "",
                        escapeHtml(currentLocation),
                        escapeHtml(currentLink),
                        escapeHtml(currentNotes)
                );

                sendHtmlResponse(
                        exchange,
                        html
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    "Error: " + e.getMessage()
            );
        }
    }

    // =========================
    // PROCESS UPDATE
    // =========================
    private static void processUpdate(
            HttpExchange exchange,
            int id,
            int userId,
            String query
    ) throws IOException {

        String company
                = getValue(query, "company");

        String role
                = getValue(query, "role");

        String jobReferenceId
                = getValue(
                        query,
                        "jobReferenceId"
                );

        String status
                = getValue(query, "status");

        String location
                = getValue(query, "location");

        String jobLink
                = getValue(query, "jobLink");

        String notes
                = getValue(query, "notes");

        try (Connection conn
                = Database.getConnection()) {

            String oldStatus = null;

            String checkSql = """
                    SELECT status
                    FROM applications
                    WHERE id = ?
                    AND user_id = ?
                    """;

            try (PreparedStatement ps
                    = conn.prepareStatement(
                            checkSql
                    )) {

                        ps.setInt(1, id);
                        ps.setInt(2, userId);

                        ResultSet rs
                                = ps.executeQuery();

                        if (!rs.next()) {

                            sendResponse(
                                    exchange,
                                    "Application not found."
                            );

                            return;
                        }

                        oldStatus
                                = rs.getString("status");
                    }

                    String sql = """
                    UPDATE applications
                    SET
                        company = ?,
                        role = ?,
                        job_reference_id = ?,
                        status = ?,
                        location = ?,
                        job_link = ?,
                        notes = ?
                    WHERE id = ?
                    AND user_id = ?
                    """;

                    try (PreparedStatement ps
                            = conn.prepareStatement(sql)) {

                        ps.setString(1, company);
                        ps.setString(2, role);
                        ps.setString(3, jobReferenceId);
                        ps.setString(4, status);
                        ps.setString(5, location);
                        ps.setString(6, jobLink);
                        ps.setString(7, notes);
                        ps.setInt(8, id);
                        ps.setInt(9, userId);

                        ps.executeUpdate();
                    }

                    if (!oldStatus.equals(status)) {

                        addHistory(
                                conn,
                                id,
                                status
                        );
                    }

                    redirect(
                            exchange,
                            "/applications"
                    );

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    "Error updating application: "
                    + e.getMessage()
            );
        }
    }

    // =========================
    // ADD HISTORY
    // =========================
    private static void addHistory(
            Connection conn,
            int applicationId,
            String status
    ) throws SQLException {

        String sql = """
                INSERT INTO application_history
                (
                    application_id,
                    status,
                    changed_at
                )
                VALUES
                (
                    ?, ?, datetime('now')
                )
                """;

        try (PreparedStatement ps
                = conn.prepareStatement(sql)) {

            ps.setInt(1, applicationId);
            ps.setString(2, status);

            ps.executeUpdate();
        }
    }

    // =========================
    // ASSIGN OLD APPLICATIONS
    // =========================
    private static void assignOldApplications(
            Connection conn,
            int userId
    ) {

        try {

            String checkUsers
                    = "SELECT COUNT(*) FROM users";

            int userCount = 0;

            try (PreparedStatement ps
                    = conn.prepareStatement(checkUsers)) {

                ResultSet rs
                        = ps.executeQuery();

                if (rs.next()) {
                    userCount
                            = rs.getInt(1);
                }
            }

            if (userCount == 1) {

                String sql = """
                        UPDATE applications
                        SET user_id = ?
                        WHERE user_id IS NULL
                        """;

                try (PreparedStatement ps
                        = conn.prepareStatement(sql)) {

                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // CSS
    // =========================
    private static void serveCss(
            HttpExchange exchange
    ) throws IOException {

        Path path
                = Paths.get(
                        "frontend",
                        "style.css"
                );

        if (!Files.exists(path)) {

            exchange.sendResponseHeaders(
                    404,
                    -1
            );

            return;
        }

        byte[] css
                = Files.readAllBytes(path);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "text/css; charset=UTF-8"
        );

        exchange.sendResponseHeaders(
                200,
                css.length
        );

        try (OutputStream os
                = exchange.getResponseBody()) {

            os.write(css);
        }
    }

    // =========================
    // SESSION
    // =========================
    private static Integer getLoggedInUserId(
            HttpExchange exchange
    ) {

        String token
                = getSessionToken(exchange);

        if (token == null) {
            return null;
        }

        return sessions.get(token);
    }

    private static String getLoggedInUserName(
            HttpExchange exchange
    ) {

        String token
                = getSessionToken(exchange);

        if (token == null) {
            return "";
        }

        return sessionNames.getOrDefault(
                token,
                ""
        );
    }

    private static String getSessionToken(
            HttpExchange exchange
    ) {

        String cookie
                = exchange.getRequestHeaders()
                        .getFirst("Cookie");

        if (cookie == null) {
            return null;
        }

        String[] cookies
                = cookie.split(";");

        for (String item : cookies) {

            String trimmed
                    = item.trim();

            if (trimmed.startsWith(
                    "JOBTRACK_SESSION="
            )) {

                return trimmed.substring(
                        "JOBTRACK_SESSION=".length()
                );
            }
        }

        return null;
    }

    // =========================
    // PASSWORD HASH
    // =========================
    private static String hashPassword(
            String password
    ) {

        try {

            MessageDigest digest
                    = MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash
                    = digest.digest(
                            password.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder hex
                    = new StringBuilder();

            for (byte b : hash) {

                String value
                        = Integer.toHexString(
                                0xff & b
                        );

                if (value.length() == 1) {
                    hex.append('0');
                }

                hex.append(value);
            }

            return hex.toString();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Password hashing failed."
            );
        }
    }

    // =========================
    // QUERY PARAMETER
    // =========================
    private static String getQueryParameter(
            HttpExchange exchange,
            String key
    ) {

        String query
                = exchange.getRequestURI()
                        .getRawQuery();

        return getValue(
                query,
                key
        );
    }

    private static String getValue(
            String query,
            String key
    ) {

        if (query == null
                || query.isEmpty()) {

            return "";
        }

        Map<String, String> params
                = new HashMap<>();

        String[] pairs
                = query.split("&");

        for (String pair : pairs) {

            String[] parts
                    = pair.split("=", 2);

            if (parts.length == 2) {

                String paramKey
                        = URLDecoder.decode(
                                parts[0],
                                StandardCharsets.UTF_8
                        );

                String paramValue
                        = URLDecoder.decode(
                                parts[1],
                                StandardCharsets.UTF_8
                        );

                params.put(
                        paramKey,
                        paramValue
                );
            }
        }

        return params.getOrDefault(
                key,
                ""
        );
    }

    // =========================
    // JOB LINK
    // =========================
    private static String createJobLink(
            String jobLink
    ) {

        if (jobLink == null
                || jobLink.trim().isEmpty()) {

            return "Not provided";
        }

        String safeUrl
                = escapeHtml(jobLink);

        return "<a class=\"job-link\" "
                + "href=\""
                + safeUrl
                + "\" target=\"_blank\">"
                + "Open Job Posting"
                + "</a>";
    }

    // =========================
    // HTML ESCAPE
    // =========================
    private static String escapeHtml(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // =========================
    // REDIRECT
    // =========================
    private static void redirect(
            HttpExchange exchange,
            String location
    ) throws IOException {

        exchange.getResponseHeaders().set(
                "Location",
                location
        );

        exchange.sendResponseHeaders(
                302,
                -1
        );
    }

    // =========================
    // RESPONSE
    // =========================
    private static void sendResponse(
            HttpExchange exchange,
            String response
    ) throws IOException {

        sendHtmlResponse(
                exchange,
                response
        );
    }

    private static void sendHtmlResponse(
            HttpExchange exchange,
            String html
    ) throws IOException {

        byte[] bytes
                = html.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders().set(
                "Content-Type",
                "text/html; charset=UTF-8"
        );

        exchange.sendResponseHeaders(
                200,
                bytes.length
        );

        try (OutputStream os
                = exchange.getResponseBody()) {

            os.write(bytes);
        }
    }
}
