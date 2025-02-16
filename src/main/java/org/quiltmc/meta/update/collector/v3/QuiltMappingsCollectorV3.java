package org.quiltmc.meta.update.collector.v3;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.model.v3.QuiltMappingsVersionV3;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.upload.InputCollector;
import org.quiltmc.meta.update.util.maven.MavenRepository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public class QuiltMappingsCollectorV3 extends InputCollector {

    private final MavenRepository quiltMaven;

    public QuiltMappingsCollectorV3(MavenRepository quiltMaven) {
        this.quiltMaven = quiltMaven;
    }

    @Override
    public String getName() {
        return "Quilt Mappings";
    }

    @Override
    public CompletableFuture<Void> gatherData(BiConsumer<String, FileInfo> output, Executor executor) {
        return getVersions(quiltMaven, executor).thenAccept(gameQuiltMappings -> {
            gameQuiltMappings.asMap().forEach((gameVersion, mappingsVersions) -> output.accept("v3/versions/quilt-mappings/" + gameVersion, FileInfo.jsonList(mappingsVersions)));

            output.accept("v3/versions/quilt-mappings", FileInfo.jsonList(gameQuiltMappings.values()));

            // TODO remove in v4
            output.accept("v3/versions/game/quilt-mappings", FileInfo.jsonList(gameQuiltMappings.keys()));

            LOGGER.info("Found {} quilt mappings", gameQuiltMappings.size());
        });
    }

    private static String stripInfo(String version) {
        if (version.contains("+build.")) {
            return version.substring(0, version.lastIndexOf('+'));
        } else {
            //TODO legacy remove when no longer needed
            char verSep = version.contains("-") ? '-' : '.';
            return version.substring(0, version.lastIndexOf(verSep));
        }
    }

    public static CompletableFuture<Multimap<String, QuiltMappingsVersionV3>> getVersions(MavenRepository maven, Executor executor) {
        return CompletableFuture.supplyAsync(() -> maven.getMetadata(Constants.QUILT_MAVEN_GROUP, "quilt-mappings"), executor).thenApply(mavenMetadata -> {
            Multimap<String, QuiltMappingsVersionV3> gameQuiltMappings = MultimapBuilder.linkedHashKeys().hashSetValues().build();
            mavenMetadata.forEach(artifact -> {
                var mappingsVersion = artifact.version;
                var gameVersion = stripInfo(mappingsVersion);
                var separator = mappingsVersion.contains("+build.") ? "+build." : ".";
                var buildNumber = Integer.parseInt(mappingsVersion.substring(mappingsVersion.lastIndexOf(".") + 1));

                var fileInfo = artifact.fileInfo(executor).join();

                var version = new QuiltMappingsVersionV3(artifact.mavenId(), mappingsVersion, gameVersion, buildNumber, separator, gameVersion, fileInfo.fileSize(), fileInfo.formatHashesForJson());
                gameQuiltMappings.put(gameVersion, version);
            });

            return gameQuiltMappings;
        });
    }
}
