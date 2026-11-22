package com.rmsftc.ftc;

import com.rmsftc.utils.ftc.daemons.FTCDaemon;

/**
 * Optional daemon-driven ticking for an {@link ArchitectureManager}.
 *
 * <p>By default nothing changes: OpModes tick their manager themselves
 * ({@code manager.tickAll()} in the loop, or via {@link ArchitecturalOpMode}).
 * If you instead drive FTCUtils daemons each loop — executing every daemon
 * registered with {@code DaemonManager}, alongside e.g. {@code ClockManager}
 * — register your manager here once and the daemon ticks it for you:
 *
 * <pre>
 * ArchitectureDaemon.register(manager);
 * // each loop, with the other daemons:
 * DaemonManager.unmodifiable().forEach(d -&gt; d.daemon.execute());
 * </pre>
 *
 * <p>Registering a second manager replaces the first. {@link #unregister()}
 * stops the daemon from ticking anything (it becomes a no-op).
 */
public final class ArchitectureDaemon {
    private static ArchitectureManager manager;

    private ArchitectureDaemon() {}

    /** Ticks {@code manager} from the daemon loop from now on. */
    public static void register(ArchitectureManager manager) {
        ArchitectureDaemon.manager = manager;
    }

    /** Stops daemon ticking (the daemon becomes a no-op). */
    public static void unregister() {
        manager = null;
    }

    @FTCDaemon(name = "architecture-tick")
    public static void daemon() {
        ArchitectureManager current = manager;
        if (current != null) {
            current.tickAll();
        }
    }
}
