package com.rmsftc.fsm.builders;

import com.rmsftc.fsm.Case;
import com.rmsftc.fsm.State;
import com.rmsftc.fsm.StateMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Builds a {@link StateMachine} in Java:
 *
 * <pre>{@code
 * StateMachine machine = JavaBuilder.stateMachine()
 *         .addCase(intake)
 *         .addCase(shoot)
 *         .initial(State.of("intake"))
 *         .build();
 * machine.start();
 * // in the loop: machine.update();
 * }</pre>
 */
public class JavaBuilder {
    private final List<Case> cases = new ArrayList<>();
    private State initial;
    private Supplier<Long> clock = () -> System.nanoTime();

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

    /** The state the machine starts from (defaults to the first case added). */
    public JavaBuilder initial(State state) {
        initial = state;
        return this;
    }

    /** The nanosecond clock (defaults to {@code System.nanoTime()}; inject a fake one for tests). */
    public JavaBuilder clock(Supplier<Long> clock) {
        this.clock = clock;
        return this;
    }

    public StateMachine build() {
        List<Case> ordered = new ArrayList<>(cases);
        if (initial != null) {
            Case first = null;
            for (Case c : ordered) {
                if (c.getState().equals(initial)) {
                    first = c;
                    break;
                }
            }
            if (first == null) {
                throw new IllegalArgumentException(
                        "JavaBuilder: no case with state: " + initial.name());
            }
            ordered.remove(first);
            ordered.add(0, first);
        }
        return new StateMachine(clock, ordered.toArray(new Case[0]));
    }
}
