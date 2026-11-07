package com.rmsftc.fsm.cases;

import com.rmsftc.fsm.Case;
import com.rmsftc.fsm.State;
import com.rmsftc.util.logger.Logger;

/**
 * Prints a message once, then moves on — the {@code Case} mirror of
 * {@code PrintCommand} (to {@code System.out}, same as the command).
 *
 * <p>With no target the case advances to the next case in the machine's
 * list; with a target it jumps there instead.
 */
public class PrintCase extends Case {
    private final String message;
    private final State target;

    /** Prints {@code message} once, then advances to the next case. */
    public PrintCase(State state, String message) {
        this(state, message, null);
    }

    /** Prints {@code message} once, then jumps to the case with {@code target}. */
    public PrintCase(State state, String message, State target) {
        super(state);
        if (message == null) {
            throw new IllegalArgumentException("PrintCase: message cannot be null");
        }
        this.message = message;
        this.target = target;
    }

    @Override
    public void onStart() {
        System.out.println(message);
        Logger.log(Logger.FSM_PRINT, "message=" + message);
        if (target != null) {
            transition(target);
        } else {
            transitionToNext();
        }
    }

    @Override
    public void onExecute() {
    }
}
