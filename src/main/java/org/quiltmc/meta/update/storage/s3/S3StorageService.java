package org.quiltmc.meta.update.storage.s3;

import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.cli.storage.S3StorageConfig;
import org.quiltmc.meta.update.storage.AbstractStorageService;
import org.quiltmc.meta.update.storage.StorageManifest;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.util.http.ContentType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class S3StorageService extends AbstractStorageService {

    private final S3StorageConfig config;
    private final S3Client client;

    public S3StorageService(S3StorageConfig config) {
        this.config = config;
        // @formatter:off
        this.client = S3Client.builder()
                .credentialsProvider(() -> AwsBasicCredentials.create(config.accessKeyId(), config.secretAccessKey()))
                .endpointOverride(config.endpoint())
                .forcePathStyle(config.usePathStyle())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .region(Optional.ofNullable(config.region()).map(Region::of).orElse(null)).build();
        // @formatter:on
    }

    @Override
    public void init() {
        try {
            client.headBucket(r -> r.bucket(config.bucketName()));
        } catch (NoSuchBucketException e) {
            LOGGER.warn("No bucket with name '{}' found, attempting to create one...", config.bucketName());
            client.createBucket(r -> r.bucket(config.bucketName()));
        }
    }

    @Override
    public Optional<StorageManifest> readExistingManifest() throws Exception {
        var tempFilePath = Files.createTempFile("quilt-meta-manifest", ".json.gz");

        try {
            // @formatter:off
            var request = GetObjectRequest.builder()
                    .bucket(config.bucketName())
                    .key(Constants.MANIFEST_PATH)
                    .build();
            // @formatter:on

            try (var inputStream = client.getObject(request, ResponseTransformer.toInputStream()); var outputStream = new BufferedOutputStream(Files.newOutputStream(tempFilePath))) {
                inputStream.transferTo(outputStream);
            } catch (NoSuchKeyException e) {
                return Optional.empty();
            }

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
        return CompletableFuture.allOf(files.entrySet().stream().map(entry -> CompletableFuture.runAsync(() -> {
            var file = entry.getValue();

            // @formatter:off
            var request = PutObjectRequest.builder()
                    .bucket(config.bucketName())
                    .key(entry.getKey())
                    .contentType(file.contentType().value())
                    .contentLength(file.getContentLength())
//                    .checksumSHA1(Base64.getEncoder().encodeToString(file.sha1Bytes()))
                    .build();
            // @formatter:on

            try (var stream = file.createInputStream()) {
                client.putObject(request, RequestBody.fromInputStream(stream, file.getContentLength()));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to close resource: " + entry.getKey(), e);
            }

        }, executorService)).toArray(CompletableFuture[]::new));
    }

    @Override
    public CompletableFuture<Void> deleteFiles(List<String> filesToRemove, ExecutorService executorService) {
        return CompletableFuture.allOf(filesToRemove.stream().map(path -> CompletableFuture.runAsync(() -> {
            // @formatter:off
            var request = DeleteObjectRequest.builder()
                    .bucket(config.bucketName())
                    .key(path)
                    .build();
            // @formatter:on
            client.deleteObject(request);
        }, executorService)).toArray(CompletableFuture[]::new));
    }

    @Override
    public CompletableFuture<Void> uploadManifest(StorageManifest manifest, ExecutorService executorService) throws Exception {
        var tempFilePath = Files.createTempFile("quilt-meta-manifest", ".json.gz");
        try {
            try (var writer = new OutputStreamWriter(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFilePath))))) {
                Constants.GSON.toJson(manifest, writer);
            }

            // @formatter:off
        var request = PutObjectRequest.builder()
                .bucket(config.bucketName())
                .key(Constants.MANIFEST_PATH)
                .contentType(ContentType.APPLICATION_GZIP.value())
                .checksumAlgorithm(ChecksumAlgorithm.SHA1)
                .build();
        // @formatter:on

            client.putObject(request, RequestBody.fromFile(tempFilePath));
        } finally {
            Files.deleteIfExists(tempFilePath);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() throws IOException {
        if (this.client != null) {
            this.client.close();
        }
    }
}
