package org.quiltmc.meta.update;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class MainTest {

    @Test
    void testCliParsingLocalStorage() {
        // valid local dir
        String[] validLocalStorage = new String[]{"--local-output-dir", "../__test_output"};
        Assertions.assertDoesNotThrow(() -> new CommandLine(new Main()).parseArgs(validLocalStorage));
    }

    @Test
    void testCliParsingBackblazeB2Storage() {
        // missing B2 application key
        String[] misconfiguredB2Storage = new String[]{"--b2-bucket-name", "test", "--b2-application-key-id", "invalid"};
        Assertions.assertThrows(CommandLine.ParameterException.class, () -> new CommandLine(new Main()).parseArgs(misconfiguredB2Storage));

        // missing bucket name
        String[] misconfiguredB2StorageBucket = new String[]{"--b2-application-key-id", "invalid", "--b2-application-key", "invalid"};
        Assertions.assertThrows(CommandLine.ParameterException.class, () -> new CommandLine(new Main()).parseArgs(misconfiguredB2StorageBucket));

        // valid B2 storage
        String[] validB2Storage = new String[]{"--b2-bucket-name", "test", "--b2-application-key-id", "invalid", "--b2-application-key", "invalid"};
        Assertions.assertDoesNotThrow(() -> new CommandLine(new Main()).parseArgs(validB2Storage));
    }

    @Test
    void testCliParsingS3Storage() {
        // missing S3 endpoint or region
        String[] misconfiguredS3Storage = new String[]{"--s3-bucket-name", "test", "--s3-access-key-id", "invalid", "--s3-secret-access-key", "invalid"};
        Assertions.assertThrows(CommandLine.ParameterException.class, () -> new CommandLine(new Main()).parseArgs(misconfiguredS3Storage));

        // has both endpoint and region
        String[] validS3storage = new String[]{"--s3-bucket-name", "test", "--s3-access-key-id", "invalid", "--s3-secret-access-key", "invalid", "--s3-region", "us-east-2", "--s3-endpoint-url", "https://s3.us-east-2.amazonaws.com"};
        Assertions.assertDoesNotThrow(() -> new CommandLine(new Main()).parseArgs(validS3storage));

        // has endpoint but no region
        String[] validS3storageEndpoint = new String[]{"--s3-bucket-name", "test", "--s3-access-key-id", "invalid", "--s3-secret-access-key", "invalid", "--s3-endpoint-url", "https://example.com"};
        Assertions.assertDoesNotThrow(() -> new CommandLine(new Main()).parseArgs(validS3storageEndpoint));

        // has valid region, but no endpoint
        String[] validS3StorageRegion = new String[]{"--s3-bucket-name", "test", "--s3-access-key-id", "invalid", "--s3-secret-access-key", "invalid", "--s3-region", "us-east-2"};
        Assertions.assertDoesNotThrow(() -> new CommandLine(new Main()).parseArgs(validS3StorageRegion));
    }

    @Test
    void testCliParsingCache() {
        // valid local dir but missing cloudflare token
        String[] invalidCfCacheArgs = new String[]{"--local-output-dir", "./__quilt_meta_test", "--cf-zone-id", "invalid", "--cf-base-url", "https://example.com"};
        Assertions.assertThrows(CommandLine.ParameterException.class, () -> new CommandLine(new Main()).parseArgs(invalidCfCacheArgs));
    }

    @Test
    void testRequiresStorageConfig() {
        Assertions.assertThrowsExactly(IllegalStateException.class, () -> new Main().call());
    }
}
