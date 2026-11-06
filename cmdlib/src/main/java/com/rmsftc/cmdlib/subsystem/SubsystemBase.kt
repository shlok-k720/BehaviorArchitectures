package com.rmsftc.cmdlib.subsystem

import com.rmsftc.cmdlib.cmd.Command

/**
 * A [Subsystem] with just name storage and a default command. Most
 * mechanisms extend this and only override [periodic].
 */
abstract class SubsystemBase : Subsystem {
    private var name: String = javaClass.simpleName
    private var defaultCommand: Command? = null

    override fun getName(): String = name
    override fun setName(name: String) {
        this.name = name
    }

    override fun getDefaultCommand(): Command? = defaultCommand
    override fun setDefaultCommand(command: Command?) {
        defaultCommand = command
    }
}
