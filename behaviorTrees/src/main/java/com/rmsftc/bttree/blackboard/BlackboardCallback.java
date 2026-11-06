package com.rmsftc.bttree.blackboard;

@FunctionalInterface
public interface BlackboardCallback<T> {
    void onChange(String key, T value);
}
