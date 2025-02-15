package org.quiltmc.meta.update.collector.v3;

import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.model.v3.QuiltInstallerVersionV3;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.upload.InputCollector;
import org.quiltmc.meta.update.util.maven.MavenRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public class QuiltInstallerVersionCollectorV3 extends InputCollector {

    private final MavenRepository quiltMaven;

    public QuiltInstallerVersionCollectorV3(MavenRepository quiltMaven) {
        this.quiltMaven = quiltMaven;
    }

    @Override
    public String getName() {
        return "Quilt Installer";
    }

    @Override
    public CompletableFuture<Void> gatherData(BiConsumer<String, FileInfo> output, Executor executor) {
        return CompletableFuture.supplyAsync(() -> getVersions(quiltMaven), executor).thenAcceptAsync(versions -> {
            output.accept("v3/versions/installer", FileInfo.jsonList(versions));

            LOGGER.info("Found {} installer versions", versions.size());
        }, executor);
    }

    public static List<QuiltInstallerVersionV3> getVersions(MavenRepository maven) {
        return maven.getMetadata(Constants.QUILT_MAVEN_GROUP, "quilt-installer").stream().map(artifact -> new QuiltInstallerVersionV3(artifact.mavenId(), artifact.version, artifact.url().toString())).toList();
    }
}
