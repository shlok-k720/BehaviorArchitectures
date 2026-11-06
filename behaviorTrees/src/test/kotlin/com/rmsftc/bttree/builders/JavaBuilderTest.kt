package com.rmsftc.bttree.builders

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.nodes.ParallelPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.function.Consumer
import java.util.function.Supplier

class JavaBuilderTest {
    @Test
    fun `builds and executes nested node types`() {
        val blackboard = Blackboard()
        val actions = mutableListOf<String>()
        val tree = JavaBuilder.rootTree(blackboard, Consumer { root ->
            root.sequence("sequence", Consumer { sequence ->
                sequence.condition("allowed") { true }
                sequence.action(
                    "action",
                    Runnable { actions += "start" },
                    Supplier { NodeStatus.SUCCESS },
                    Runnable { actions += "end" }
                )
            })
            root.reactiveSelector("selector", Consumer { it.condition("unreached") { true } })
            root.waitMs("wait", 1_000, Consumer { it.condition("unreached") { true } })
            root.parallel("parallel", ParallelPolicy.SUCCESS, Consumer { it.condition("unreached") { true } })
        })

        assertEquals(NodeStatus.SUCCESS, tree.tick())
        assertEquals(listOf("start", "end"), actions)
    }

    @Test
    fun `callback interceptor registers while building even when branch is not ticked`() {
        val blackboard = Blackboard()
        var callbackValue: Any? = null
        JavaBuilder.rootTree(blackboard, Consumer { root ->
            root.action("running", null, Supplier { NodeStatus.RUNNING }, null)
            root.onCallbackTriggered(
                "callback branch",
                "target",
                Consumer { callbackValue = it },
                Consumer { it.condition("unreached") { true } }
            )
        })

        blackboard.set("target", "updated")

        assertEquals("updated", callbackValue)
    }

    @Test
    fun `builds decorators duration timeouts and tick only actions`() {
        val blackboard = Blackboard()
        var attempts = 0
        val tree = JavaBuilder.rootTree(blackboard, Consumer { root ->
            root.sequence("sequence", Consumer { sequence ->
                sequence.inverter("not blocked", Consumer { it.condition("blocked") { false } })
                sequence.succeeder("best effort", Consumer { it.condition("optional") { false } })
                sequence.retry("intake", 3, Consumer { it.condition("has sample") { attempts++ >= 2 } })
                sequence.repeat("score twice", 2, Consumer { it.condition("scored") { true } })
                sequence.waitMs("wait", Duration.ofSeconds(5), Consumer { it.condition("unreached") { true } })
                sequence.action("tick only", Supplier { NodeStatus.SUCCESS })
            })
        })

        assertEquals(NodeStatus.SUCCESS, tree.tick())
        assertEquals(3, attempts)
    }
}
