package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.Status

abstract class Command : ICommand {
    private var isStarted = false
    override fun execute(): Status {
        if (!isStarted) {
            start()
            isStarted = true
        }
        loop()
        val status = status()
        if (!status.isRunning()) {
            end()
            isStarted = false
        }
        return status
    }

    abstract fun start()
    abstract fun loop()
    abstract fun status(): Status
    abstract fun end()
    abstract override fun invalidate()

}