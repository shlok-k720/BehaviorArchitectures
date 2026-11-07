package com.rmsftc.fsm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs a switch/case state machine: an ordered list of {@link Case}s, one
 * active at a time, each bound to a {@link State}. The OpMode loop calls
 * {@link #update()} once per loop; the machine ticks the current case
 * ({@link Case#onExecute()}), then applies any transition the case (or
 * outside code) requested.
 *
 * <p>A case ends when it asks to: {@link Case#transition(State)} jumps to
 * the case with that state, {@link Case#transition(Case)} jumps to a case
 * directly, and {@link Case#transitionToNext()} advances to the next case
 * in the list. Advancing past the last case stops the machine
 * ({@link #isFinished()}). On every switch the outgoing case's
 * {@link Case#onTransition(Case)} runs first (with the incoming case, or
 * null on stop/finish), then the incoming case's {@link Case#onStart()}.
 *
 * <p>At most one transition is applied per {@link #update()} tick, after
 * {@link Case#onExecute()} returns. The machine's clock
 * ({@link #getClock()}, nanoseconds, {@code System.nanoTime()} by default)
 * is the single time source for cases that need one — see
 * {@link Case#nowNanos()} — so tests inject one fake clock and all timing
 * follows it.
 */
public class StateMachine {
    private final List<Case> cases = new ArrayList<>();
    private final Map<State, Case> byState = new LinkedHashMap<>();
    private final Supplier<Long> clock;
    private int index = 0;
    private boolean running = false;
    private State pendingState;
    private boolean pendingAdvance;

    /** A machine over {@code cases} in order, using {@code System.nanoTime()}. */
    public StateMachine(Case... cases) {
        this(() -> System.nanoTime(), cases);
    }

    /** A machine over {@code cases} in order, with an injected nanosecond clock (for tests). */
    public StateMachine(Supplier<Long> clock, Case... cases) {
        if (clock == null) {
            throw new IllegalArgumentException("StateMachine: clock cannot be null");
        }
        this.clock = clock;
        for (Case c : cases) {
            addCase(c);
        }
    }

    /** Adds a case to the end of the list. Two cases cannot share a {@link State}. */
    public StateMachine addCase(Case c) {
        if (c == null) {
            throw new IllegalArgumentException("StateMachine.addCase: case cannot be null");
        }
        if (byState.containsKey(c.getState())) {
            throw new IllegalArgumentException(
                    "StateMachine.addCase: duplicate state: " + c.getState().name());
        }
        cases.add(c);
        byState.put(c.getState(), c);
        c.attach(this);
        return this;
    }

    /** The cases in order (unmodifiable view). */
    public List<Case> getCases() {
        return Collections.unmodifiableList(cases);
    }

    /** The nanosecond clock cases use for timing (see {@link Case#nowNanos()}). */
    public Supplier<Long> getClock() {
        return clock;
    }

    /** Begins from the first case. If a case was active, it is transitioned out of first. */
    public void start() {
        startAt(0);
    }

    /** Begins from the case with this state. */
    public void start(State initial) {
        Case c = byState.get(initial);
        if (c == null) {
            throw new IllegalArgumentException(
                    "StateMachine.start: no case with state: " + initial.name());
        }
        startAt(cases.indexOf(c));
    }

    private void startAt(int i) {
        if (cases.isEmpty()) {
            return;
        }
        if (running) {
            switchTo(cases.get(i));
        } else {
            index = i;
            running = true;
            pendingState = null;
            pendingAdvance = false;
            cases.get(index).onStart();
        }
    }

    /** One tick: runs the current case, then applies a requested transition, if any. */
    public void update() {
        if (!running) {
            return;
        }
        Case current = cases.get(index);
        current.onExecute();

        if (pendingState != null) {
            State target = pendingState;
            pendingState = null;
            pendingAdvance = false;
            switchTo(byState.get(target));
        } else if (pendingAdvance) {
            pendingAdvance = false;
            advance();
        }
    }

    /**
     * Requests a jump to the case with this state, applied at the end of the
     * current tick (or on the next {@link #update()} if called between
     * ticks). The last request in a tick wins.
     */
    public void transitionTo(State state) {
        if (state == null) {
            throw new IllegalArgumentException("StateMachine.transitionTo: state cannot be null");
        }
        if (!byState.containsKey(state)) {
            throw new IllegalArgumentException(
                    "StateMachine.transitionTo: no case with state: " + state.name());
        }
        if (!running) {
            throw new IllegalStateException("StateMachine.transitionTo: machine is not running");
        }
        pendingState = state;
        pendingAdvance = false;
    }

    /** Requests an advance to the next case in the list (see {@link #transitionTo(State)}). */
    public void transitionToNext() {
        if (!running) {
            throw new IllegalStateException("StateMachine.transitionToNext: machine is not running");
        }
        pendingAdvance = true;
        pendingState = null;
    }

    /** Stops the machine; the current case's {@code onTransition(null)} runs. A later {@link #start()} begins again. */
    public void stop() {
        if (!running) {
            return;
        }
        cases.get(index).onTransition(null);
        running = false;
        pendingState = null;
        pendingAdvance = false;
    }

    /** Stops the machine and resets it so the next {@link #start()} begins from the first case. */
    public void reset() {
        stop();
        index = 0;
    }

    /** Whether the machine ran out of cases (or was never started). */
    public boolean isFinished() {
        return !running;
    }

    public boolean isRunning() {
        return running;
    }

    /** The currently active case, or null when the machine is not running. */
    public Case getCurrentCase() {
        return running ? cases.get(index) : null;
    }

    /** The state of the currently active case, or null when the machine is not running. */
    public State getCurrentState() {
        Case current = getCurrentCase();
        return current == null ? null : current.getState();
    }

    private void switchTo(Case target) {
        Case from = cases.get(index);
        from.onTransition(target);
        index = cases.indexOf(target);
        cases.get(index).onStart();
    }

    private void advance() {
        if (index + 1 < cases.size()) {
            switchTo(cases.get(index + 1));
        } else {
            cases.get(index).onTransition(null);
            running = false;
        }
    }
}
