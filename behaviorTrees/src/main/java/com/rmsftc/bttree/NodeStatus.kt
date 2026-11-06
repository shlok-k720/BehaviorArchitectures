package com.rmsftc.bttree

enum class NodeStatus {
    SUCCESS,
    FAILURE,
    RUNNING;

    /** True once the node has finished, i.e. SUCCESS or FAILURE. */
    fun isTerminal(): Boolean = this != RUNNING

    /** True while the node is still in progress. */
    fun isRunning(): Boolean = this == RUNNING
}