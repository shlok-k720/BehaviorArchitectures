package com.rmsftc.bttree.nodes;


import com.rmsftc.bttree.NodeStatus;

import java.util.List;

@FunctionalInterface
public interface ParallelPolicy {
    NodeStatus status(List<NodeStatus> statuses);

    /**
     * Wait for every child to finish, then SUCCESS regardless of
     * whether individual children succeeded or failed. Any child
     * still RUNNING keeps the whole node RUNNING. Mixed or all-failed
     * results are therefore reported as SUCCESS: use SUCCESS if a
     * failure must fail the parallel node.
     */
    ParallelPolicy JOIN = statuses -> {
        for (NodeStatus status : statuses) {
            if (status == NodeStatus.RUNNING) return NodeStatus.RUNNING;
        }
        return NodeStatus.SUCCESS;
    };

    /**
     * RUNNING while any child is RUNNING, otherwise SUCCESS only if
     * every child succeeded, FAILURE if any child failed.
     */
    ParallelPolicy SUCCESS = statuses -> {
        if (JOIN.status(statuses) == NodeStatus.RUNNING) return NodeStatus.RUNNING;
        for (NodeStatus status : statuses) {
            if (status != NodeStatus.SUCCESS) return NodeStatus.FAILURE;
        }
        return NodeStatus.SUCCESS;
    };

    /**
     * RUNNING while any child is RUNNING, otherwise FAILURE only if
     * every child failed, SUCCESS otherwise — including mixed
     * success/failure results. Despite the name, this policy does
     * not fail the parallel node when only some children fail.
     */
    ParallelPolicy FAILURE = statuses -> {
        if (JOIN.status(statuses) == NodeStatus.RUNNING) return NodeStatus.RUNNING;
        for (NodeStatus status : statuses) {
            if (status != NodeStatus.FAILURE) return NodeStatus.SUCCESS;
        }
        return NodeStatus.FAILURE;
    };

    /**
     * RUNNING while any child is RUNNING, otherwise SUCCESS if at
     * least one child succeeded, FAILURE only if every child failed.
     * Unlike FAILURE, the name says what it does. An empty child
     * list reports FAILURE: no child succeeded.
     */
    ParallelPolicy ANY_SUCCESS = statuses -> {
        if (JOIN.status(statuses) == NodeStatus.RUNNING) return NodeStatus.RUNNING;
        for (NodeStatus status : statuses) {
            if (status == NodeStatus.SUCCESS) return NodeStatus.SUCCESS;
        }
        return NodeStatus.FAILURE;
    };

    /**
     * RUNNING while any child is RUNNING, otherwise SUCCESS when at
     * least {@code requiredSuccesses} children succeeded, FAILURE
     * otherwise. Use it for "two out of three sensors agree" style
     * redundancy checks that none of the fixed policies express.
     *
     * @param requiredSuccesses how many children must succeed; must be at least 1.
     */
    static ParallelPolicy atLeast(int requiredSuccesses) {
        if (requiredSuccesses < 1) {
            throw new IllegalArgumentException(
                    "requiredSuccesses must be at least 1, was " + requiredSuccesses);
        }
        return statuses -> {
            if (JOIN.status(statuses) == NodeStatus.RUNNING) return NodeStatus.RUNNING;
            int successes = 0;
            for (NodeStatus status : statuses) {
                if (status == NodeStatus.SUCCESS) successes++;
            }
            return successes >= requiredSuccesses ? NodeStatus.SUCCESS : NodeStatus.FAILURE;
        };
    }

}
