package com.rmsftc.fsm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One case of a switch/case state machine: a state that runs while it is
 * active. The machine ticks the current case once per loop
 * ({@link StateMachine#update()}); the case's {@link #execute()} is the
 * body of that `case` in the switch.
 *
 * <p>Two ways to leave a case:
 * <ul>
 *   <li>Imperative: call {@link #transition(Case)} inside {@link #execute()}
 *       (or anywhere in the subclass). The move is applied at the end of
 *       the current tick and wins over declarative transitions.</li>
 *   <li>Declarative: {@link #addTransition(Supplier, Case)} adds a guarded
 *       transition, checked by the machine after {@link #execute()} each
 *       tick, in the order added. The first whose condition is true wins.
 *       A {@code null} target means "advance to the next case in the
 *       machine's list", like falling through to the next case.</li>
 * </ul>
 *
 * <p>Timing (all plain milliseconds, like the rest of this library):
 * {@link #minTimeMs(long)} gates the declarative transitions — they cannot
 * fire before the case has been active that long. {@link #maxTimeMs(long)}
 * forces the case to finish after that long, going to the
 * {@link #fallback(Case)} if one is set, or advancing to the next case.
 * The imperative {@link #transition(Case)} is never gated by either.
 *
 * <p>Subclass it for behavior, or use {@link SimpleCase} for a case built
 * from lambdas. The model follows 23641 Cuberobot's {@code util/fsm}
 * (State/Transition/StateMachine), with direct case references instead of
 * string names, a per-tick {@link #execute()}, and an injected clock.
 */
public abstract class Case {
    private final String name;
    private final List<Transition> transitions = new ArrayList<>();
    private long minTimeMs = 0;
    private long maxTimeMs = Long.MAX_VALUE;
    private Case fallback;
    private Case pendingTarget;

    public Case() {
        this(null);
    }

    public Case(String name) {
        this.name = name;
    }

    /** The case's name, or null if unnamed. Used by the builders and listeners. */
    public String getName() {
        return name;
    }

    /** Runs every tick while this case is active: the body of the `case`. */
    public abstract void execute();

    /** Runs once when this case becomes active. Default: nothing. */
    public void onEnter() {
    }

    /** Runs once when this case stops being active. Default: nothing. */
    public void onExit() {
    }

    /**
     * Requests a move to {@code other}, applied at the end of the current
     * tick (or after the first tick, if called from {@link #onEnter()}).
     * Wins over any declarative transition that fires on the same tick.
     */
    protected final void transition(Case other) {
        if (other == null) {
            throw new IllegalArgumentException("Case.transition: target cannot be null");
        }
        pendingTarget = other;
    }

    /**
     * Adds a guarded transition: when {@code condition} is true (and the
     * case has been active at least {@link #minTimeMs(long)}), the machine
     * moves to {@code target}; a {@code null} target advances to the next
     * case in the machine's list. Checked in the order added.
     */
    public final Case addTransition(Supplier<Boolean> condition, Case target) {
        transitions.add(new Transition(condition, target));
        return this;
    }

    /**
     * Adds a guarded transition that advances to the next case in the
     * machine's list when {@code condition} is true.
     */
    public final Case addTransition(Supplier<Boolean> condition) {
        return addTransition(condition, null);
    }

    /** Adds a prebuilt {@link Transition}. See {@link #addTransition(Supplier, Case)}. */
    public final Case addTransition(Transition transition) {
        if (transition == null) {
            throw new IllegalArgumentException("Case.addTransition: transition cannot be null");
        }
        transitions.add(transition);
        return this;
    }

    /** Declarative transitions cannot fire before the case has been active this long. */
    public final Case minTimeMs(long ms) {
        if (ms < 0) {
            throw new IllegalArgumentException("Case.minTimeMs: cannot be negative");
        }
        minTimeMs = ms;
        return this;
    }

    /** The case is forced to finish after it has been active this long. */
    public final Case maxTimeMs(long ms) {
        if (ms < 0) {
            throw new IllegalArgumentException("Case.maxTimeMs: cannot be negative");
        }
        if (ms < minTimeMs) {
            throw new IllegalArgumentException("Case.maxTimeMs: cannot be less than minTimeMs");
        }
        maxTimeMs = ms;
        return this;
    }

    /** Where a {@link #maxTimeMs(long)} expiry goes; {@code null} advances to the next case. */
    public final Case fallback(Case fallback) {
        this.fallback = fallback;
        return this;
    }

    // Machine-side access (package-private).

    List<Transition> getTransitions() {
        return transitions;
    }

    long getMinTimeMs() {
        return minTimeMs;
    }

    long getMaxTimeMs() {
        return maxTimeMs;
    }

    Case getFallback() {
        return fallback;
    }

    Case takePendingTarget() {
        Case target = pendingTarget;
        pendingTarget = null;
        return target;
    }
}
