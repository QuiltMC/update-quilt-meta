package org.quiltmc.meta.update.cli;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.meta.update.cli.cache.CloudflareOptions;
import picocli.CommandLine;

public class CacheOptions {

    @Nullable
    @CommandLine.ArgGroup(exclusive = false)
    public CloudflareOptions cloudflare;

}
