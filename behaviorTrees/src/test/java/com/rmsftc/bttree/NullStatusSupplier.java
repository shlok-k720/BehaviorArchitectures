package com.rmsftc.bttree;

import java.util.function.Supplier;

public final class NullStatusSupplier implements Supplier<NodeStatus> {
    @Override
    public NodeStatus get() {
        return null;
    }
}
