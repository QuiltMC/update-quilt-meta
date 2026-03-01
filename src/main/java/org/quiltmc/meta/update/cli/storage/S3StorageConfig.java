package org.quiltmc.meta.update.cli.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.net.URI;
import java.util.Objects;

@SuppressWarnings("NotNullFieldNotInitialized")
public class S3StorageConfig {

    @NotNull
    @CommandLine.Option(names = "--s3-bucket-name", required = true)
    private String bucketName;

    @CommandLine.Option(names = "--s3-use-path-style", defaultValue = "false")
    private boolean usePathStyle;

    @NotNull
    @CommandLine.Option(names = "--s3-access-key-id", required = true)
    private String accessKeyId;

    @NotNull
    @CommandLine.Option(names = "--s3-secret-access-key", required = true)
    private String secretAccessKey;

    @CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
    private EndpointOrRegion endpointOrRegion;

    public String bucketName() {
        return bucketName;
    }

    public String accessKeyId() {
        return accessKeyId;
    }

    public String secretAccessKey() {
        return secretAccessKey;
    }

    public @Nullable URI endpoint() {
        return endpointOrRegion.endpoint;
    }

    public String region() {
        return Objects.requireNonNullElse(endpointOrRegion.region, "custom");
    }

    public boolean usePathStyle() {
        return usePathStyle;
    }

    private static class EndpointOrRegion {
        @Nullable
        @CommandLine.Option(names = "--s3-endpoint-url")
        private URI endpoint;

        @Nullable
        @CommandLine.Option(names = "--s3-region")
        private String region;
    }
}
