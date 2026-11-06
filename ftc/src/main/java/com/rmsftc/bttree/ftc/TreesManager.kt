package com.rmsftc.bttree.ftc

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.builders.JavaBuilder
import com.rmsftc.bttree.builders.KotlinBuilder
import com.rmsftc.bttree.builders.KotlinSelectorBuilder
import com.rmsftc.bttree.logger.LogSystem
import com.rmsftc.bttree.logger.Logger
import com.rmsftc.bttree.nodes.ReactiveSelectorNode
import org.firstinspires.ftc.robotcore.external.Telemetry
import java.util.function.Consumer
import kotlin.jvm.JvmName


/** A Utility class for Behavior Tree instantiation related to FTC */
class TreesManager {
    private val roots = mutableListOf<ReactiveSelectorNode>()

    constructor() {
        resetAndInitialize()
    }

    var blackboard = Blackboard()


    /** @return the blackboard to return
     */
    fun blackboard(): Blackboard {
        return blackboard
    }

    /**
     * Aborts all managed trees, clears them, and initializes a fresh blackboard.
     * Trees created before this call reference the previous blackboard and should
     * not be ticked afterward.
     */
    fun resetAndInitialize() {
        invalidateAll()
        roots.clear()
        blackboard = Blackboard()
    }

    /** Returns an immutable snapshot of the trees managed by this instance. */
    fun trees(): List<ReactiveSelectorNode> = roots.toList()

    /** Ticks every managed tree once and returns each result in creation order. */
    fun tickAll(): List<NodeStatus> = roots.map { it.tick() }

    /** Aborts every managed tree that is currently running. */
    fun invalidateAll() {
        roots.forEach { it.invalidate() }
    }

    /** Returns every managed tree and its descendants to their never-ticked state. */
    fun resetAll() {
        roots.forEach { it.reset() }
    }

    @JvmName("jTree")
    fun tree(builderConsumer: Consumer<JavaBuilder>): ReactiveSelectorNode {
        return JavaBuilder.rootTree(
            blackboard(),
            builderConsumer
        ).also(roots::add)
    }

    @JvmName("kTree")
    fun tree(builderConsumer: Consumer<KotlinSelectorBuilder>): ReactiveSelectorNode {
        return KotlinBuilder.rootTree(
            blackboard(),
            builderConsumer
        ).also(roots::add)
    }

    @JvmName("kDSLTree")
    fun tree(builder: KotlinSelectorBuilder.() -> Unit): ReactiveSelectorNode {
        return KotlinBuilder.rootTree(
            blackboard(),
            builder
        ).also(roots::add)
    }


    fun telemetryLogger(telemetry: Telemetry): LogSystem {
        return LogSystem { caption, data, timeStampMS ->
            telemetry.addData("$caption ($timeStampMS)", data)
        }.also(Logger::attachLogger)
    }
}