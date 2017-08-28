/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ro.cs.products.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

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

    public static void registerHandler(Handler handler) {
        handler.setFormatter(new LogFormatter());
        handler.setLevel(logger.getLevel());
        logger.addHandler(handler);
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