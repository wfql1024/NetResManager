package com.netresmanager;

import javafx.application.Application;

/**
 * Pre-JavaFX bootstrap launcher.
 * Necessary to avoid JavaFX module system issues when calling
 * Application.launch() from a class that extends Application.
 */
public class Launcher {

    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
