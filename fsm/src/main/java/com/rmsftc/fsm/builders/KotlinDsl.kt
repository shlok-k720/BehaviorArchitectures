package com.rmsftc.fsm.builders

import com.rmsftc.fsm.Case
import com.rmsftc.fsm.CaseBuilder
import com.rmsftc.fsm.State
import com.rmsftc.fsm.StateMachine
import java.util.function.Consumer
import java.util.function.Supplier

@DslMarker
annotation class FSMMarker

/**
 * One case inside the [stateMachine] DSL. The [onStart], [onExecute] and
 * [onTransition] blocks run with this as their receiver, so transitions
 * are called from inside them:
 *
 * <pre>{@code
 * val machine = stateMachine {
 *     case("intake") {
 *         onStart { intake.start() }
 *         onExecute { if (intake.isFull) transitionTo("shoot") }
 *     }
 *     case("shoot") {
 *         onStart { shooter.fire() }
 *         onExecute { if (shooter.done()) transitionToNext() }
 *     }
 * }
 * }</pre>
 *
 * Call [transitionTo] and [transitionToNext] only from inside those
 * blocks (they need the case to be part of a machine, which happens when
 * the machine is built).
 */
@FSMMarker
class CaseDsl internal constructor(internal val builder: CaseBuilder) {
    /** Runs once when the case becomes active. */
    fun onStart(action: CaseDsl.() -> Unit) {
        builder.setOnStart(Runnable { action.invoke(this) })
    }

    /** Runs every tick while the case is active. */
    fun onExecute(action: CaseDsl.() -> Unit) {
        builder.setOnExecute(Runnable { action.invoke(this) })
    }

    /** Runs on this case just before the machine leaves it; receives the case being transitioned into (null on stop/finish). */
    fun onTransition(action: CaseDsl.(Case?) -> Unit) {
        builder.setOnTransition(Consumer { other -> action.invoke(this, other) })
    }

    /** Jumps to the case with this state, applied at the end of the current tick. */
    fun transitionTo(state: State) {
        builder.transition(state)
    }

    /** Jumps to the case with this state name, applied at the end of the current tick. */
    fun transitionTo(name: String) {
        builder.transition(State.of(name))
    }

    /** Jumps to the case with this enum state, applied at the end of the current tick. */
    fun <E : Enum<E>> transitionTo(state: E) {
        builder.transition(State.of(state))
    }

    /** Advances to the next case in the machine's list, applied at the end of the current tick. */
    fun transitionToNext() {
        builder.transitionToNext()
    }
}

/** Builds a [StateMachine] with the [stateMachine] DSL. */
@FSMMarker
class KotlinBuilder {
    private val cases = ArrayList<Case>()
    private var initialState: State? = null
    private var clock: Supplier<Long> = Supplier { System.nanoTime() }

    /** Declares a case for this state. Returns the built case. */
    fun case(state: State, init: CaseDsl.() -> Unit): Case {
        val builder = CaseBuilder(state)
        CaseDsl(builder).init()
        cases.add(builder)
        return builder
    }

    /** Declares a case for this state name. Returns the built case. */
    fun case(name: String, init: CaseDsl.() -> Unit): Case =
        case(State.of(name), init)

    /** Declares a case for this enum state. Returns the built case. */
    fun <E : Enum<E>> case(state: E, init: CaseDsl.() -> Unit): Case =
        case(State.of(state), init)

    /** Adds a prebuilt case (built with Java or by subclassing [Case]). */
    fun add(case: Case) {
        cases.add(case)
    }

    /** The state the machine starts from (defaults to the first case declared). */
    fun initial(state: State) {
        initialState = state
    }

    /** The state the machine starts from, by name. */
    fun initial(name: String) {
        initialState = State.of(name)
    }

    /** The state the machine starts from, by enum value. */
    fun <E : Enum<E>> initial(state: E) {
        initialState = State.of(state)
    }

    /** The nanosecond clock (defaults to `System.nanoTime()`; inject a fake one for tests). */
    fun clock(supplier: Supplier<Long>) {
        clock = supplier
    }

    internal fun build(): StateMachine {
        val ordered = ArrayList(cases)
        initialState?.let { state ->
            val initial = ordered.find { it.state == state }
                ?: throw IllegalArgumentException("KotlinBuilder: no case with state: ${state.name()}")
            ordered.remove(initial)
            ordered.add(0, initial)
        }
        return StateMachine(clock, *ordered.toTypedArray())
    }
}

/** Builds a [StateMachine] with the Kotlin DSL. See [CaseDsl]. */
fun stateMachine(init: KotlinBuilder.() -> Unit): StateMachine =
    KotlinBuilder().apply(init).build()
