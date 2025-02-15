package org.quiltmc.meta.update.model.v3;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.meta.update.util.maven.QuiltLoaderMetadataJson;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// TODO do we really need both 'time' and 'releaseTime'?
public interface LauncherProfileV3 {

    String TYPE_RELEASE = "release";

    String id();
    @Nullable String inheritsFrom();
    String type();
    @Nullable String mainClass();
    @Nullable String launcherMainClass();
    Map<String, List<String>> arguments();
    List<Library> libraries();
    Instant releaseTime();
    Instant time();

    record Server(String id, @Nullable String inheritsFrom, String type, @Nullable String mainClass, @Nullable String launcherMainClass, Map<String, List<String>> arguments, List<LauncherProfileV3.Library> libraries, Instant releaseTime, Instant time) implements LauncherProfileV3 {}
    record Client(String id, @Nullable String inheritsFrom, String type, @Nullable String mainClass, Map<String, List<String>> arguments, List<LauncherProfileV3.Library> libraries, Instant releaseTime, Instant time) implements LauncherProfileV3 {
        @Override
        public @Nullable String launcherMainClass() {
            return null;
        }
    }

    record Library(String name, String url) {

        public static Library fromLauncherMeta(QuiltLoaderMetadataJson.Artifact artifact) {
            return new Library(artifact.name(), artifact.url());
        }
    }
}
