package org.quiltmc.meta.update.util.function;

@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable> {

    void accept(T t) throws E;
}
