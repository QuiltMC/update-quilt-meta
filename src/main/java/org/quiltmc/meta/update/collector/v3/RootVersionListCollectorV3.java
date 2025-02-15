package org.quiltmc.meta.update.collector.v3;

import com.google.common.collect.Multimap;
import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.model.v3.*;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.upload.InputCollector;
import org.quiltmc.meta.update.util.maven.MavenRepository;
import org.quiltmc.meta.update.util.mojang.MinecraftMeta;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

// TODO remove in v4
public class RootVersionListCollectorV3 extends InputCollector {

    private final MavenRepository quiltMaven;

    public RootVersionListCollectorV3(MavenRepository quiltMaven) {
        this.quiltMaven = quiltMaven;
    }

    @Override
    public String getName() {
        return "Root Version List";
    }

    @Override
    public CompletableFuture<Void> gatherData(BiConsumer<String, FileInfo> output, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            Map<String, HashedMojmapVersionV3> hashed = HashedMojmapAndGameVersionCollectorV3.getVersions(quiltMaven);
            List<GameVersionV3> game = HashedMojmapAndGameVersionCollectorV3.getGameVersions(MinecraftMeta.get(Constants.GSON), hashed::containsKey);
            Multimap<String, QuiltMappingsVersionV3> mappings = QuiltMappingsCollectorV3.getVersions(quiltMaven);
            List<QuiltLoaderVersionV3> loader = QuiltLoaderVersionCollectorV3.getVersions(quiltMaven);
            List<QuiltInstallerVersionV3> installer = QuiltInstallerVersionCollectorV3.getVersions(quiltMaven);

            var versionList = new VersionListV3(game, mappings.values(), hashed.values(), loader, installer);
            output.accept("v3/versions", FileInfo.json(versionList));

            LOGGER.info("Generated root version list");
        });
    }
}
