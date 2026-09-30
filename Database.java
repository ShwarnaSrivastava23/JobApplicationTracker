import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class Database {

    private static final String URL = "jdbc:sqlite:jobtrack.db";

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Database connection failed: " + e.getMessage()
            );
        }
    }

    public static void main(String[] args) {

        try (
                Connection conn = getConnection();
                Statement stmt = conn.createStatement()
        ) {

            // =========================
            // USERS TABLE
            // =========================

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        email TEXT UNIQUE NOT NULL,
                        password TEXT NOT NULL
                    )
                    """);


            // =========================
            // APPLICATIONS TABLE
            // =========================

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


            // =========================
            // ADD MISSING COLUMNS
            // FOR EXISTING DATABASE
            // =========================

            try {
                stmt.execute(
                        "ALTER TABLE applications " +
                        "ADD COLUMN jobtrack_id TEXT"
                );
            } catch (Exception ignored) {
            }

            try {
                stmt.execute(
                        "ALTER TABLE applications " +
                        "ADD COLUMN job_reference_id TEXT"
                );
            } catch (Exception ignored) {
            }


            // =========================
            // APPLICATION HISTORY
            // =========================

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


            // =========================
            // GENERATE JOBTRACK IDs
            // FOR OLD APPLICATIONS
            // =========================

            try {
                stmt.execute("""
                        UPDATE applications
                        SET jobtrack_id =
                            'JT-' || printf('%04d', id)
                        WHERE jobtrack_id IS NULL
                           OR jobtrack_id = ''
                        """);
            } catch (Exception ignored) {
            }


            // =========================
            // FINISHED
            // =========================

            System.out.println(
                    "Database setup completed successfully!"
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}