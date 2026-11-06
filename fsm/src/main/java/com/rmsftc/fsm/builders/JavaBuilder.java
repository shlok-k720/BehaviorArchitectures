package com.rmsftc.fsm.builders;

import com.rmsftc.fsm.Case;
import com.rmsftc.fsm.StateMachine;
import com.rmsftc.fsm.StateMachineListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Builds a {@link StateMachine} in Java:
 *
 * <pre>{@code
 * StateMachine machine = JavaBuilder.stateMachine()
 *         .addCase(intake)
 *         .addCase(shoot)
 *         .initial(intake)
 *         .onTransition((from, to) -> telemetry.addData("state", to.getName()))
 *         .build();
 * machine.start();
 * // in the loop: machine.update();
 * }</pre>
 */
public class JavaBuilder {
    private final List<Case> cases = new ArrayList<>();
    private Case initial;
    private Supplier<Long> clock = () -> System.nanoTime();
    private final List<StateMachineListener> listeners = new ArrayList<>();

    public static JavaBuilder stateMachine() {
        return new JavaBuilder();
    }

    private JavaBuilder() {
    }

    /** Adds a case to the end of the machine's list. */
    public JavaBuilder addCase(Case c) {
        cases.add(c);
        return this;
    }

    /** The case the machine starts from (defaults to the first case added). */
    public JavaBuilder initial(Case c) {
        initial = c;
        return this;
    }

    /** The nanosecond clock (defaults to {@code System.nanoTime()}; inject a fake one for tests). */
    public JavaBuilder clock(Supplier<Long> clock) {
        this.clock = clock;
        return this;
    }

    /** Registers a lifecycle listener. */
    public JavaBuilder listener(StateMachineListener listener) {
        listeners.add(listener);
        return this;
    }

    /** Registers a transition listener: called with the case left and the case entered. */
    public JavaBuilder onTransition(BiConsumer<Case, Case> handler) {
        listeners.add(new StateMachineListener() {
            @Override
            public void onTransition(StateMachine machine, Case from, Case to) {
                handler.accept(from, to);
            }
        });
        return this;
    }

    public StateMachine build() {
        List<Case> ordered = new ArrayList<>(cases);
        if (initial != null) {
            if (!ordered.contains(initial)) {
                throw new IllegalArgumentException(
                        "JavaBuilder: initial case was never added to the machine");
            }
            ordered.remove(initial);
            ordered.add(0, initial);
        }
        StateMachine machine = new StateMachine(clock, ordered.toArray(new Case[0]));
        listeners.forEach(machine::addListener);
        return machine;
    }
}
