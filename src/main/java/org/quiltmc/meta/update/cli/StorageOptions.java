package org.quiltmc.meta.update.cli;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.meta.update.cli.storage.B2StorageConfig;
import org.quiltmc.meta.update.cli.storage.FileBasedStorageConfig;
import org.quiltmc.meta.update.cli.storage.S3StorageConfig;
import picocli.CommandLine;

public class StorageOptions {

    @Nullable
    @CommandLine.ArgGroup(exclusive = false)
    public B2StorageConfig b2;

    @Nullable
    @CommandLine.ArgGroup(exclusive = false)
    public FileBasedStorageConfig file;

    @Nullable
    @CommandLine.ArgGroup(exclusive = false)
    public S3StorageConfig s3;
}
