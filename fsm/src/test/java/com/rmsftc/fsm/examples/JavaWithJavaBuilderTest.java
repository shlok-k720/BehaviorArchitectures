package com.rmsftc.fsm.examples;

import com.rmsftc.fsm.FakeClock;
import com.rmsftc.fsm.State;
import com.rmsftc.fsm.StateMachine;
import com.rmsftc.fsm.builders.JavaBuilder;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Example: the same intake -> wait -> shoot machine, built in Java with
 * {@link JavaBuilder}. The intake case runs until the intake is full, the
 * wait case pauses 100 ms, and the shoot case runs until the shot is done.
 */
class JavaWithJavaBuilderTest {

    @Test
    void buildsAndRunsAnIntakeWaitShootMachine() {
        AtomicBoolean full = new AtomicBoolean(false);
        AtomicBoolean shot = new AtomicBoolean(false);
        FakeClock clock = new FakeClock();

        StateMachine machine = JavaBuilder.stateMachine(b -> b
                .caseBuilder(State.of("intake"), intake -> intake
                        .setOnExecute(() -> {
                            if (full.get()) intake.transition(State.of("wait"));
                        }))
                .waitMs(State.of("wait"), 100)
                .caseBuilder(State.of("shoot"), shoot -> shoot
                        .setOnExecute(() -> {
                            if (shot.get()) shoot.transitionToNext();
                        }))
                .clock(clock.supplier()));

        machine.start();
        assertEquals(State.of("intake"), machine.getCurrentState());

        machine.update();
        assertEquals(State.of("intake"), machine.getCurrentState());

        full.set(true);
        machine.update();
        assertEquals(State.of("wait"), machine.getCurrentState());

        clock.advanceMs(99);
        machine.update();
        assertEquals(State.of("wait"), machine.getCurrentState());

        clock.advanceMs(1);
        machine.update();
        assertEquals(State.of("shoot"), machine.getCurrentState());

        shot.set(true);
        machine.update();
        assertTrue(machine.isFinished());
    }
}
