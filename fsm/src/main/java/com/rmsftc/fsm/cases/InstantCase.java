package com.rmsftc.fsm.cases;

import com.rmsftc.fsm.Case;
import com.rmsftc.fsm.State;

/**
 * Runs an action once, then moves on — the {@code Case} mirror of
 * {@code InstantCommand}. The action runs in {@link #onStart()}; the move
 * is applied at the end of the first tick.
 *
 * <p>With no target the case advances to the next case in the machine's
 * list; with a target it jumps there instead.
 */
public class InstantCase extends Case {
    private final Runnable action;
    private final State target;

    /** Runs {@code action} once, then advances to the next case. */
    public InstantCase(State state, Runnable action) {
        this(state, action, null);
    }

    /** Runs {@code action} once, then jumps to the case with {@code target}. */
    public InstantCase(State state, Runnable action, State target) {
        super(state);
        if (action == null) {
            throw new IllegalArgumentException("InstantCase: action cannot be null");
        }
        this.action = action;
        this.target = target;
    }

    @Override
    public void onStart() {
        action.run();
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
