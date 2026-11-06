package com.rmsftc.fsm;

import java.util.function.Supplier;

/**
 * A guarded move out of a {@link Case}: when {@code condition} is true, the
 * machine moves to {@code target}. A {@code null} target means "advance to
 * the next case in the machine's list".
 *
 * <p>The condition is evaluated fresh every tick (no latch): a case that is
 * left and later re-entered re-evaluates its transitions from scratch.
 */
public class Transition {
    private final Supplier<Boolean> condition;
    private final Case target;

    public Transition(Supplier<Boolean> condition, Case target) {
        if (condition == null) {
            throw new IllegalArgumentException("Transition: condition cannot be null");
        }
        this.condition = condition;
        this.target = target;
    }

    /** A transition that advances to the next case when {@code condition} is true. */
    public Transition(Supplier<Boolean> condition) {
        this(condition, null);
    }

    public Supplier<Boolean> getCondition() {
        return condition;
    }

    /** The target case, or null to advance to the next case in the list. */
    public Case getTarget() {
        return target;
    }
}
