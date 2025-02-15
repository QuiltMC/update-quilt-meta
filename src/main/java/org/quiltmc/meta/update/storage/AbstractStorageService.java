package org.quiltmc.meta.update.storage;

import org.quiltmc.meta.update.upload.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public abstract class AbstractStorageService implements Closeable {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    public abstract void init() throws Exception;

    public abstract Optional<StorageManifest> readExistingManifest() throws Exception;

    public abstract CompletableFuture<Void> uploadFiles(Map<String, FileInfo> files, ExecutorService executorService);

    public abstract CompletableFuture<Void> deleteFiles(List<String> filesToRemove, ExecutorService executorService);

    public abstract CompletableFuture<Void> uploadManifest(StorageManifest manifest, ExecutorService executorService) throws Exception;
}
