package com.rmsftc.fsm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs a switch/case state machine: an ordered list of {@link Case}s, one
 * active at a time. The OpMode loop calls {@link #update()} once per loop;
 * the machine ticks the current case ({@link Case#execute()}), then moves
 * on when a transition fires.
 *
 * <p>How a case ends, in priority order:
 * <ol>
 *   <li>Imperative: the case called {@link Case#transition(Case)} (or
 *       outside code called {@link #transitionTo(Case)}) — jumps to that
 *       case, even mid-sequence.</li>
 *   <li>Declarative: the first {@link Transition} whose condition is true,
 *       once the case has been active at least its minimum time — jumps to
 *       its target, or advances to the next case when the target is null.</li>
 *   <li>Timeout: the case has been active at least its maximum time —
 *       goes to its fallback case, or advances to the next case.</li>
 * </ol>
 *
 * <p>Advancing past the last case stops the machine ({@link #isFinished()}).
 * On every switch the old case's {@link Case#onExit()} runs, then the new
 * case's {@link Case#onEnter()} runs and its timer restarts.
 *
 * <p>The model follows 23641 Cuberobot's {@code util/fsm} (State/
 * Transition/StateMachine), with direct case references instead of string
 * names, a per-tick {@link Case#execute()}, and an injected nanosecond
 * clock so tests can drive time with a fake clock instead of sleeping.
 */
public class StateMachine {
    private final List<Case> cases = new ArrayList<>();
    private final List<StateMachineListener> listeners = new ArrayList<>();
    private final Supplier<Long> clock;
    private int index = 0;
    private boolean running = false;
    private long caseStartNanos = 0;
    private Case pendingTarget;

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

    /** Adds a case to the end of the list. */
    public StateMachine addCase(Case c) {
        if (c == null) {
            throw new IllegalArgumentException("StateMachine.addCase: case cannot be null");
        }
        cases.add(c);
        return this;
    }

    /** The cases in order (unmodifiable snapshot view). */
    public List<Case> getCases() {
        return Collections.unmodifiableList(cases);
    }

    public void addListener(StateMachineListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("StateMachine.addListener: listener cannot be null");
        }
        listeners.add(listener);
    }

    /** Begins from the first case. If a case was active, its {@code onExit()} runs first. */
    public void start() {
        startAt(0);
    }

    /** Begins from {@code initial}, which must already be part of this machine. */
    public void start(Case initial) {
        int i = cases.indexOf(initial);
        if (i < 0) {
            throw new IllegalArgumentException(
                    "StateMachine.start: initial case is not part of this machine");
        }
        startAt(i);
    }

    private void startAt(int i) {
        if (cases.isEmpty()) {
            return;
        }
        if (running) {
            cases.get(index).onExit();
        }
        index = i;
        running = true;
        pendingTarget = null;
        activateCurrent();
        listeners.forEach(l -> l.onStarted(this));
    }

    /** One tick: runs the current case, then moves on if a transition fired. */
    public void update() {
        if (!running) {
            return;
        }
        Case current = cases.get(index);
        current.execute();

        long elapsedMs = (clock.get() - caseStartNanos) / 1_000_000L;
        Case next = null;
        boolean advance = false;

        Case pending = current.takePendingTarget();
        if (pending != null) {
            next = pending;
        } else if (pendingTarget != null) {
            next = pendingTarget;
            pendingTarget = null;
        } else {
            if (elapsedMs >= current.getMinTimeMs()) {
                for (Transition t : current.getTransitions()) {
                    if (t.getCondition().get()) {
                        next = t.getTarget();
                        advance = (next == null);
                        break;
                    }
                }
            }
            if (next == null && !advance && elapsedMs >= current.getMaxTimeMs()) {
                Case fallback = current.getFallback();
                if (fallback != null) {
                    next = fallback;
                } else {
                    advance = true;
                }
            }
        }

        if (next != null) {
            switchTo(next);
        } else if (advance) {
            advance();
        }
    }

    /**
     * Requests a move to {@code target} from outside a case (for example,
     * OpMode code forcing a state). Applied at the end of the current tick,
     * or on the next {@link #update()} if called between ticks. An explicit
     * {@link Case#transition(Case)} from inside the running case wins over
     * this on the same tick.
     */
    public void transitionTo(Case target) {
        if (target == null) {
            throw new IllegalArgumentException("StateMachine.transitionTo: target cannot be null");
        }
        if (!cases.contains(target)) {
            throw new IllegalArgumentException(
                    "StateMachine.transitionTo: target is not part of this machine");
        }
        pendingTarget = target;
    }

    /** Stops the machine; the current case's {@code onExit()} runs. A later {@link #start()} begins again. */
    public void stop() {
        if (!running) {
            return;
        }
        cases.get(index).onExit();
        running = false;
        listeners.forEach(l -> l.onStopped(this));
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

    private void switchTo(Case target) {
        int i = cases.indexOf(target);
        if (i < 0) {
            throw new IllegalStateException(
                    "StateMachine: transition target is not part of this machine"
                            + (target.getName() != null ? ": " + target.getName() : ""));
        }
        Case from = cases.get(index);
        from.onExit();
        index = i;
        activateCurrent();
        listeners.forEach(l -> l.onTransition(this, from, target));
    }

    private void advance() {
        if (index + 1 < cases.size()) {
            Case from = cases.get(index);
            from.onExit();
            index++;
            activateCurrent();
            Case to = cases.get(index);
            listeners.forEach(l -> l.onTransition(this, from, to));
        } else {
            cases.get(index).onExit();
            running = false;
            listeners.forEach(l -> l.onFinished(this));
        }
    }

    private void activateCurrent() {
        caseStartNanos = clock.get();
        cases.get(index).onEnter();
    }
}
