package org.quiltmc.meta.update.model.v3;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public record QuiltInstallerVersionV3(String maven, String version, String url, @SerializedName("file_size") long fileSize, Map<String, String> hashes) {
}
