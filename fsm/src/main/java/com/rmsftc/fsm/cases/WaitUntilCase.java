package com.rmsftc.fsm.cases;

import com.rmsftc.fsm.Case;
import com.rmsftc.fsm.State;

import java.util.function.Supplier;

/**
 * Waits until a condition is true, then moves on — the {@code Case} mirror
 * of {@code WaitUntilCommand}. With no target the case advances to the
 * next case in the machine's list when the condition holds; with a target
 * it jumps there instead.
 */
public class WaitUntilCase extends Case {
    private final Supplier<Boolean> condition;
    private final State target;

    /** Waits for {@code condition}, then advances to the next case. */
    public WaitUntilCase(State state, Supplier<Boolean> condition) {
        this(state, condition, null);
    }

    /** Waits for {@code condition}, then jumps to the case with {@code target}. */
    public WaitUntilCase(State state, Supplier<Boolean> condition, State target) {
        super(state);
        if (condition == null) {
            throw new IllegalArgumentException("WaitUntilCase: condition cannot be null");
        }
        this.condition = condition;
        this.target = target;
    }

    @Override
    public void onExecute() {
        if (condition.get()) {
            if (target != null) {
                transition(target);
            } else {
                transitionToNext();
            }
        }
    }
}
