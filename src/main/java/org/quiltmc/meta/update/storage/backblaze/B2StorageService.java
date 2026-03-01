package org.quiltmc.meta.update.storage.backblaze;

import com.backblaze.b2.client.B2ClientConfig;
import com.backblaze.b2.client.B2FilePolicy;
import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.B2UploadFileRequest;
import com.backblaze.b2.util.B2IoUtils;
import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.cli.storage.B2StorageConfig;
import org.quiltmc.meta.update.storage.AbstractStorageService;
import org.quiltmc.meta.update.storage.StorageManifest;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.util.http.ContentType;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class B2StorageService extends AbstractStorageService {

    private final B2StorageConfig config;
    private final B2StorageClient client;
    private String bucketId;

    public B2StorageService(B2StorageConfig config) {
        this.config = config;
        this.client = B2StorageClientFactory.createDefaultFactory().create(B2ClientConfig.builder(config.applicationKeyId(), config.applicationKey(), Constants.USER_AGENT).build());
    }

    @Override
    public void init() throws Exception {
        var bucket = Optional.ofNullable(client.getBucketOrNullByName(config.bucketName())).orElseThrow(() -> new NoSuchElementException("No B2 bucket found with name '%s'".formatted(config.bucketName())));
        this.bucketId = bucket.getBucketId();
    }

    @Override
    public Optional<StorageManifest> readExistingManifest() throws Exception {
        var tempFilePath = Files.createTempFile("quilt-meta-manifest", ".json.gz");

        try {
            // download manifest to temp file
            try {
                client.downloadByName(config.bucketName(), Constants.MANIFEST_PATH, B2ContentPathWriter.of(tempFilePath));
            } catch (B2Exception e) {
                if (e.getStatus() == 404) {
                    return Optional.empty();
                }

                // rethrow other errors
                throw e;
            }

            // parse the manifest
            try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(tempFilePath), 1024)))) {
                var manifest = Constants.GSON.fromJson(reader, StorageManifest.class);
                return Optional.ofNullable(manifest);
            }
        } finally {
            Files.deleteIfExists(tempFilePath);
        }
    }

    @Override
    public CompletableFuture<Void> uploadFiles(Map<String, FileInfo> files, ExecutorService executorService) {
        return CompletableFuture.supplyAsync(this::getPolicy, executorService).thenComposeAsync(filePolicy -> CompletableFuture.allOf(files.entrySet().stream().map(entry -> {
            var uploadRequest = B2UploadFileRequest.builder(bucketId, entry.getKey(), entry.getValue().contentType().value(), entry.getValue()).build();
            try {
                return wrapUpload(filePolicy, uploadRequest, executorService);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).toArray(CompletableFuture[]::new)));
    }

    @Override
    public CompletableFuture<Void> deleteFiles(List<String> filesToRemove, ExecutorService executorService) {
        return CompletableFuture.allOf(filesToRemove.stream().map(path -> CompletableFuture.runAsync(() -> {
            try {
                var fileInfo = client.getFileInfoByName(config.bucketName(), path);
                client.deleteFileVersion(fileInfo);
            } catch (B2Exception e) {
                if (e.getStatus() != 404) {
                    throw new RuntimeException("Unable to delete: " + path, e);
                }
            }
        }, executorService)).toArray(CompletableFuture[]::new));
    }

    @Override
    public CompletableFuture<Void> uploadManifest(StorageManifest manifest, ExecutorService executorService) throws Exception {
        var tempFilePath = Files.createTempFile("quilt-meta-manifest", ".json.gz");
        try (var writer = new OutputStreamWriter(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFilePath))))) {
            Constants.GSON.toJson(manifest, writer);
        }
        var source = B2PathContentSource.of(tempFilePath);
        var uploadRequest = B2UploadFileRequest.builder(bucketId, Constants.MANIFEST_PATH, ContentType.APPLICATION_GZIP.value(), source).build();
        var policy = getPolicy();

        return wrapUpload(policy, uploadRequest, executorService).whenComplete((unused, throwable) -> {
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to delete temporary manifest file", e);
            }
        });
    }

    private B2FilePolicy getPolicy() {
        try {
            return client.getFilePolicy();
        } catch (B2Exception e) {
            throw new RuntimeException("Unable to get account file policy", e);
        }
    }

    private CompletableFuture<Void> wrapUpload(B2FilePolicy filePolicy, B2UploadFileRequest uploadRequest, ExecutorService executorService) throws IOException {
        if (filePolicy.shouldBeLargeFile(uploadRequest.getContentSource().getContentLength())) {
            // only use upload executor for the actual requests, not the waiting part
            // however we still need to wrap it in a future so we dont block the main thread here
            return CompletableFuture.runAsync(() -> {
                try {
                    client.uploadLargeFile(uploadRequest, executorService);
                } catch (B2Exception e) {
                    throw new RuntimeException("Unable to upload file: " + uploadRequest.getFileName(), e);
                }
            });
        } else {
            return CompletableFuture.runAsync(() -> {
                try {
                    client.uploadSmallFile(uploadRequest);
                } catch (B2Exception e) {
                    throw new RuntimeException("Unable to upload file: " + uploadRequest.getFileName(), e);
                }
            }, executorService);
        }
    }

    @Override
    public void close() {
        B2IoUtils.closeQuietly(client);
    }

    @Override
    public String toString() {
        return "B2Storage['" + config.bucketName() + "']";
    }
}
