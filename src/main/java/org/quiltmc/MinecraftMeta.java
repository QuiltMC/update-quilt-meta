package org.quiltmc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class MinecraftMeta {
    private static final URL MANIFEST;

    static {
        URL url = null;

        try {
            url = new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load Minecraft version manifest");
        }

        MANIFEST = url;
    }

    private MinecraftMeta() {
    }

    @SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
    private List<Version> versions;

    public static JsonArray get(MavenRepository.ArtifactMetadata hashedMojmap, Gson gson) {
        JsonArray versions = new JsonArray();

        InputStreamReader reader;
        try {
            reader = new InputStreamReader(MANIFEST.openStream());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load Minecraft version manifest");
        }
        MinecraftMeta meta = gson.fromJson(reader, MinecraftMeta.class);

        for (Version version : meta.versions) {
            if (hashedMojmap.contains(version.id)) {
                JsonObject object = new JsonObject();

                object.addProperty("version", version.id);
                object.addProperty("stable", version.type.equals("release"));

                versions.add(object);
            }
        }

        return versions;
    }

    private static class Version {
        String id;
        String type;
    }
}
