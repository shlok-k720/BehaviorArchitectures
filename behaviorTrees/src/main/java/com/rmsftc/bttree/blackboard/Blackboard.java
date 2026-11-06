package com.rmsftc.bttree.blackboard;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Blackboard {
    private final Map<String, Object> data = new HashMap<>();
    private final Map<String, List<BlackboardCallback<?>>> callbacks = new HashMap<>();

    public void registerCallback(String key, @NotNull BlackboardCallback<?> callback) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(callback, "callback");
        if (!callbacks.containsKey(key)) {
            callbacks.put(key, new ArrayList<>());
        }
        Objects.requireNonNull(callbacks.get(key)).add(callback);
    }

    public void registerCallback(Enum<?> key, @NotNull BlackboardCallback<?> callback) {
        registerCallback(convertToString(key), callback);
    }

    public void deregisterCallback(String key, BlackboardCallback<?> callback) {
        if(callback == null) return;
        List<BlackboardCallback<?>> callbackList = callbacks.get(key);
        if (callbackList != null) {
            callbackList.remove(callback);
            if (callbackList.isEmpty()) {
                callbacks.remove(key);
            }
        }
    }

    public void deregisterCallback(Enum<?> key, BlackboardCallback<?> callback) {
        deregisterCallback(convertToString(key), callback);
    }

    public List<BlackboardCallback<?>> getCallbacks(String key) {
        // Return a copy: callers must not be able to mutate the
        // blackboard's internal callback list.
        return new ArrayList<>(callbacks.getOrDefault(key, new ArrayList<>()));
    }

    public List<BlackboardCallback<?>> getCallbacks(Enum<?> key) {
        return getCallbacks(convertToString(key));
    }

    // Store a value
    public void set(String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        data.put(key, value);

        List<BlackboardCallback<?>> callbackList = callbacks.get(key);
        if (callbackList != null && !callbackList.isEmpty()) {
            // Snapshot: a callback may register, deregister, or set
            // the same key, which must not corrupt this iteration.
            // Invoke every callback even if one throws, then rethrow
            // the first failure so it is not silently swallowed.
            List<BlackboardCallback<?>> snapshot = new ArrayList<>(callbackList);
            RuntimeException firstFailure = null;
            for (BlackboardCallback<?> callback : snapshot) {
                try {
                    invokeCallback(callback, key, value);
                } catch (RuntimeException e) {
                    if (firstFailure == null) {
                        firstFailure = e;
                    }
                }
            }
            if (firstFailure != null) {
                throw firstFailure;
            }
        }
    }

    public void set(Enum<?> key, Object value) {
        set(convertToString(key), value);
    }

    @SuppressWarnings("unchecked")
    private <T> void invokeCallback(BlackboardCallback<T> callback, String key, Object value) {
        callback.onChange(key, (T) value);
    }

    // Retrieve a value with automatic Type Casting
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) return null;
        return type.cast(value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Enum<?> key, Class<T> type) {
        return get(convertToString(key), type);
    }

    // Retrieve a value with a fallback default if it doesn't exist
    // (or if the stored value is not of the requested type). A null
    // default is returned without storing anything.
    public <T> T getOrDefault(String key, Class<T> type, T defaultValue) {
        Object raw = data.get(key);
        if (raw != null && type.isInstance(raw)) {
            return type.cast(raw);
        }
        if (defaultValue == null) {
            return null;
        }
        set(key, defaultValue);
        return defaultValue;
    }

    public <T> T getOrDefault(Enum<?> key, Class<T> type, T defaultValue) {
        return getOrDefault(convertToString(key), type, defaultValue);
    }

    // Pure read: like get(), but returns null instead of throwing when
    // the stored value is not of the requested type, and — unlike
    // getOrDefault() — never writes anything into the board. Use it
    // for conditions that poll optional state without mutating it.
    public <T> T getIfPresent(String key, Class<T> type) {
        Object raw = data.get(key);
        if (raw != null && type.isInstance(raw)) {
            return type.cast(raw);
        }
        return null;
    }

    public <T> T getIfPresent(Enum<?> key, Class<T> type) {
        return getIfPresent(convertToString(key), type);
    }

    // Store a value only if the key is not already present. Returns
    // the value now stored for the key (the existing one, or the new
    // one when the key was absent). Unlike a has() check followed by
    // set(), the check and the write happen together, and unlike
    // getOrDefault() this fires callbacks only when a value is
    // actually stored. Throws ClassCastException if the key already
    // holds a value of a different type, like get() does.
    public <T> T putIfAbsent(String key, Class<T> type, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        Object existing = data.get(key);
        if (existing != null) {
            return type.cast(existing);
        }
        set(key, value);
        return value;
    }

    public <T> T putIfAbsent(Enum<?> key, Class<T> type, T value) {
        return putIfAbsent(convertToString(key), type, value);
    }

    // Check if a key exists (useful for condition nodes)
    public boolean has(String key) {
        return data.containsKey(key);
    }

    public boolean has(Enum<?> key) {return has(convertToString(key));}

    // Clear specific keys between autonomous runs. Note: this also
    // removes the callbacks registered for that key. To clear only
    // the value (keeping callbacks) use removeValue(); to drop only
    // the callbacks (keeping the value) use removeCallbacks().
    public void remove(String key) {
        data.remove(key);
        callbacks.remove(key);
    }

    public void remove(Enum<?> key) {
        remove(convertToString(key));
    }

    // Remove the stored value for a key, keeping any callbacks
    // registered for it. Use this when resetting state between runs
    // without losing interceptors (e.g. an emergency-stop callback).
    public void removeValue(String key) {
        data.remove(key);
    }

    public void removeValue(Enum<?> key) {
        removeValue(convertToString(key));
    }

    // Deregister every callback for a key, keeping the stored value.
    public void removeCallbacks(String key) {
        callbacks.remove(key);
    }

    public void removeCallbacks(Enum<?> key) {
        removeCallbacks(convertToString(key));
    }


    // Note: this also removes all registered callbacks.
    public void clearAll() {
        data.clear();
        callbacks.clear();
    }


    private static String convertToString(Enum<?> e) {
        Objects.requireNonNull(e, "key");
        // Stable, readable key: declaring class + constant name.
        // No hashCode (opaque and JVM-specific) and no package
        // lookup (null for a default-package enum).
        return e.getDeclaringClass().getName() + "#" + e.name();
    }
}
