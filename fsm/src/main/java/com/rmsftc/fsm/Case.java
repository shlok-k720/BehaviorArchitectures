package com.rmsftc.fsm;

/**
 * One case of a switch/case state machine: a state that runs while it is
 * active. The machine ticks the current case once per loop
 * ({@link StateMachine#update()}); the case's {@link #onExecute()} is the
 * body of that `case` in the switch.
 *
 * <p>Lifecycle — {@link #onStart()} is the entry hook and
 * {@link #onTransition(Case)} is the exit hook (the opposite of start):
 * it runs on the case being left, just before the switch, with the case
 * being transitioned into (or null when the machine stops or finishes).
 *
 * <p>A case moves the machine with the {@code transition} utilities, which
 * use the {@link StateMachine} the case belongs to. A request is applied
 * at the end of the current tick, so {@link #onExecute()} always finishes
 * its tick before the switch happens. Or build a case from lambdas with
 * {@link CaseBuilder}.
 */
public abstract class Case {
    private final State state;
    private StateMachine machine;

    public Case(State state) {
        if (state == null) {
            throw new IllegalArgumentException("Case: state cannot be null");
        }
        this.state = state;
    }

    /** The state this case runs for. */
    public final State getState() {
        return state;
    }

    /** Runs once when this case becomes active. Default: nothing. */
    public void onStart() {
    }

    /** Runs every tick while this case is active: the body of the `case`. */
    public abstract void onExecute();

    /**
     * Runs on this case just before the machine leaves it.
     *
     * @param other the case being transitioned into, or null when the
     *              machine stops or finishes with no incoming case.
     */
    public void onTransition(Case other) {
    }

    /** Jumps to the case with this state, applied at the end of the current tick. */
    public final void transition(State next) {
        requireMachine();
        machine.transitionTo(next);
    }

    /** Jumps to this case, applied at the end of the current tick. */
    public final void transition(Case other) {
        if (other == null) {
            throw new IllegalArgumentException("Case.transition: target cannot be null");
        }
        requireMachine();
        machine.transitionTo(other.getState());
    }

    /** Advances to the next case in the machine's list, applied at the end of the current tick. */
    public final void transitionToNext() {
        requireMachine();
        machine.transitionToNext();
    }

    /** The machine this case belongs to (null until the case is added to a machine). */
    protected final StateMachine machine() {
        return machine;
    }

    /** Current time in nanoseconds, from the machine's clock. */
    protected final long nowNanos() {
        requireMachine();
        return machine.getClock().get();
    }

    // Machine-side access (package-private).

    void attach(StateMachine machine) {
        this.machine = machine;
    }

    private void requireMachine() {
        if (machine == null) {
            throw new IllegalStateException(
                    "Case " + state.name() + " is not part of a StateMachine");
        }
    }
}
