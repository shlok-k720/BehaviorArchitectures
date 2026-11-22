package com.rmsftc.bttree;

/**
 * Log captions for behavior-tree lifecycle events, passed as the caption to
 * {@code com.rmsftc.utils.logger.Logger.log(caption, data)}. Attach a
 * {@code LogSystem} to receive them.
 */
public final class Captions {
    private Captions() {}

    public static final String NODE_STARTED = "com.rmsftc.bttree: Node Started";
    public static final String NODE_TICKED = "com.rmsftc.bttree: Node Ticked";
    public static final String NODE_FINISHED = "com.rmsftc.bttree: Node Finished";
    public static final String NODE_INVALIDATED = "com.rmsftc.bttree: Node Invalidated";
    public static final String NODE_RESET = "com.rmsftc.bttree: Node Reset";
}
