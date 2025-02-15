package org.quiltmc.meta.update.util.mojang;

import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

public record MinecraftMeta(List<MinecraftVersion> versions) {

    private static final URI VERSION_MANIFEST_V2_URL = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

    public static MinecraftMeta get(Gson gson) {
        try (var reader = new InputStreamReader(new BufferedInputStream(VERSION_MANIFEST_V2_URL.toURL().openStream()))) {
            return gson.fromJson(reader, MinecraftMeta.class);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Game Meta URL was invalid", e);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read game version manifest from Piston Meta", e);
        }
    }

    public record MinecraftVersion(String id, String type) {
    }
}
