package org.quiltmc.meta.update.cli.storage;

import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.nio.file.Path;

@SuppressWarnings("NotNullFieldNotInitialized")
public class FileBasedStorageConfig {

    @NotNull
    @CommandLine.Option(names = "--local-output-dir", required = true)
    private Path outputDir;

    public Path outputDir() {
        return outputDir;
    }
}
