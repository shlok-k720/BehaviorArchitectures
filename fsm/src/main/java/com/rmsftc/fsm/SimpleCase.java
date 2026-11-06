package com.rmsftc.fsm;

/**
 * A {@link Case} built from lambdas instead of a subclass, for the
 * 23641 style (a state that just runs actions) and for the builders.
 * Any action may be null, meaning "do nothing".
 */
public class SimpleCase extends Case {
    private final Runnable onEnterAction;
    private final Runnable onTickAction;
    private final Runnable onExitAction;

    public SimpleCase(String name, Runnable onEnter, Runnable onTick, Runnable onExit) {
        super(name);
        this.onEnterAction = onEnter;
        this.onTickAction = onTick;
        this.onExitAction = onExit;
    }

    /** A case that only runs {@code onTick} every tick. */
    public SimpleCase(Runnable onTick) {
        this(null, null, onTick, null);
    }

    /** A named case that only runs {@code onTick} every tick. */
    public SimpleCase(String name, Runnable onTick) {
        this(name, null, onTick, null);
    }

    @Override
    public void execute() {
        if (onTickAction != null) {
            onTickAction.run();
        }
    }

    @Override
    public void onEnter() {
        if (onEnterAction != null) {
            onEnterAction.run();
        }
    }

    @Override
    public void onExit() {
        if (onExitAction != null) {
            onExitAction.run();
        }
    }
}
