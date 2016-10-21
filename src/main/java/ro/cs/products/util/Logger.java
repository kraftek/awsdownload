/*
 * Copyright (C) 2016 Cosmin Cara
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 *  with this program; if not, see http://www.gnu.org/licenses/
 */
package ro.cs.products.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.*;

/**
 * Simple file logger.
 *
 * @author Cosmin Cara
 */
public class Logger {

    public interface CustomLogger {
        void debug(String message, Object...args);
        void info(String message, Object...args);
        void warn(String message, Object...args);
        void error(String message, Object...args);
    }

    private static final java.util.logging.Logger logger;
    private static String rootLogFile;
    private static CustomLogger rootLogger = new CustomLogger() {
        @Override
        public void debug(String message, Object... args) { fine(message, args); }

        @Override
        public void info(String message, Object... args) {
            information(message, args);
        }

        @Override
        public void warn(String message, Object... args) {
            warning(message, args);
        }

        @Override
        public void error(String message, Object... args) {
            err(message, args);
        }
    };

    static {
        logger = java.util.logging.Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME);
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        if (handlers[0] instanceof ConsoleHandler) {
            handlers[0].setFormatter(new LogFormatter());
        }
        logger.setLevel(Level.ALL);
    }

    public static void initialize(String masterLogFile, boolean verbose) throws IOException {
        synchronized (logger) {
            if (rootLogFile == null) {
                rootLogFile = masterLogFile;
                registerHandler(rootLogFile, verbose);
            }
        }
    }

    public static CustomLogger getRootLogger() {
        return rootLogger;
    }

    private static Handler registerHandler(String logFile, boolean verbose) throws IOException {
        Handler fileHandler = new FileHandler(logFile, true);
        fileHandler.setFormatter(new LogFormatter());
        logger.addHandler(fileHandler);
        Level level = verbose ? Level.ALL : Level.INFO;
        logger.setLevel(level);
        Arrays.stream(java.util.logging.Logger.getLogger("").getHandlers())
                .forEach(h -> h.setLevel(level));
        return fileHandler;
    }

    private static void fine(String message, Object...args) {
        if (args != null && args.length > 0) {
            message = String.format(message, args);
        }
        logger.fine(message);
    }

    private static void information(String message, Object...args) {
        if (args != null && args.length > 0) {
            message = String.format(message, args);
        }
        logger.info(message);
    }

    private static void warning(String message, Object...args) {
        if (args != null && args.length > 0) {
            message = String.format(message, args);
        }
        logger.warning(message);
    }

    private static void err(String message, Object...args) {
        if (args != null && args.length > 0) {
            message = String.format(message, args);
        }
        logger.severe(message);
    }

    public static class ScopeLogger implements CustomLogger, AutoCloseable {

        private Handler fileHandler;

        public ScopeLogger(String logFile) throws IOException {
            //fileHandler = registerHandler(logFile);
            fileHandler = new FileHandler(logFile);
            fileHandler.setFormatter(new LogFormatter());
        }

        @Override
        public void debug(String message, Object...args) {
            if (args != null && args.length > 0) {
                message = String.format(message, args);
            }
            fileHandler.publish(new LogRecord(Level.FINE, message));
        }

        @Override
        public void info(String message, Object...args) {
            if (args != null && args.length > 0) {
                message = String.format(message, args);
            }
            fileHandler.publish(new LogRecord(Level.INFO, message));
        }

        @Override
        public void warn(String message, Object...args) {
            if (args != null && args.length > 0) {
                message = String.format(message, args);
            }
            fileHandler.publish(new LogRecord(Level.WARNING, message));
        }

        @Override
        public void error(String message, Object...args) {
            if (args != null && args.length > 0) {
                message = String.format(message, args);
            }
            fileHandler.publish(new LogRecord(Level.SEVERE, message));
        }

        @Override
        public void close() throws IOException {
            if (fileHandler != null) {
                fileHandler.close();
                logger.removeHandler(fileHandler);
            }
        }
    }

}