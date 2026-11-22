package com.rmsftc.cmdlib;

/**
 * Log captions for command lifecycle events, passed as the caption to
 * {@code com.rmsftc.utils.logger.Logger.log(caption, data)}. Attach a
 * {@code LogSystem} to receive them.
 */
public final class Captions {
    private Captions() {}

    public static final String CMD_SCHEDULED = "com.rmsftc.cmdlib: Command Scheduled";
    public static final String CMD_FINISHED = "com.rmsftc.cmdlib: Command Finished";
    public static final String CMD_CANCELLED = "com.rmsftc.cmdlib: Command Cancelled";
    public static final String CMD_INTERRUPTED = "com.rmsftc.cmdlib: Command Interrupted";
    public static final String CMD_PRINT = "com.rmsftc.cmdlib: PrintCommand";
}
