package com.rmsftc.fsm;

/**
 * Log captions for state-machine lifecycle events, passed as the caption to
 * {@code com.rmsftc.utils.logger.Logger.log(caption, data)}. Attach a
 * {@code LogSystem} to receive them.
 */
public final class Captions {
    private Captions() {}

    public static final String FSM_STATE_ENTERED = "com.rmsftc.fsm: State Entered";
    public static final String FSM_STATE_EXITED = "com.rmsftc.fsm: State Exited";
    public static final String FSM_FINISHED = "com.rmsftc.fsm: Finished";
    public static final String FSM_PRINT = "com.rmsftc.fsm: PrintCase";
}
