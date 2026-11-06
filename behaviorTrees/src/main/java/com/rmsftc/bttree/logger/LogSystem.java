package com.rmsftc.bttree.logger;

@FunctionalInterface
public interface LogSystem {
    /**
     * Receives a behavior-tree log event.
     *
     * @param caption one of the event captions exposed by {@link Logger}
     * @param data event-specific details
     * @param timeStampMs Unix epoch timestamp in milliseconds
     */
    void log(String caption, String data, double timeStampMs);
}
