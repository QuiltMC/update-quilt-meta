package org.quiltmc.meta.update.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public abstract class InputCollector {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    public abstract String getName();

    public abstract CompletableFuture<Void> gatherData(BiConsumer<String, FileInfo> output, Executor executor);
}
