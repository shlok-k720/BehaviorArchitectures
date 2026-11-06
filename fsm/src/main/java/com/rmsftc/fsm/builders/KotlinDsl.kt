package com.rmsftc.fsm.builders

import com.rmsftc.fsm.Case
import com.rmsftc.fsm.SimpleCase
import com.rmsftc.fsm.StateMachine
import com.rmsftc.fsm.StateMachineListener
import java.util.function.Supplier

@DslMarker
annotation class FSMMarker

/**
 * One case inside the [stateMachine] DSL. Targets of [transition] and
 * [fallback] are case *names*, resolved when the machine is built.
 *
 * <pre>{@code
 * val machine = stateMachine {
 *     case("intake") {
 *         onEnter { intake.start() }
 *         onTick { telemetry.addData("intake", intake.power) }
 *         transition({ intake.isFull }, "shoot")
 *         maxTimeMs(3000)
 *         fallback("shoot")
 *     }
 *     case("shoot") {
 *         onEnter { shooter.fire() }
 *         transition({ shooter.done() }, "intake")
 *     }
 * }
 * }</pre>
 */
@FSMMarker
class CaseDsl internal constructor(val name: String) {
    internal var enterAction: (() -> Unit)? = null
    internal var tickAction: (() -> Unit)? = null
    internal var exitAction: (() -> Unit)? = null
    internal var minTimeMs: Long = 0
    internal var maxTimeMs: Long = Long.MAX_VALUE
    internal val transitions = ArrayList<Pair<() -> Boolean, String?>>()
    internal var fallbackName: String? = null

    /** Runs once when the case becomes active. */
    fun onEnter(action: () -> Unit) {
        enterAction = action
    }

    /** Runs every tick while the case is active. */
    fun onTick(action: () -> Unit) {
        tickAction = action
    }

    /** Runs once when the case stops being active. */
    fun onExit(action: () -> Unit) {
        exitAction = action
    }

    /**
     * A guarded transition to the case named [to]; [to] null advances to
     * the next case in the machine. Checked in the order added.
     */
    fun transition(condition: () -> Boolean, to: String? = null) {
        transitions.add(condition to to)
    }

    /** Declarative transitions cannot fire before the case has been active this long. */
    fun minTimeMs(ms: Long) {
        minTimeMs = ms
    }

    /** The case is forced to finish after it has been active this long. */
    fun maxTimeMs(ms: Long) {
        maxTimeMs = ms
    }

    /** Where a [maxTimeMs] expiry goes. */
    fun fallback(to: String) {
        fallbackName = to
    }
}

/** Builds a [StateMachine] with the [stateMachine] DSL. */
@FSMMarker
class KotlinBuilder {
    private val cases = ArrayList<Case>()
    private val dsls = ArrayList<CaseDsl>()
    private val byName = HashMap<String, Case>()
    private var initialName: String? = null
    private var clock: Supplier<Long> = Supplier { System.nanoTime() }
    private val listeners = ArrayList<StateMachineListener>()

    /**
     * Declares a case. The returned [Case] is already built (a [SimpleCase]
     * from the block's actions); its transitions are wired when the machine
     * is built. Names must be unique.
     */
    fun case(name: String, init: CaseDsl.() -> Unit): Case {
        if (byName.containsKey(name)) {
            throw IllegalArgumentException("KotlinBuilder: duplicate case name: $name")
        }
        val dsl = CaseDsl(name).apply(init)
        val c = SimpleCase(
            name,
            dsl.enterAction?.let { action -> Runnable { action() } },
            dsl.tickAction?.let { action -> Runnable { action() } },
            dsl.exitAction?.let { action -> Runnable { action() } }
        )
        cases.add(c)
        dsls.add(dsl)
        byName[name] = c
        return c
    }

    /** Adds a prebuilt case (built with Java or by subclassing [Case]). */
    fun add(case: Case) {
        val name = case.name
        if (name != null) {
            if (byName.containsKey(name)) {
                throw IllegalArgumentException("KotlinBuilder: duplicate case name: $name")
            }
            byName[name] = case
        }
        cases.add(case)
    }

    /** The case the machine starts from, by name (defaults to the first case declared). */
    fun initial(name: String) {
        initialName = name
    }

    /** The nanosecond clock (defaults to `System.nanoTime()`; inject a fake one for tests). */
    fun clock(supplier: Supplier<Long>) {
        clock = supplier
    }

    /** Registers a lifecycle listener. */
    fun listener(listener: StateMachineListener) {
        listeners.add(listener)
    }

    /** Registers a transition listener: called with the case left and the case entered. */
    fun onTransition(handler: (Case, Case) -> Unit) {
        listeners.add(object : StateMachineListener {
            override fun onTransition(machine: StateMachine, from: Case, to: Case) {
                handler(from, to)
            }
        })
    }

    internal fun build(): StateMachine {
        for ((c, dsl) in cases.zip(dsls)) {
            for ((condition, targetName) in dsl.transitions) {
                val target = targetName?.let {
                    byName[it]
                        ?: throw IllegalArgumentException("KotlinBuilder: unknown case: $it")
                }
                c.addTransition({ condition() }, target)
            }
            c.minTimeMs(dsl.minTimeMs)
            c.maxTimeMs(dsl.maxTimeMs)
            dsl.fallbackName?.let { name ->
                c.fallback(
                    byName[name]
                        ?: throw IllegalArgumentException("KotlinBuilder: unknown case: $name")
                )
            }
        }
        val ordered = ArrayList(cases)
        initialName?.let { name ->
            val initial = byName[name]
                ?: throw IllegalArgumentException("KotlinBuilder: unknown case: $name")
            ordered.remove(initial)
            ordered.add(0, initial)
        }
        val machine = StateMachine(clock, *ordered.toTypedArray())
        listeners.forEach { machine.addListener(it) }
        return machine
    }
}

/** Builds a [StateMachine] with the Kotlin DSL. See [CaseDsl]. */
fun stateMachine(init: KotlinBuilder.() -> Unit): StateMachine =
    KotlinBuilder().apply(init).build()
