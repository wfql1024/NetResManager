package com.netresmanager;

import com.netresmanager.bridge.JsBridge;
import com.netresmanager.config.AppConfig;
import com.netresmanager.db.DatabaseManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main JavaFX Application class.
 * Sets up the window with an embedded WebView and registers the Java-JS bridge.
 */
public class MainApp extends Application {

    private static final Logger LOG = Logger.getLogger(MainApp.class.getName());

    private Stage primaryStage;
    private WebView webView;
    private WebEngine webEngine;
    private JsBridge jsBridge;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Initialize database
        initDatabase();

        // Create bridge
        this.jsBridge = new JsBridge();

        // Setup WebView
        setupWebView();

        // Configure window
        BorderPane root = new BorderPane();
        root.setCenter(webView);

        Scene scene = new Scene(root, AppConfig.WINDOW_DEFAULT_WIDTH, AppConfig.WINDOW_DEFAULT_HEIGHT);

        stage.setTitle(AppConfig.APP_NAME + " v" + AppConfig.APP_VERSION);
        stage.setMinWidth(AppConfig.WINDOW_MIN_WIDTH);
        stage.setMinHeight(AppConfig.WINDOW_MIN_HEIGHT);
        stage.setScene(scene);

        // Load app icon
        try {
            var iconUrl = getClass().getResource("/web/icons/app-icon.png");
            if (iconUrl != null) {
                stage.getIcons().add(new Image(iconUrl.toExternalForm()));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load app icon", e);
        }

        stage.show();
    }

    @Override
    public void stop() {
        DatabaseManager.getInstance().close();
        Platform.exit();
    }

    private void initDatabase() {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.initializeSchema();
            db.checkIntegrity();
            LOG.info("Database initialized at: " + AppConfig.getDbPath());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private void setupWebView() {
        webView = new WebView();
        webEngine = webView.getEngine();

        // Enable JavaScript
        webEngine.setJavaScriptEnabled(true);

        // Register JS bridge when page loads
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                registerJsBridge();
            }
        });

        // Handle JS errors
        webEngine.setOnError(event -> {
            LOG.log(Level.WARNING, "WebView error: " + event.getMessage());
        });

        // Load the main HTML page
        String url = getClass().getResource("/web/index.html").toExternalForm();
        webEngine.load(url);
        LOG.info("Loading: " + url);
    }

    /**
     * Registers the JsBridge instance as window.javaObject so JavaScript
     * can call Java methods via window.javaObject.methodName().
     */
    private void registerJsBridge() {
        try {
            JSObject window = (JSObject) webEngine.executeScript("window");
            if (window != null) {
                window.setMember("javaObject", jsBridge);
                LOG.info("JS bridge registered: window.javaObject");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to register JS bridge", e);
        }
    }

    /**
     * Executes a JavaScript expression from the Java side (for push updates).
     */
    public void executeScript(String script) {
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(script);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to execute script: " + script, e);
            }
        });
    }

    /**
     * Gets the JsBridge instance (useful for testing or direct calls).
     */
    public JsBridge getJsBridge() {
        return jsBridge;
    }
}
