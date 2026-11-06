package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.Status

interface ICommand {
    fun execute(): Status
    fun invalidate()
}