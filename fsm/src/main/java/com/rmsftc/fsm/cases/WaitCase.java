package com.rmsftc.fsm.cases;

import com.rmsftc.fsm.Case;
import com.rmsftc.fsm.State;

/**
 * Waits a fixed time, then moves on — the {@code Case} mirror of
 * {@code WaitCommand}. Times are plain {@code long} milliseconds (no
 * {@code java.time.Duration}: it is API 26+ and this library targets
 * minSdk 24).
 *
 * <p>With no target the case advances to the next case in the machine's
 * list when the time is up; with a target it jumps there instead. The
 * clock is the machine's (see {@link Case#nowNanos()}), so tests inject
 * one fake clock into the machine and all timing follows it.
 */
public class WaitCase extends Case {
    private final long millis;
    private final State target;
    private long startNanos = 0;

    /** Waits {@code millis}, then advances to the next case. */
    public WaitCase(State state, long millis) {
        this(state, millis, null);
    }

    /** Waits {@code millis}, then jumps to the case with {@code target}. */
    public WaitCase(State state, long millis, State target) {
        super(state);
        if (millis < 0) {
            throw new IllegalArgumentException("WaitCase: millis cannot be negative");
        }
        this.millis = millis;
        this.target = target;
    }

    @Override
    public void onStart() {
        startNanos = nowNanos();
    }

    @Override
    public void onExecute() {
        if ((nowNanos() - startNanos) / 1_000_000L >= millis) {
            if (target != null) {
                transition(target);
            } else {
                transitionToNext();
            }
        }
    }
}
