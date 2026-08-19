package com.wayne.statusbarforceclose;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class BridgeObserverRegistry<T> {
    private final Map<String, Consumer<T>> observers = new LinkedHashMap<>();

    synchronized void register(String observerId, Consumer<T> observer) {
        requireId(observerId);
        observers.put(observerId, Objects.requireNonNull(observer, "observer"));
    }

    synchronized boolean remove(String observerId) {
        return observers.remove(observerId) != null;
    }

    void notifyObservers(T value) {
        List<Map.Entry<String, Consumer<T>>> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(observers.entrySet());
        }
        for (Map.Entry<String, Consumer<T>> entry : snapshot) {
            try {
                entry.getValue().accept(value);
            } catch (RuntimeException failure) {
                synchronized (this) {
                    if (observers.get(entry.getKey()) == entry.getValue()) {
                        observers.remove(entry.getKey());
                    }
                }
            }
        }
    }

    private static void requireId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("observerId must not be blank");
        }
    }
}
