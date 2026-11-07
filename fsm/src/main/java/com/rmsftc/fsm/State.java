package com.rmsftc.fsm;

/**
 * A state id: either a String or an enum value. Internally a state is just
 * its string form — the string itself, or the enum constant's
 * {@link Enum#name()} — so a state created from the string {@code "INTAKE"}
 * and one created from the enum constant {@code IntakeStates.INTAKE} are
 * the same state.
 */
public final class State {
    private final String name;

    private State(String name) {
        this.name = name;
    }

    /** A state from its string form. */
    public static State of(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("State.of: name cannot be null or empty");
        }
        return new State(name);
    }

    /** A state from an enum value (its {@link Enum#name()} is the string form). */
    public static State of(Enum<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("State.of: enum value cannot be null");
        }
        return new State(value.name());
    }

    /** The string form of this state. */
    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof State && name.equals(((State) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
