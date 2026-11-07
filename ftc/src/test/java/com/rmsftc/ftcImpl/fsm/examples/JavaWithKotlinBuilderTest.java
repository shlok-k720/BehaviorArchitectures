package com.rmsftc.ftcImpl.fsm.examples;

import com.rmsftc.ftcImpl.fsm.FakeClock;
import com.rmsftc.fsm.State;
import com.rmsftc.fsm.StateMachine;
import com.rmsftc.ftcImpl.StateMachines.CaseDsl;
import com.rmsftc.ftcImpl.StateMachines.KotlinBuilder;
import com.rmsftc.fsm.cases.WaitCase;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Example: the same intake -> wait -> shoot machine, built in Java with
 * the Kotlin builder. The Kotlin DSL's blocks take {@link Consumer}s from
 * Java, so each block is an explicitly-typed {@code Consumer<CaseDsl>}
 * (a bare lambda would be ambiguous between the Kotlin-lambda and the
 * Consumer overloads).
 */
class JavaWithKotlinBuilderTest {

    @Test
    void buildsAndRunsAnIntakeWaitShootMachine() {
        AtomicBoolean full = new AtomicBoolean(false);
        AtomicBoolean shot = new AtomicBoolean(false);
        FakeClock clock = new FakeClock();

        Consumer<CaseDsl> intakeBlock = dsl -> {
            Consumer<CaseDsl> onExecute = d -> {
                if (full.get()) d.transitionTo("wait");
            };
            dsl.onExecute(onExecute);
        };
        Consumer<CaseDsl> shootBlock = dsl -> {
            Consumer<CaseDsl> onExecute = d -> {
                if (shot.get()) d.transitionToNext();
            };
            dsl.onExecute(onExecute);
        };

        StateMachine machine = KotlinBuilder.stateMachine(b -> {
            // addCase, not case: case is a keyword in Java.
            b.addCase(State.of("intake"), intakeBlock);
            b.add(new WaitCase(State.of("wait"), 100));
            b.addCase(State.of("shoot"), shootBlock);
            b.clock(clock.supplier());
        });

        machine.start();
        assertEquals(State.of("intake"), machine.getCurrentState());

        machine.update();
        assertEquals(State.of("intake"), machine.getCurrentState());

        full.set(true);
        machine.update();
        assertEquals(State.of("wait"), machine.getCurrentState());

        clock.advanceMs(100);
        machine.update();
        assertEquals(State.of("shoot"), machine.getCurrentState());

        shot.set(true);
        machine.update();
        assertTrue(machine.isFinished());
    }
}
