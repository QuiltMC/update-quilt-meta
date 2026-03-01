package org.quiltmc.meta.update.model.v3;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public record QuiltLoaderVersionV3(String maven, String version, int build, String separator, @SerializedName("file_size") long fileSize, Map<String, String> hashes) {
}
