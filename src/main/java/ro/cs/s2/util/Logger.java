package ro.cs.s2.util;

import java.io.IOException;
import java.util.logging.*;

/**
 * Simple file logger.
 *
 * @author Cosmin Cara
 */
public class Logger {

    public interface CustomLogger {
        void info(String message, Object...args);
        void warn(String message, Object...args);
        void error(String message, Object...args);
    }

    private static final java.util.logging.Logger logger;
    private static String rootLogFile;
    private static CustomLogger rootLogger = new CustomLogger() {
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

    public static void initialize(String masterLogFile) throws IOException {
        synchronized (logger) {
            if (rootLogFile == null) {
                registerHandler(masterLogFile);
            }
        }
    }

    public static CustomLogger getRootLogger() {
        return rootLogger;
    }

    private static Handler registerHandler(String logFile) throws IOException {
        Handler fileHandler = new FileHandler(logFile, true);
        fileHandler.setFormatter(new LogFormatter());
        logger.addHandler(fileHandler);
        return fileHandler;
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
