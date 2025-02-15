package org.quiltmc.meta.update.collector.v3;

import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.model.v3.QuiltLoaderVersionV3;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.upload.InputCollector;
import org.quiltmc.meta.update.util.maven.MavenRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public class QuiltLoaderVersionCollectorV3 extends InputCollector {

    private final MavenRepository quiltMaven;

    public QuiltLoaderVersionCollectorV3(MavenRepository quiltMaven) {
        this.quiltMaven = quiltMaven;
    }

    @Override
    public String getName() {
        return "Quilt Loader";
    }

    @Override
    public CompletableFuture<Void> gatherData(BiConsumer<String, FileInfo> output, Executor executor) {
        return CompletableFuture.supplyAsync(() -> getVersions(quiltMaven), executor).thenAccept(versions -> {
            output.accept("v3/versions/loader", FileInfo.jsonList(versions));

            LOGGER.info("Found {} quilt loader versions", versions.size());
        });
    }

    public static List<QuiltLoaderVersionV3> getVersions(MavenRepository maven) {
        return maven.getMetadata(Constants.QUILT_MAVEN_GROUP, "quilt-loader").stream().map(artifact -> {
            var loaderVersion = artifact.version;
            var separator = loaderVersion.contains("+build.") ? "+build." : ".";
            var buildNumber = Integer.parseInt(loaderVersion.substring(loaderVersion.lastIndexOf(".") + 1));

            return new QuiltLoaderVersionV3(artifact.mavenId(), loaderVersion, buildNumber, separator);
        }).toList();
    }
}
