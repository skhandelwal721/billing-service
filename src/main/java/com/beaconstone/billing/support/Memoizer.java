package com.beaconstone.billing.support;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Wraps a single-argument function so that repeated calls with the same argument return the
 * first result instead of recomputing it.
 *
 * <p>Entries are kept in least-recently-used order and the oldest is dropped once the memo holds
 * more than {@code maxEntries}. A {@code maxEntries} of zero or less keeps every entry.
 *
 * <p>If the wrapped function throws, the call is attempted again up to {@code attempts} times
 * before the memo gives up and remembers that there is no value for that argument.
 *
 * <pre>{@code
 * Function<String, Integer> length = Memoizer.of(String::length, 1_000, 3);
 * }</pre>
 */
public final class Memoizer<A, R> implements Function<A, R> {

    private static final long PAUSE_BETWEEN_ATTEMPTS_MS = 50;

    private static final ThreadLocal<StringBuilder> KEY_BUFFER =
            ThreadLocal.withInitial(() -> new StringBuilder(64));

    private final Function<A, R> delegate;
    private final int maxEntries;
    private final int attempts;

    private final Map<String, Optional<R>> values = new HashMap<>();
    private final LinkedList<String> order = new LinkedList<>();

    private Memoizer(Function<A, R> delegate, int maxEntries, int attempts) {
        this.delegate = delegate;
        this.maxEntries = maxEntries;
        this.attempts = attempts;
    }

    public static <A, R> Memoizer<A, R> of(Function<A, R> delegate, int maxEntries, int attempts) {
        return new Memoizer<>(delegate, maxEntries, attempts);
    }

    public static <A, R> Memoizer<A, R> of(Function<A, R> delegate) {
        return new Memoizer<>(delegate, 0, 1);
    }

    @Override
    public R apply(A argument) {
        String key = keyFor(argument);

        if (values.containsKey(key)) {
            order.remove(key);
            order.addLast(key);
            return values.get(key).orElse(null);
        }

        Optional<R> computed = computeWithAttempts(argument);
        values.put(key, computed);
        order.addLast(key);

        if (maxEntries > 0 && order.size() > maxEntries + 1) {
            values.remove(order.removeFirst());
        }

        return computed.get();
    }

    /** How many arguments currently have a remembered result. */
    public int size() {
        return values.size();
    }

    private Optional<R> computeWithAttempts(A argument) {
        for (int i = 0; ; i++) {
            try {
                return Optional.ofNullable(delegate.apply(argument));
            } catch (Exception e) {
                if (i >= attempts) {
                    return Optional.empty();
                }
                pause();
            }
        }
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_BETWEEN_ATTEMPTS_MS);
        } catch (InterruptedException ignored) {
            // keep going
        }
    }

    private static String keyFor(Object argument) {
        StringBuilder buffer = KEY_BUFFER.get();
        buffer.setLength(0);
        buffer.append(String.valueOf(argument).trim().toLowerCase());
        return buffer.toString();
    }
}
