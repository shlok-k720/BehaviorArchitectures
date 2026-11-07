package com.rmsftc.fsm.cases;

import com.rmsftc.fsm.Case;
import com.rmsftc.fsm.State;

/**
 * Runs an action every tick, forever — the {@code Case} mirror of
 * {@code RunCommand}. It never finishes on its own; something else has to
 * transition the machine away from it.
 */
public class RunCase extends Case {
    private final Runnable action;

    public RunCase(State state, Runnable action) {
        super(state);
        if (action == null) {
            throw new IllegalArgumentException("RunCase: action cannot be null");
        }
        this.action = action;
    }

    @Override
    public void onExecute() {
        action.run();
    }
}
