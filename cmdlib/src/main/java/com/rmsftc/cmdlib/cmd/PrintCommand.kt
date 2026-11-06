package com.rmsftc.cmdlib.cmd

/** Prints [message] to standard output once, then finishes. */
class PrintCommand(private val message: String) :
    InstantCommand(Runnable { println(message) })
