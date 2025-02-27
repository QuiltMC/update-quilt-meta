package org.quiltmc.meta.update.collector.v3;

import org.quiltmc.meta.update.model.v3.IntermediaryVersionV3;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.upload.InputCollector;
import org.quiltmc.meta.update.util.maven.MavenRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IntermediaryVersionCollectorV3 extends InputCollector {

    private final MavenRepository quiltMaven;
    private final MavenRepository fabricMaven;

    public IntermediaryVersionCollectorV3(MavenRepository quiltMaven, MavenRepository fabricMaven) {
        this.quiltMaven = quiltMaven;
        this.fabricMaven = fabricMaven;
    }

    @Override
    public String getName() {
        return "Fabric Intermediary";
    }

    @Override
    public CompletableFuture<Void> gatherData(BiConsumer<String, FileInfo> output, Executor executor) {
        return CompletableFuture.supplyAsync(() -> getVersions(fabricMaven), executor).thenAcceptBothAsync(HashedMojmapAndGameVersionCollectorV3.getVersions(quiltMaven, executor), (intermediary, hashed) -> {
            intermediary = intermediary.entrySet().stream().filter(entry -> {
                // parity with previous data:
                // only generate versions where both hashed and intermediary exist
                return hashed.containsKey(entry.getKey());
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            intermediary.forEach((gameVersion, version) -> {
                // TODO remove in v4
                output.accept("v3/versions/intermediary/" + gameVersion, FileInfo.jsonList(List.of(version)));
            });

            output.accept("v3/versions/intermediary", FileInfo.jsonList(intermediary.values()));

            // TODO remove in v4
            output.accept("v3/versions/game/intermediary", FileInfo.jsonList(intermediary.keySet()));

            LOGGER.info("Found {} intermediary mappings", intermediary.size());
        });
    }

    public static Map<String, IntermediaryVersionV3> getVersions(MavenRepository maven) {
        return maven.getMetadata("net.fabricmc", "intermediary").stream().map(artifact -> new IntermediaryVersionV3(artifact.mavenId(), artifact.version)).collect(Collectors.toMap(IntermediaryVersionV3::version, Function.identity()));
    }
}
