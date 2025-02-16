package org.quiltmc.meta.update.util.maven;

import com.google.gson.JsonObject;
import org.quiltmc.meta.update.util.hash.Hash;
import org.quiltmc.meta.update.util.hash.HashFunction;

import java.util.*;

public record GradleModuleMetadata(String formatVersion, Component component, JsonObject createdBy, List<Variant> variants) {

    String gradleVersion() {
        return createdBy().getAsJsonObject("gradle").get("version").getAsString();
    }

    Optional<Variant.File> mainFileInfo() {
        var expectedJarFileName = "%s-%s.jar".formatted(component().module(), component().version());
        return variants().stream().flatMap(variant -> variant.files().stream()).filter(file -> file.url().equals(expectedJarFileName)).findAny();
    }

    public record Component(String group, String module, String version, Map<String, Object> attributes) {}
    public record Variant(String name, Map<String, Object> attributes, List<File> files) {
        public record File(String name, String url, long size, String sha1, String sha256, String sha512) {
            Map<HashFunction, Hash> hashes() {
                Map<HashFunction, Hash> map = new EnumMap<>(HashFunction.class);
                if(sha1() != null) {
                    map.put(HashFunction.SHA1, Hash.parseHexValue(HashFunction.SHA1, sha1()));
                }
                if(sha256() != null) {
                    map.put(HashFunction.SHA256, Hash.parseHexValue(HashFunction.SHA256, sha256()));
                }
                if(sha512() != null) {
                    map.put(HashFunction.SHA512, Hash.parseHexValue(HashFunction.SHA512, sha512()));
                }
                return Collections.unmodifiableMap(map);
            }
        }
    }
}
