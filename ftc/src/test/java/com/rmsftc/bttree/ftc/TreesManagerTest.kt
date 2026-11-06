package com.rmsftc.bttree.ftc

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.builders.JavaBuilder
import java.util.function.Consumer
import java.util.function.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreesManagerTest {
    @Test
    fun `tickAll ticks managed trees in creation order`() {
        val manager = TreesManager()
        manager.tree(Consumer<JavaBuilder> { builder ->
            builder.action("success", Supplier { NodeStatus.SUCCESS })
        })
        manager.tree(Consumer<JavaBuilder> { builder ->
            builder.action("running", Supplier { NodeStatus.RUNNING })
        })

        assertEquals(listOf(NodeStatus.SUCCESS, NodeStatus.RUNNING), manager.tickAll())
        assertEquals(2, manager.trees().size)
    }

    @Test
    fun `resetAndInitialize invalidates and removes managed trees`() {
        val manager = TreesManager()
        var ended = false
        manager.tree(Consumer<JavaBuilder> { builder ->
            builder.action(
                "running",
                Runnable {},
                Supplier { NodeStatus.RUNNING },
                Runnable { ended = true }
            )
        })
        manager.tickAll()
        manager.blackboard().set("target", "backdrop")

        manager.resetAndInitialize()

        assertTrue(ended)
        assertTrue(manager.trees().isEmpty())
        assertFalse(manager.blackboard().has("target"))
    }

}
