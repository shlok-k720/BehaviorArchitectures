package com.rmsftc.util.blackboard;

@FunctionalInterface
public interface BlackboardCallback<T> {
    void onChange(String key, T value);
}
