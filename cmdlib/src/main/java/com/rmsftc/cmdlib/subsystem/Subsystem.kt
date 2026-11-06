package com.rmsftc.cmdlib.subsystem

import com.rmsftc.cmdlib.cmd.Command

/**
 * A robot mechanism (drivetrain, shooter, intake, ...) that commands act on.
 * A command declares the subsystems it needs as requirements; the scheduler
 * guarantees no two running commands require the same subsystem.
 *
 * The scheduler calls [periodic] once per tick for every registered
 * subsystem, and runs a subsystem's default command whenever no other
 * command requires it.
 */
interface Subsystem {
    /** Called once per scheduler tick. Read sensors and update state here. */
    fun periodic()

    fun getName(): String
    fun setName(name: String)

    /** The command that runs whenever nothing else requires this subsystem,
     * or null for none. */
    fun getDefaultCommand(): Command?
    fun setDefaultCommand(command: Command?)
}
