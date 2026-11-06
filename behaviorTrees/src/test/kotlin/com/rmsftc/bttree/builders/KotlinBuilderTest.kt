package com.rmsftc.bttree.builders

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.nodes.ParallelPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.function.Consumer
import java.util.function.Supplier

class KotlinBuilderTest {
    @Test
    fun `dsl builds nested selectors sequences actions timeouts and parallels`() {
        val blackboard = Blackboard()
        val actions = mutableListOf<String>()
        val tree = KotlinBuilder.rootTree(blackboard) {
            sequence("sequence") {
                condition("allowed") { true }
                action("action", { actions += "start" }, { NodeStatus.SUCCESS }, { actions += "end" })
            }
            selector("selector") { condition("unreached") { true } }
            reactiveSelector("named selector") { condition("unreached") { true } }
            waitMs("wait", 1_000) { condition("unreached") { true } }
            parallel("parallel", ParallelPolicy.SUCCESS) { condition("unreached") { true } }
        }

        assertEquals(NodeStatus.SUCCESS, tree.tick())
        assertEquals(listOf("start", "end"), actions)
    }

    @Test
    fun `consumer overloads and callback interceptor work from Java compatible callers`() {
        val blackboard = Blackboard()
        var callbackValue: Any? = null
        val tree = KotlinBuilder.rootTree(blackboard, Consumer { root ->
            root.action("running", null, Supplier { NodeStatus.RUNNING }, null)
            root.onCallbackTriggered(
                "callback branch",
                "key",
                Consumer { callbackValue = it },
                Consumer { sequence -> sequence.condition("unreached") { true } }
            )
        })

        assertEquals(NodeStatus.RUNNING, tree.tick())
        blackboard.set("key", 9)
        assertEquals(9, callbackValue)
    }

    @Test
    fun `dsl builds decorators and duration timeouts`() {
        val blackboard = Blackboard()
        var attempts = 0
        val tree = KotlinBuilder.rootTree(blackboard) {
            sequence("sequence") {
                inverter("not blocked") { condition("blocked") { false } }
                succeeder("best effort") { condition("optional") { false } }
                retry("intake", 3) { condition("has sample") { attempts++ >= 2 } }
                repeat("score twice", 2) { condition("scored") { true } }
                waitMs("wait", Duration.ofSeconds(5)) { condition("unreached") { true } }
            }
        }

        assertEquals(NodeStatus.SUCCESS, tree.tick())
        assertEquals(3, attempts)
    }

    @Test
    fun `decorator block with two children is rejected`() {
        val blackboard = Blackboard()

        assertThrows(IllegalArgumentException::class.java) {
            KotlinBuilder.rootTree(blackboard) {
                inverter("bad") {
                    condition("one") { true }
                    condition("two") { true }
                }
            }
        }
    }
}
