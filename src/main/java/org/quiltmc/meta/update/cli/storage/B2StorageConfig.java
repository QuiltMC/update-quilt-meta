package org.quiltmc.meta.update.cli.storage;

import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

@SuppressWarnings("NotNullFieldNotInitialized")
public class B2StorageConfig {

    @NotNull
    @CommandLine.Option(names = "--b2-bucket-name", required = true)
    private String bucketName;

    @NotNull
    @CommandLine.Option(names = "--b2-application-key-id", required = true)
    private String applicationKeyId;

    @NotNull
    @CommandLine.Option(names = "--b2-application-key", required = true)
    private String applicationKey;

    public String bucketName() {
        return bucketName;
    }

    public String applicationKeyId() {
        return applicationKeyId;
    }

    public String applicationKey() {
        return applicationKey;
    }
}
