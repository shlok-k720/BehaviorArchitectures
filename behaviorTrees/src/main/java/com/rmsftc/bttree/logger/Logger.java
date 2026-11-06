package com.rmsftc.bttree.logger;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class Logger {
    public static final String NODE_STARTED = "com.rmsftc.bttree: Node Started";
    public static final String NODE_TICKED = "com.rmsftc.bttree: Node Ticked";
    public static final String NODE_FINISHED = "com.rmsftc.bttree: Node Finished";
    public static final String NODE_INVALIDATED = "com.rmsftc.bttree: Node Invalidated";
    public static final String NODE_RESET = "com.rmsftc.bttree: Node Reset";

    private static final Set<LogSystem> loggers = new CopyOnWriteArraySet<>();

    public Logger() {}

    public static void reset() {
        loggers.clear();
    }

    public static void attachLogger(LogSystem... loggers) {
        Objects.requireNonNull(loggers, "loggers");
        for (LogSystem logger : loggers) {
            attachLogger(logger);
        }
    }

    public static void attachLogger(LogSystem logger) {
        loggers.add(Objects.requireNonNull(logger, "logger"));
    }

    public static void removeLogger(LogSystem logSystem) {
        loggers.remove(Objects.requireNonNull(logSystem, "logSystem"));
    }

    public static void log(String caption, String data) {
        Objects.requireNonNull(caption, "caption");
        Objects.requireNonNull(data, "data");
        // Nothing is listening: skip the timestamp and the iteration.
        // Every node tick logs, so this is on the hot path.
        if (loggers.isEmpty()) return;

        double timestampMs = System.currentTimeMillis();
        loggers.forEach(logger -> logger.log(caption, data, timestampMs));
    }
}
