package org.quiltmc.meta.update.model.v3;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public record HashedMojmapVersionV3(String maven, String version, @SerializedName("file_size") long fileSize, Map<String, String> hashes) {
}
