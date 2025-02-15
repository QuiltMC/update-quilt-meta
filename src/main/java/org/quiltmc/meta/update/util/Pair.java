package org.quiltmc.meta.update.util;

import java.util.Objects;

public record Pair<F, S>(F first, S second) {

    public static <F, S> Pair<F, S> of(F first, S second) {
        return new Pair<>(first, second);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Pair<?, ?>(Object first1, Object second1) && Objects.equals(first(), first1) && Objects.equals(second(), second1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first(), second());
    }
}
