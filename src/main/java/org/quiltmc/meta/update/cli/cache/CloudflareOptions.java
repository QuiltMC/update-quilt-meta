package org.quiltmc.meta.update.cli.cache;

import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.net.URI;

@SuppressWarnings("NotNullFieldNotInitialized")
public class CloudflareOptions {

    @NotNull
    @CommandLine.Option(names = "--cf-zone-id", required = true)
    public String zoneId;

    @NotNull
    @CommandLine.Option(names = "--cf-access-token", required = true)
    public String accessToken;

    @NotNull
    @CommandLine.Option(names = "--cf-base-url", required = true)
    public URI baseUrl;
}
