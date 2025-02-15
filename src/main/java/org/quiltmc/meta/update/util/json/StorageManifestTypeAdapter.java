package org.quiltmc.meta.update.util.json;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.quiltmc.meta.update.storage.StorageManifest;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StorageManifestTypeAdapter implements JsonSerializer<StorageManifest>, JsonDeserializer<StorageManifest> {

    private static final TypeToken<Collection<StorageManifest.Entry>> FILES_TYPE_TOKEN = new TypeToken<>() {
    };

    @Override
    public StorageManifest deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = context.deserialize(json, JsonObject.class);
        int manifestVersion = Objects.requireNonNullElse(context.deserialize(obj.get(StorageManifest.KEY_VERSION), Integer.class), 0);

        if(manifestVersion != StorageManifest.MANIFEST_VERSION) {
            throw new JsonParseException("Unexpected manifest version: " + manifestVersion);
        }

        Instant creationDate = Objects.requireNonNullElse(context.deserialize(obj.get(StorageManifest.KEY_CREATED_AT), Instant.class), Instant.EPOCH);
        Collection<StorageManifest.Entry> entries = Objects.requireNonNullElseGet(context.deserialize(obj.get(StorageManifest.KEY_FILES), FILES_TYPE_TOKEN.getType()), List::of);
        return new StorageManifest(manifestVersion, entries.stream().collect(Collectors.toUnmodifiableMap(StorageManifest.Entry::path, Function.identity())), creationDate);
    }

    @Override
    public JsonElement serialize(StorageManifest src, Type typeOfSrc, JsonSerializationContext context) {
        var obj = new JsonObject();
        obj.addProperty(StorageManifest.KEY_VERSION, src.manifestVersion());
        obj.add(StorageManifest.KEY_FILES, context.serialize(src.files().values(), FILES_TYPE_TOKEN.getType()));
        obj.add(StorageManifest.KEY_CREATED_AT, context.serialize(src.createdAt()));
        return obj;
    }
}
