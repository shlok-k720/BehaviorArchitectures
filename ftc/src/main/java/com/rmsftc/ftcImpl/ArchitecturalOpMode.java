package com.rmsftc.ftcImpl;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

/**
 * A LinearOpMode base class that runs all three architectures through one
 * {@link ArchitectureManager}: behavior trees, state machines, and commands.
 *
 * <pre>
 * &#64;Autonomous
 * public class MyAuto extends ArchitecturalOpMode {
 *     &#64;Override
 *     protected void onInit() {
 *         // hardware setup: motors, servos, sensors
 *     }
 *
 *     &#64;Override
 *     protected void build(ArchitectureManager manager) {
 *         manager.tree(b -&gt; b.action("score", () -&gt; NodeStatus.SUCCESS));
 *         manager.commands(b -&gt; b
 *                 .runOnce(() -&gt; intake.start(), "intake")
 *                 .waitMs(500)
 *                 .runOnce(() -&gt; shooter.fire(), "shooter"));
 *     }
 * }
 * </pre>
 *
 * The lifecycle is fixed — {@link #runOpMode} is final, so subclasses use
 * the hooks instead of overriding it:
 * <ul>
 *   <li>{@link #onInit} — hardware setup, once, before {@link #build}.</li>
 *   <li>{@link #build} — build trees, machines, and commands on the manager,
 *       once, after {@link #onInit} and before {@code waitForStart()}.</li>
 *   <li>{@link #onLoop} — extra work once per loop, after the manager ticks
 *       everything. Telemetry is updated automatically after it.</li>
 *   <li>{@link #onStop} — cleanup when the OpMode stops; the base
 *       implementation aborts every managed tree/machine and cancels every
 *       scheduled command, so overrides must call {@code super.onStop()}.</li>
 * </ul>
 *
 * The manager's scheduler is not gated on OpMode state here: nothing runs
 * before {@code start()} because {@code tickAll()} only runs inside the
 * loop, and {@link #onStop} invalidates everything when the OpMode ends.
 * Commands built in {@link #build} with {@code manager.commands(...)} are
 * scheduled immediately and start running on the first loop tick.
 */
public class ArchitecturalOpMode extends LinearOpMode {

    /** The manager this OpMode runs: one shared blackboard, one scheduler, one loop call. */
    protected final ArchitectureManager manager = new ArchitectureManager();

    public ArchitecturalOpMode() {

    }

    /**
     * Hardware setup. Called once from {@link #runOpMode}, before
     * {@link #build}. Override to set up motors, servos, sensors, and
     * anything else the architectures need — the default does nothing.
     */
    protected void onInit() {
    }

    /**
     * Build the architectures (trees, machines, commands) on {@code manager}.
     * Called once from {@link #runOpMode}, after {@link #onInit} and before
     * {@code waitForStart()}. The default does nothing.
     */
    protected void build(ArchitectureManager manager) {
    }

    /**
     * Extra work once per loop, after {@link ArchitectureManager#tickAll}.
     * The default does nothing. Telemetry is updated automatically after
     * this returns.
     */
    protected void onLoop() {
    }

    @Override
    public final void runOpMode() {
        onInit();
        build(manager);
        waitForStart();
        manager.startAll();
        while (opModeIsActive()) {
            manager.tickAll();
            onLoop();
            telemetry.update();
        }
        onStop();
    }

    /**
     * Called when the OpMode stops: aborts every managed tree and machine
     * and cancels every scheduled command. Override to add cleanup, but
     * call {@code super.onStop()}.
     */
    public void onStop() {
        manager.invalidateAll();
    }

    /**
     * Aborts everything managed here and asks the framework to stop the
     * OpMode. Call it to end the run early (for example from a command or
     * a case that detects a fault).
     */
    public void interrupt() {
        manager.invalidateAll();
        requestOpModeStop();
    }

    public final void registerOpMode() {

    }
}
