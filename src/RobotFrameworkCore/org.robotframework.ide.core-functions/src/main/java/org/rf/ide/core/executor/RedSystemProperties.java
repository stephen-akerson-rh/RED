/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.executor;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Splitter;

/**
 * @author Michal Anglart
 */
public class RedSystemProperties {

    public static final String RED_CONNECT_TO_SERVER_AT = "red.connectToServerAt";

    public static final String RED_USE_DIRECT_INTERPRETER = "red.useDirectInterpreter";

    public static final String RED_SHOW_SESSION_CONSOLE = "red.showSessionConsole";

    public static final String RED_USE_OLD_REPARSED_LINK_MODE = "red.useOldReparsedLinkMode";

    public static final String RED_USE_DIRECT_SCANNER = "red.useDirectScanner";

    public static boolean shouldConnectToRunningServer() {
        return System.getProperty(RED_CONNECT_TO_SERVER_AT) != null;
    }

    public static String getSessionServerAddress() {
        return System.getProperty(RED_CONNECT_TO_SERVER_AT);
    }

    public static boolean shouldUseDirectExecutor() {
        return Boolean.valueOf(System.getProperty(RED_USE_DIRECT_INTERPRETER));
    }

    public static boolean shouldShowSessionConsole() {
        return Boolean.valueOf(System.getProperty(RED_SHOW_SESSION_CONSOLE));
    }

    public static boolean shouldUseOldReparsedLinkMode() {
        return Boolean.valueOf(System.getProperty(RED_USE_OLD_REPARSED_LINK_MODE));
    }

    public static boolean shouldUseDirectScanner() {
        return Boolean.valueOf(System.getProperty(RED_USE_DIRECT_SCANNER));
    }

    public static boolean isWindowsPlatform() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static String getPathsSeparator() {
        return isWindowsPlatform() ? ";" : ":";
    }

    public static List<String> getPythonPaths() {
        return getPaths("PYTHONPATH");
    }

    public static List<String> getClassPaths() {
        return getPaths("CLASSPATH");
    }

    private static List<String> getPaths(final String name) {
        final String paths = System.getenv(name);
        if (paths == null || paths.isEmpty()) {
            return new ArrayList<>();
        }
        return Splitter.on(getPathsSeparator()).splitToList(paths);
    }
}
