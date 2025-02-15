package org.quiltmc.meta.update.util.maven;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * covers versions {@code 1}-{@code 2}
 *
 * <ul>
 *     <li>example version 1: <a href="https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-loader/0.16.0-beta.1/quilt-loader-0.16.0-beta.1.json">0.16.0-beta.1</a></li>
 *     <li>example version 1 extended (added `mainClass.serverLauncher`): <a href="https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-loader/0.16.0/quilt-loader-0.16.0.json">0.16.0</a></li>
 *     <li>example version 2 (added `min_java_version` and `libraries.development`): <a href="https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-loader/0.28.0-beta.8/quilt-loader-0.28.0-beta.8.json">0.28.0-beta.8</a></li>
 * </ul>
 */
public record QuiltLoaderMetadataJson(int version, Map<String, List<QuiltLoaderMetadataJson.Artifact>> libraries, @Nullable Map<String, String> mainClass, @SerializedName("min_java_version") @Nullable Integer minimumSupportedJavaVersion) {

    public record Artifact(String name, String url) {}
}
