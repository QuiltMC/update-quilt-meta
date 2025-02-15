package org.quiltmc.meta.update.storage.file;

import com.google.common.collect.Iterables;
import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.cli.storage.FileBasedStorageConfig;
import org.quiltmc.meta.update.storage.AbstractStorageService;
import org.quiltmc.meta.update.storage.StorageManifest;
import org.quiltmc.meta.update.upload.FileInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.StreamSupport;

public class FileBasedStorageService extends AbstractStorageService {

    private final Path outputPath;
    private final Path filePath;
    private final Path metaPath;

    public FileBasedStorageService(FileBasedStorageConfig config) {
        this.outputPath = config.outputDir().normalize();
        this.filePath = outputPath.resolve("files");
        this.metaPath = outputPath.resolve("meta");
    }

    @Override
    public void init() throws Exception {
        Files.createDirectories(metaPath);
        Files.createDirectories(filePath);
    }

    @Override
    public Optional<StorageManifest> readExistingManifest() throws Exception {
        var path = this.metaPath.resolve("manifest.json");

        if(Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                return Optional.ofNullable(Constants.GSON.fromJson(reader, StorageManifest.class));
            }
        }

        return Optional.empty();
    }

    @Override
    public CompletableFuture<Void> uploadFiles(Map<String, FileInfo> files, ExecutorService executorService) {
        return CompletableFuture.allOf(files.entrySet().stream().map(entry -> CompletableFuture.runAsync(() -> {
            var rawPath = entry.getKey();
            var expectedExt = "." + entry.getValue().contentType().getFileExtension();
            if (!rawPath.endsWith(expectedExt)) {
                rawPath += expectedExt;
            }

            try {
                var path = resolveValidPath(rawPath);

                Files.createDirectories(path.getParent());
                Files.copy(entry.getValue().createInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("Error while writing to local file: " + rawPath, e);
            }
        }, executorService)).toArray(CompletableFuture[]::new));
    }

    @Override
    public CompletableFuture<Void> deleteFiles(List<String> filesToRemove, ExecutorService executorService) {
        return CompletableFuture.allOf(StreamSupport.stream(Iterables.partition(filesToRemove, 100).spliterator(), false).map(list -> CompletableFuture.runAsync(() -> list.forEach(rawPath -> {
            try {
                var targetPath = resolveValidPath(rawPath);
                Files.deleteIfExists(targetPath);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to delete: " + rawPath, e);
            }
        }))).toArray(CompletableFuture[]::new));
    }

    @Override
    public CompletableFuture<Void> uploadManifest(StorageManifest manifest, ExecutorService executorService) throws Exception {
        var path = this.metaPath.resolve("manifest.json");

        Files.createDirectories(path.getParent());
        try (var writer = Files.newBufferedWriter(path)) {
            Constants.GSON.toJson(manifest, writer);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public String toString() {
        return "FileBasedStorage['" + outputPath.toAbsolutePath() + "']";
    }

    private Path resolveValidPath(String path) throws IOException {
        var result = filePath.resolve(path).normalize();

        if (!result.startsWith(filePath)) {
            throw new NoSuchFileException("Cannot write outside of output path: " + path);
        }

        return result;
    }
}
