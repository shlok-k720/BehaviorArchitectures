package com.rmsftc.cmdlib.trigger

/** Which edge a [Trigger.debounce] waits to settle. */
enum class DebounceType {
    /** The output goes true only after the input has been true for the
     * debounce time; it goes false the moment the input goes false. */
    RISING,

    /** The output goes false only after the input has been false for the
     * debounce time; it goes true the moment the input goes true. */
    FALLING,

    /** The output follows the input, but only after the input has been
     * stable for the debounce time. */
    BOTH
}
