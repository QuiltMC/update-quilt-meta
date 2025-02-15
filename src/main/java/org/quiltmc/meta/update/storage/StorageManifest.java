package org.quiltmc.meta.update.storage;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.util.json.StorageManifestTypeAdapter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

@JsonAdapter(StorageManifestTypeAdapter.class)
public record StorageManifest(@SerializedName(KEY_VERSION) int manifestVersion, @SerializedName(KEY_FILES) Map<String, Entry> files, @SerializedName(KEY_CREATED_AT) Instant createdAt) implements BiPredicate<String, String> {

    public static final int MANIFEST_VERSION = 2;
    public static final String KEY_CREATED_AT = "created_at";
    public static final String KEY_FILES = "files";
    public static final String KEY_VERSION = "manifest_version";

    public Optional<Entry> entry(String path) {
        return Optional.ofNullable(files.get(path));
    }

    @Override
    public boolean test(String path, String sha1) {
        return entry(path).map(e -> e.sha1().equals(sha1)).orElse(false);
    }

    public static StorageManifest ofFiles(Map<String, FileInfo> files) {
        return new StorageManifest(MANIFEST_VERSION, files.entrySet().stream().map(e -> new StorageManifest.Entry(e.getKey(), e.getValue().manifestSha1())).collect(Collectors.toUnmodifiableMap(StorageManifest.Entry::path, Function.identity())), Instant.now());
    }

    public record Entry(String path, String sha1) {}
}
