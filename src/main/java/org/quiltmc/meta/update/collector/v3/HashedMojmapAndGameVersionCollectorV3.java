package org.quiltmc.meta.update.collector.v3;

import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.model.v3.GameVersionV3;
import org.quiltmc.meta.update.model.v3.HashedMojmapVersionV3;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.upload.InputCollector;
import org.quiltmc.meta.update.util.maven.MavenRepository;
import org.quiltmc.meta.update.util.mojang.MinecraftMeta;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class HashedMojmapAndGameVersionCollectorV3 extends InputCollector {

    private final MavenRepository maven;

    public HashedMojmapAndGameVersionCollectorV3(MavenRepository maven) {
        this.maven = maven;
    }

    @Override
    public String getName() {
        return "Hashed Mojmap and Game Versions";
    }

    @Override
    public CompletableFuture<Void> gatherData(BiConsumer<String, FileInfo> output, Executor executor) {
        return getVersions(maven, executor).thenAcceptAsync(hashed -> {
            var minecraftMeta = MinecraftMeta.get(Constants.GSON);

            hashed.forEach((gameVersion, version) -> {
                // TODO remove in v4
                output.accept("v3/versions/hashed/" + gameVersion, FileInfo.jsonList(List.of(version)));
            });

            output.accept("v3/versions/hashed", FileInfo.jsonList(hashed.values()));

            // TODO remove in v4
            output.accept("v3/versions/game/hashed", FileInfo.jsonList(hashed.keySet()));

            LOGGER.info("Found {} hashed versions", hashed.size());

            var filteredGameVersions = getGameVersions(minecraftMeta, hashed::containsKey);
            output.accept("v3/versions/game", FileInfo.jsonList(filteredGameVersions));

            var supportedPercentage = (filteredGameVersions.size() / (float) minecraftMeta.versions().size()) * 100;
            LOGGER.info("Found {} supported game versions ({}% of {} total)", filteredGameVersions.size(), String.format("%.1f", supportedPercentage), minecraftMeta.versions().size());
        }, executor);
    }

    public static CompletableFuture<Map<String, HashedMojmapVersionV3>> getVersions(MavenRepository maven, Executor executor) {
        return CompletableFuture.supplyAsync(() -> maven.getMetadata(Constants.QUILT_MAVEN_GROUP, "hashed"), executor).thenApply(metadata -> metadata.stream().map(artifact -> {
            var fileInfo = artifact.fileInfo(executor).join();
            return new HashedMojmapVersionV3(artifact.mavenId(), artifact.version, fileInfo.fileSize(), fileInfo.formatHashesForJson());
        }).collect(Collectors.toMap(HashedMojmapVersionV3::version, Function.identity())));
    }

    public static List<GameVersionV3> getGameVersions(MinecraftMeta minecraftMeta, Predicate<String> isValidVersion) {
        // TODO for v4 figure out what versions we want to support.
        // currently that means only those MC versions where hashed exists;
        // for fabric compat we might also want to list those where intermediary exists, but not hashed;
        // and likewise, might want to also list versions as supported where only mojmap exists
        return minecraftMeta.versions().stream().filter(it -> isValidVersion.test(it.id()) || !it.isObfuscated()).map(GameVersionV3::fromMojang).toList();
    }
}
