package com.rmsftc.fsm;

/**
 * Lifecycle events of a {@link StateMachine}. All methods are default
 * no-ops: implement only the ones you need.
 */
public interface StateMachineListener {
    /** The machine started (a case just became active for the first time). */
    default void onStarted(StateMachine machine) {
    }

    /** The machine moved from one case to another (including a restart of the same case). */
    default void onTransition(StateMachine machine, Case from, Case to) {
    }

    /** The machine ran out of cases and stopped on its own. */
    default void onFinished(StateMachine machine) {
    }

    /** The machine was stopped with {@link StateMachine#stop()}. */
    default void onStopped(StateMachine machine) {
    }
}
