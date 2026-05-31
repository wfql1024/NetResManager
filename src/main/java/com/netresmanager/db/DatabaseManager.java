package com.netresmanager.db;

import com.netresmanager.config.AppConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages the SQLite database connection, initialization, and migrations.
 * Uses a single shared connection with synchronization for thread safety.
 */
public class DatabaseManager {

    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());

    private static DatabaseManager instance;

    private final String dbPath;
    private Connection connection;

    private DatabaseManager() {
        this.dbPath = AppConfig.getDbPath();
    }

    /**
     * Returns the singleton DatabaseManager instance.
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Returns a connection to the database, initializing it if necessary.
     * The connection is shared — all callers receive the same one.
     */
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                LOG.info("Connected to database: " + dbPath);
            } catch (SQLException e) {
                LOG.log(Level.SEVERE, "Failed to connect to database: " + dbPath, e);
                throw e;
            }
        }
        return connection;
    }

    /**
     * Initializes the database schema by executing the DDL from schema.sql.
     * Safe to call multiple times — uses IF NOT EXISTS clauses.
     */
    public synchronized void initializeSchema() {
        String sql = readSchemaFile();
        if (sql == null || sql.isBlank()) {
            LOG.severe("Schema SQL is empty or could not be read.");
            return;
        }
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // Remove comment lines (lines starting with --)
            StringBuilder cleanSql = new StringBuilder();
            for (String line : sql.split("\n")) {
                String trimmedLine = line.trim();
                if (!trimmedLine.startsWith("--")) {
                    cleanSql.append(line).append("\n");
                }
            }
            // Split by semicolons and execute each statement
            String[] statements = cleanSql.toString().split(";");
            for (String st : statements) {
                String trimmed = st.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        stmt.execute(trimmed);
                    } catch (SQLException e) {
                        LOG.log(Level.WARNING, "Error executing SQL: " + trimmed.substring(0, Math.min(80, trimmed.length())), e);
                    }
                }
            }
            LOG.info("Database schema initialized successfully.");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to initialize database schema.", e);
        }
    }

    /**
     * Checks database integrity on startup.
     * @return true if integrity check passes
     */
    public synchronized boolean checkIntegrity() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("PRAGMA integrity_check");
            if (rs.next()) {
                String result = rs.getString(1);
                if ("ok".equalsIgnoreCase(result)) {
                    LOG.info("Database integrity check passed.");
                    return true;
                }
                LOG.severe("Database integrity check failed: " + result);
                return false;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error during integrity check.", e);
        }
        return false;
    }

    /**
     * Reads schema.sql from the classpath.
     */
    private String readSchemaFile() {
        try (InputStream is = getClass().getResourceAsStream("/db/schema.sql")) {
            if (is == null) {
                LOG.severe("schema.sql not found in classpath.");
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to read schema.sql.", e);
            return null;
        }
    }

    /**
     * Closes the database connection. Should be called on application shutdown.
     */
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                LOG.info("Database connection closed.");
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Error closing database connection.", e);
            }
        }
    }

    /**
     * For testing: creates a DatabaseManager that uses an in-memory database.
     */
    static DatabaseManager createTestInstance() {
        DatabaseManager dm = new DatabaseManager();
        // Override the dbPath to use in-memory database
        // We use a subclass approach differently — just set up for :memory:
        return new DatabaseManager() {
            private Connection testConnection;

            @Override
            public synchronized Connection getConnection() throws SQLException {
                if (testConnection == null || testConnection.isClosed()) {
                    testConnection = DriverManager.getConnection("jdbc:sqlite::memory:");
                }
                return testConnection;
            }
        };
    }
}
