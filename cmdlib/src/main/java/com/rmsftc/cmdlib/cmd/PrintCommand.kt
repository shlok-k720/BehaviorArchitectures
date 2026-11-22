package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.Captions

import com.rmsftc.utils.logger.Logger

/** Prints [message] to standard output once, then finishes. */
class PrintCommand(private val message: String) :
    InstantCommand(Runnable {
        println(message)
        Logger.log(Captions.CMD_PRINT, "message=$message")
    })
