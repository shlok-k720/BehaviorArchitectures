package com.rmsftc.fsm;

import java.util.function.Consumer;

/**
 * A {@link Case} built from lambdas, like Ivy's {@code CommandBuilder}: the
 * functional case. Use it for one-off cases; subclass {@link Case} for a
 * named, reusable one.
 *
 * <p>Defaults are all no-ops, so a bare builder runs forever until
 * something transitions the machine away — like Ivy's {@code NOOP}. The
 * lambdas run on the built case, so they can move the machine through it:
 *
 * <pre>{@code
 * CaseBuilder wait = new CaseBuilder(State.of("WAIT"));
 * wait.setOnExecute(() -> {
 *     if (intake.isFull()) wait.transitionToNext();
 * });
 * }</pre>
 */
public class CaseBuilder extends Case {
    private Runnable onStartAction;
    private Runnable onExecuteAction;
    private Consumer<Case> onTransitionAction;

    public CaseBuilder(State state) {
        super(state);
    }

    /** Like Ivy's {@code Command.build()}: a fresh builder for this state. */
    public static CaseBuilder build(State state) {
        return new CaseBuilder(state);
    }

    public CaseBuilder setOnStart(Runnable action) {
        onStartAction = action;
        return this;
    }

    public CaseBuilder setOnExecute(Runnable action) {
        onExecuteAction = action;
        return this;
    }

    /** The action receives the case being transitioned into (null on stop/finish). */
    public CaseBuilder setOnTransition(Consumer<Case> action) {
        onTransitionAction = action;
        return this;
    }

    @Override
    public void onStart() {
        if (onStartAction != null) {
            onStartAction.run();
        }
    }

    @Override
    public void onExecute() {
        if (onExecuteAction != null) {
            onExecuteAction.run();
        }
    }

    @Override
    public void onTransition(Case other) {
        if (onTransitionAction != null) {
            onTransitionAction.accept(other);
        }
    }
}
