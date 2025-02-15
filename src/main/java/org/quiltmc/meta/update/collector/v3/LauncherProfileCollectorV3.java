package org.quiltmc.meta.update.collector.v3;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.model.v3.LauncherProfileV3;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.upload.InputCollector;
import org.quiltmc.meta.update.util.hash.HashFunction;
import org.quiltmc.meta.update.util.http.ContentType;
import org.quiltmc.meta.update.util.maven.MavenRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class LauncherProfileCollectorV3 extends InputCollector {

    private final MavenRepository quiltMaven;
    private final MavenRepository fabricMaven;

    public LauncherProfileCollectorV3(MavenRepository quiltMaven, MavenRepository fabricMaven) {
        this.quiltMaven = quiltMaven;
        this.fabricMaven = fabricMaven;
    }

    @Override
    public String getName() {
        return "Launcher Profiles";
    }

    @Override
    public CompletableFuture<Void> gatherData(BiConsumer<String, FileInfo> output, Executor executor) {
        var time = Instant.now();

        return QuiltLauncherMetadataCollectorV3.getVersions(quiltMaven, fabricMaven, executor).thenAcceptAsync(gameLauncherVersions -> {
            gameLauncherVersions.forEach((gameVersion, launcherMetadata) -> {
                var hashed = new LauncherProfileV3.Library(launcherMetadata.hashed().maven(), quiltMaven.url().toString());
                var intermediary = new LauncherProfileV3.Library(launcherMetadata.intermediary().maven(), fabricMaven.url().toString());
                var loader = new LauncherProfileV3.Library(launcherMetadata.loader().maven(), quiltMaven.url().toString());

                for (var profileType : ProfileType.values()) {

                    List<LauncherProfileV3.Library> libraries = Stream.concat(profileType.getLibraryKeysToInclude().stream().flatMap(key -> launcherMetadata.launcherMeta().libraries().get(key).stream()).map(LauncherProfileV3.Library::fromLauncherMeta), Stream.of(hashed, intermediary, loader)).toList();

                    var mainClass = Optional.ofNullable(launcherMetadata.launcherMeta().mainClass()).map(map -> map.get(profileType.getName())).orElse(null);
                    var serverLauncherMainClass = mainClass != null ? launcherMetadata.launcherMeta().mainClass().get("serverLauncher") : null;

                    // need to compute the hash with constant timestamps, otherwise all profiles would be constantly flagged as needing updates
                    var profileForCache = profileType.create("quilt-loader-%s-%s".formatted(launcherMetadata.loader().version(), gameVersion), gameVersion, LauncherProfileV3.TYPE_RELEASE, mainClass, serverLauncherMainClass, Map.of("game", List.of()), libraries, Instant.EPOCH, Instant.EPOCH);
                    var hash = HashFunction.SHA1.createHash(Constants.GSON.toJson(profileForCache).getBytes(StandardCharsets.UTF_8));

                    var profile = profileType.create("quilt-loader-%s-%s".formatted(launcherMetadata.loader().version(), gameVersion), gameVersion, LauncherProfileV3.TYPE_RELEASE, mainClass, serverLauncherMainClass, Map.of("game", List.of()), libraries, time, time);
                    var profileBytes = Constants.GSON.toJson(profile).getBytes(StandardCharsets.UTF_8);

                    //TODO change path in v4: profiles/<mcVer>/<loaderVer>/client + profiles/<mcVer>/<loaderVer>/server
                    output.accept("v3/versions/loader/%s/%s/%s".formatted(gameVersion, launcherMetadata.loader().version(), profileType.getUrlPath()), FileInfo.withHashValue(profileBytes, ContentType.APPLICATION_JSON, hash.bytes()));
                }
            });

            LOGGER.info("Generated {} launch profiles", gameLauncherVersions.size() * ProfileType.values().length);
        }, executor);
    }

    private enum ProfileType {
        CLIENT("client", "profile/json", List.of("common", "client"), (id, inheritsFrom, type, mainClass, launcherMainClass, arguments, libraries, releaseTime, time) -> new LauncherProfileV3.Client(id, inheritsFrom, type, mainClass, arguments, libraries, releaseTime, time)),
        SERVER("server", "server/json", List.of("common", "server"), LauncherProfileV3.Server::new);

        private final String name;
        private final String urlPath;
        private final List<String> libraryKeysToInclude;
        private final ProfileFactory<?> profileFactory;

        ProfileType(String name, String urlPath, List<String> libraryKeysToInclude, ProfileFactory<?> profileFactory) {
            this.name = name;
            this.urlPath = urlPath;
            this.libraryKeysToInclude = libraryKeysToInclude;
            this.profileFactory = profileFactory;
        }

        @Override
        public String toString() {
            return name;
        }

        public String getName() {
            return name;
        }

        public String getUrlPath() {
            return urlPath;
        }

        public List<String> getLibraryKeysToInclude() {
            return libraryKeysToInclude;
        }

        public LauncherProfileV3 create(String id, @Nullable String inheritsFrom, String type, @Nullable String mainClass, @Nullable String serverLauncherMainClass, Map<String, List<String>> arguments, List<LauncherProfileV3.Library> libraries, Instant releaseTime, Instant time) {
            return this.profileFactory.create(id, inheritsFrom, type, mainClass, serverLauncherMainClass, arguments, libraries, releaseTime, time);
        }

        @FunctionalInterface
        public interface ProfileFactory<T extends LauncherProfileV3> {

            T create(String id, @Nullable String inheritsFrom, String type, @Nullable String mainClass, @Nullable String serverLauncherMainClass, Map<String, List<String>> arguments, List<LauncherProfileV3.Library> libraries, Instant releaseTime, Instant time);
        }
    }
}
