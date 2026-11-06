package com.rmsftc.cmdlib

/**
 * What happens to a *running* command when another command that needs one of
 * the same tokens is scheduled at the same priority. This is a property
 * of the running command (the victim), following WPILib v2.
 */
enum class InterruptionBehavior {
    /** The running command ends (interrupted) and the incoming command is
     * scheduled. This is the default. */
    CANCEL_SELF,

    /** The incoming command is rejected and the running command keeps going. */
    CANCEL_INCOMING
}
