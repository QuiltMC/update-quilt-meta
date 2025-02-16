package org.quiltmc.meta.update.model.v3;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

// TODO remove 'hashed' field in v4? it seems superfluous
public record QuiltMappingsVersionV3(String maven, String version, String gameVersion, int build, String separator, String hashed, @SerializedName("file_size") long fileSize, Map<String, String> hashes) {
}
