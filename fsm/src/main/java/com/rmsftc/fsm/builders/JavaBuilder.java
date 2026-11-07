package com.rmsftc.fsm.builders;

import com.rmsftc.fsm.Case;
import com.rmsftc.fsm.CaseBuilder;
import com.rmsftc.fsm.State;
import com.rmsftc.fsm.StateMachine;
import com.rmsftc.fsm.cases.InstantCase;
import com.rmsftc.fsm.cases.PrintCase;
import com.rmsftc.fsm.cases.RunCase;
import com.rmsftc.fsm.cases.StartEndCase;
import com.rmsftc.fsm.cases.WaitCase;
import com.rmsftc.fsm.cases.WaitUntilCase;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builds a {@link StateMachine} in Java, in the shape of BehaviorTrees'
 * {@code JavaBuilder}: one static entry point that takes a
 * {@link Consumer} configuring the builder.
 *
 * <pre>{@code
 * StateMachine machine = JavaBuilder.stateMachine(b -> b
 *         .addCase(intake)
 *         .waitMs(State.of("wait"), 500)
 *         .initial(State.of("intake")));
 * machine.start();
 * // in the loop: machine.update();
 * }</pre>
 */
public class JavaBuilder {
    private final List<Case> cases = new ArrayList<>();
    private State initial;
    private Supplier<Long> clock = System::nanoTime;

    /** Single entry point: configures a builder and returns the built machine. */
    public static StateMachine stateMachine(Consumer<JavaBuilder> block) {
        JavaBuilder builder = new JavaBuilder();
        block.accept(builder);
        return builder.build();
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

    /** Adds a case built from lambdas (see {@link CaseBuilder}). */
    public JavaBuilder caseBuilder(State state, Consumer<CaseBuilder> block) {
        CaseBuilder builder = new CaseBuilder(state);
        block.accept(builder);
        cases.add(builder);
        return this;
    }

    /** Adds a {@link WaitCase}: waits {@code millis}, then advances to the next case. */
    public JavaBuilder waitMs(State state, long millis) {
        cases.add(new WaitCase(state, millis));
        return this;
    }

    /** Adds a {@link WaitUntilCase}: waits for {@code condition}, then advances. */
    public JavaBuilder waitUntil(State state, Supplier<Boolean> condition) {
        cases.add(new WaitUntilCase(state, condition));
        return this;
    }

    /** Adds a {@link RunCase}: runs {@code action} every tick, forever. */
    public JavaBuilder run(State state, Runnable action) {
        cases.add(new RunCase(state, action));
        return this;
    }

    /** Adds an {@link InstantCase}: runs {@code action} once, then advances. */
    public JavaBuilder instant(State state, Runnable action) {
        cases.add(new InstantCase(state, action));
        return this;
    }

    /** Adds a {@link StartEndCase}: start action on entry, end action on exit. */
    public JavaBuilder startEnd(State state, Runnable onStart, Runnable onEnd) {
        cases.add(new StartEndCase(state, onStart, onEnd));
        return this;
    }

    /** Adds a {@link PrintCase}: prints {@code message} once, then advances. */
    public JavaBuilder print(State state, String message) {
        cases.add(new PrintCase(state, message));
        return this;
    }

    private StateMachine build() {
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
