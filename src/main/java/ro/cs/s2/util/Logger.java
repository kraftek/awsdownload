package ro.cs.s2.util;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

/**
 * Simple file logger.
 *
 * @author Cosmin Cara
 */
public class Logger {
    private static java.util.logging.Logger logger;

    static {
        logger = java.util.logging.Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME);
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        if (handlers[0] instanceof ConsoleHandler) {
            handlers[0].setFormatter(new LogFormatter());
        }
        logger.setLevel(Level.INFO);
        Handler fileHandler = null;
        try {
            fileHandler = new FileHandler("download.log");
            fileHandler.setFormatter(new LogFormatter());
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void info(String message, Object...args) {
        if (args != null && args.length > 0) {
            message = String.format(message, args);
        }
        logger.info(message);
    }

    public static void warn(String message, Object...args) {
        if (args != null && args.length > 0) {
            message = String.format(message, args);
        }
        logger.warning(message);
    }

    public static void error(String message, Object...args) {
        if (args != null && args.length > 0) {
            message = String.format(message, args);
        }
        logger.severe(message);
    }
}
