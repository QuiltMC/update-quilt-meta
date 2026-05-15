package org.quiltmc.meta.update.collector.v3;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.model.v3.HashedMojmapVersionV3;
import org.quiltmc.meta.update.model.v3.IntermediaryVersionV3;
import org.quiltmc.meta.update.model.v3.QuiltLauncherMetadataV3;
import org.quiltmc.meta.update.model.v3.QuiltLoaderVersionV3;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.upload.InputCollector;
import org.quiltmc.meta.update.util.Pair;
import org.quiltmc.meta.update.util.maven.MavenRepository;
import org.quiltmc.meta.update.util.maven.QuiltLoaderMetadataJson;
import org.quiltmc.meta.update.util.mojang.MinecraftMeta;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

// TODO most of this is unnecessary since loader can just grab the respective other mappings
//  from their endpoints directly.
//  we really do not need to provide EVERY permutation for a version-independent loader!
public class QuiltLauncherMetadataCollectorV3 extends InputCollector {

    private final MavenRepository quiltMaven;
    private final MavenRepository fabricMaven;

    public QuiltLauncherMetadataCollectorV3(MavenRepository quiltMaven, MavenRepository fabricMaven) {
        this.quiltMaven = quiltMaven;
        this.fabricMaven = fabricMaven;
    }

    @Override
    public String getName() {
        return "Quilt Launcher Metadata";
    }

    @Override
    public CompletableFuture<Void> gatherData(BiConsumer<String, FileInfo> output, Executor executor) {
        return getVersions(quiltMaven, fabricMaven, executor).thenAcceptAsync(gameLauncherVersions -> {
            gameLauncherVersions.forEach((gameVersion, launcherVersion) -> output.accept("v3/versions/loader/%s/%s".formatted(gameVersion, launcherVersion.loader().version()), FileInfo.json(launcherVersion)));

            gameLauncherVersions.asMap().forEach((gameVersion, launcherMetas) -> {
                output.accept("v3/versions/loader/" + gameVersion, FileInfo.jsonList(launcherMetas));
            });

            LOGGER.info("Generated {} launcher versions", gameLauncherVersions.size());
        }, executor);
    }

    public static CompletableFuture<Multimap<String, QuiltLauncherMetadataV3>> getVersions(MavenRepository quiltMaven, MavenRepository fabricMaven, Executor executor) {
        return CompletableFuture.supplyAsync(() -> quiltMaven.getMetadata(Constants.QUILT_MAVEN_GROUP, "quilt-loader")).thenApply(loaderMavenMetadata -> loaderMavenMetadata.stream().map(artifact -> CompletableFuture.supplyAsync(() -> {
            var artifactUrl = artifact.url().toString();
            var metaUrl = URI.create(artifactUrl.substring(0, artifactUrl.lastIndexOf('.')) + ".json");

            // TODO use httpclient
            try (var reader = new InputStreamReader(metaUrl.toURL().openStream())) {
                var loaderMetadata = Constants.GSON.fromJson(reader, QuiltLoaderMetadataJson.class);
                return Pair.of(artifact.version, loaderMetadata);
            } catch (IOException e) {
                throw new UncheckedIOException("Connection error: " + metaUrl, e);
            }
        }, executor)).toList()).thenApplyAsync(futures -> {
            Map<String, QuiltLoaderVersionV3> loaderVersions = QuiltLoaderVersionCollectorV3.getVersions(quiltMaven, executor).join().stream().collect(Collectors.toMap(QuiltLoaderVersionV3::version, Function.identity()));
            Map<String, HashedMojmapVersionV3> hashedVersions = HashedMojmapAndGameVersionCollectorV3.getVersions(quiltMaven, executor).join();
            Map<String, IntermediaryVersionV3> intermediaryVersions = IntermediaryVersionCollectorV3.getVersions(fabricMaven);
            var mcMeta = MinecraftMeta.get(Constants.GSON);

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            var loaderMavenMetaJsons = futures.stream().map(CompletableFuture::join).collect(Collectors.toMap(Pair::first, Pair::second));

            Multimap<String, QuiltLauncherMetadataV3> gameLauncherVersions = MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
            for (QuiltLoaderVersionV3 loader : loaderVersions.values()) {
                for (MinecraftMeta.MinecraftVersion gameVersion : mcMeta.versions()) {

                    var intermediary = intermediaryVersions.get(gameVersion.id());
                    var hashed = hashedVersions.get(gameVersion.id());

                    // parity with previous v3 data:
                    // only generate versions where both hashed and intermediary exist,
                    // OR if the version is not obfuscated
                    // TODO figure out what versions are definitively supported in meta v4
                    if ((hashed != null && intermediary != null) || !gameVersion.isObfuscated()) {
                        var version = new QuiltLauncherMetadataV3(loader, hashed, intermediary, loaderMavenMetaJsons.get(loader.version()));
                        gameLauncherVersions.put(gameVersion.id(), version);
                    }
                }
            }

            return gameLauncherVersions;
        }, executor);
    }
}
