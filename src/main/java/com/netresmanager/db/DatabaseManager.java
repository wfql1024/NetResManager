package com.netresmanager.db;

import com.netresmanager.config.AppConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages SQLite database connection, schema initialization, and version migrations.
 * Database file: {user.home}/.netresmanager/netresmanager.db
 */
public class DatabaseManager {

    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());
    private static final int CURRENT_SCHEMA_VERSION = 4;

    private static DatabaseManager instance;
    private final String dbPath;
    private Connection connection;

    private DatabaseManager() {
        this.dbPath = AppConfig.getDbPath();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            LOG.info("Connected to: " + dbPath);
        }
        return connection;
    }

    /**
     * Initializes or upgrades the database to the current schema version.
     * Existing data is preserved during migration.
     */
    public synchronized void initializeSchema() {
        LOG.info("=== Database initialization START ===");
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            int currentVersion = getSchemaVersion(conn);
            LOG.info("Current DB version: " + currentVersion +
                     ", target: " + CURRENT_SCHEMA_VERSION);

            // Step 1: Always ensure core tables exist (hardcoded, no file parsing)
            ensureCoreTables(stmt);

            // Step 2: Attempt file-based schema/migration (best-effort)
            try {
                executeSqlFile(conn, "/db/schema.sql");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "schema.sql execution had errors (core tables already ensured)", e);
            }

            // Step 3: Run data migrations if upgrading from old version
            if (currentVersion > 0 && currentVersion < CURRENT_SCHEMA_VERSION) {
                for (int v = currentVersion + 1; v <= CURRENT_SCHEMA_VERSION; v++) {
                    String migrationFile = String.format("/db/migrations/%03d_v%d_to_v%d.sql",
                            v - 1, v - 1, v);
                    LOG.info("Running migration: " + migrationFile);
                    try {
                        executeSqlFile(conn, migrationFile);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Migration file had errors: " + migrationFile, e);
                    }
                }
            }

            // Step 4: Update version
            stmt.execute("PRAGMA user_version = " + CURRENT_SCHEMA_VERSION);
            LOG.info("=== Database initialization DONE (version " + CURRENT_SCHEMA_VERSION + ") ===");

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "FATAL: Database initialization failed", e);
        }
    }

    /**
     * Hardcoded CREATE TABLE IF NOT EXISTS for all core tables.
     * This guarantees tables exist regardless of SQL file parsing issues.
     */
    private void ensureCoreTables(Statement stmt) throws SQLException {
        LOG.info("Ensuring core tables exist...");

        stmt.execute("CREATE TABLE IF NOT EXISTS projects (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "name TEXT NOT NULL UNIQUE," +
            "paths TEXT NOT NULL DEFAULT '[]'," +
            "export_dir TEXT NOT NULL DEFAULT ''," +
            "export_prefix TEXT NOT NULL DEFAULT ''," +
            "recycle_prefix TEXT NOT NULL DEFAULT ''," +
            "created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))," +
            "updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')))");

        stmt.execute("CREATE TABLE IF NOT EXISTS tags (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "file_path TEXT NOT NULL," +
            "tag_name TEXT NOT NULL," +
            "project_id INTEGER NOT NULL," +
            "created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))," +
            "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE," +
            "UNIQUE(file_path, tag_name, project_id))");

        stmt.execute("CREATE TABLE IF NOT EXISTS operation_records (" +
            "record_id TEXT PRIMARY KEY," +
            "project_id INTEGER NOT NULL," +
            "operation_type TEXT NOT NULL," +
            "source_path TEXT NOT NULL," +
            "dest_path TEXT NOT NULL DEFAULT ''," +
            "file_type TEXT NOT NULL DEFAULT ''," +
            "file_size INTEGER NOT NULL DEFAULT 0," +
            "tags_json TEXT NOT NULL DEFAULT '[]'," +
            "success_time TEXT NOT NULL DEFAULT ''," +
            "hidden INTEGER NOT NULL DEFAULT 0," +
            "exclude_from_stats INTEGER NOT NULL DEFAULT 0," +
            "deleted INTEGER NOT NULL DEFAULT 0," +
            "rollback_failure_reason TEXT NOT NULL DEFAULT ''," +
            "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE)");

        // Verify
        var rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name");
        StringBuilder tables = new StringBuilder("Tables: ");
        while (rs.next()) tables.append(rs.getString(1)).append(" ");
        LOG.info(tables.toString());
    }

    /**
     * Gets the current schema version from the database.
     */
    private int getSchemaVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Sets the schema version in the database.
     */
    private void setSchemaVersion(Connection conn, int version) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA user_version = " + version);
        }
    }

    /**
     * Executes a SQL file from classpath.
     * Removes comment lines (starting with --) before execution.
     */
    private void executeSqlFile(Connection conn, String resourcePath) {
        String sql = readResource(resourcePath);
        if (sql == null || sql.isBlank()) {
            LOG.severe("SQL file not found or empty: " + resourcePath);
            return;
        }

        // Remove comment lines AND strip inline comments
        StringBuilder clean = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) continue;
            // Strip inline comments (e.g., "col TEXT, -- note; extra")
            int commentIdx = trimmed.indexOf("--");
            if (commentIdx >= 0) {
                trimmed = trimmed.substring(0, commentIdx).trim();
                if (trimmed.isEmpty()) continue;
            }
            clean.append(trimmed).append("\n");
        }

        // Execute each statement separately
        String[] statements = clean.toString().split(";");
        int executed = 0;
        int failed = 0;

        try (Statement stmt = conn.createStatement()) {
            for (String st : statements) {
                String trimmed = st.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    stmt.execute(trimmed);
                    executed++;
                } catch (SQLException e) {
                    failed++;
                    LOG.log(Level.WARNING, "SQL error in " + resourcePath +
                            ": " + e.getMessage() +
                            " [SQL: " + trimmed.substring(0, Math.min(100, trimmed.length())) + "...]");
                }
            }
            LOG.info("Executed " + executed + " statements from " + resourcePath +
                     (failed > 0 ? " (" + failed + " failed)" : ""));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to execute SQL from: " + resourcePath, e);
        }
    }

    /**
     * Reads a resource file from classpath.
     */
    private String readResource(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                LOG.warning("Resource not found: " + path);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to read resource: " + path, e);
            return null;
        }
    }

    /**
     * Checks database integrity.
     */
    public synchronized boolean checkIntegrity() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
            if (rs.next()) {
                String result = rs.getString(1);
                if ("ok".equalsIgnoreCase(result)) {
                    LOG.info("Integrity check: OK");
                    return true;
                }
                LOG.severe("Integrity check FAILED: " + result);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Integrity check error", e);
        }
        return false;
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                LOG.info("Database connection closed.");
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Error closing connection", e);
            }
        }
    }
}
