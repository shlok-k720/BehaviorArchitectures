package com.rmsftc.cmdlib.cmd

import com.rmsftc.util.logger.Logger

/** Prints [message] to standard output once, then finishes. */
class PrintCommand(private val message: String) :
    InstantCommand(Runnable {
        println(message)
        Logger.log(Logger.CMD_PRINT, "message=$message")
    })
