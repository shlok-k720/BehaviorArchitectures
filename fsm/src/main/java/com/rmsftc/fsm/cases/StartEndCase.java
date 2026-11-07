package com.rmsftc.fsm.cases;

import com.rmsftc.fsm.Case;
import com.rmsftc.fsm.State;

/**
 * Runs a start action on entry and an end action on exit — the
 * {@code Case} mirror of {@code StartEndCommand}. It never finishes on
 * its own; something else has to transition the machine away from it, and
 * the end action runs then (or when the machine stops or finishes).
 */
public class StartEndCase extends Case {
    private final Runnable onStartAction;
    private final Runnable onEndAction;

    public StartEndCase(State state, Runnable onStartAction, Runnable onEndAction) {
        super(state);
        if (onStartAction == null) {
            throw new IllegalArgumentException("StartEndCase: onStart action cannot be null");
        }
        if (onEndAction == null) {
            throw new IllegalArgumentException("StartEndCase: onEnd action cannot be null");
        }
        this.onStartAction = onStartAction;
        this.onEndAction = onEndAction;
    }

    @Override
    public void onStart() {
        onStartAction.run();
    }

    @Override
    public void onExecute() {
    }

    @Override
    public void onTransition(Case other) {
        onEndAction.run();
    }
}
