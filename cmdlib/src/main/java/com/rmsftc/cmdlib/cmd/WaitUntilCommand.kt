package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import java.util.function.BooleanSupplier

/** Finishes as soon as [condition] becomes true. */
class WaitUntilCommand(private val condition: BooleanSupplier) : Command() {
    override fun start() {}

    override fun loop() {}

    override fun status(): Status = Status.of(!condition.asBoolean)

    override fun end(endCondition: EndCondition) {}
}
