package org.quiltmc.meta.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.quiltmc.meta.update.util.json.MojangInstantTypeAdapter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class Constants {
    // General
    public static final String TOOL_VERSION = Objects.requireNonNullElse(Constants.class.getPackage().getImplementationVersion(), "UNKNOWN");
    public static final String USER_AGENT = "update-quilt-meta/" + TOOL_VERSION;

    public static final String QUILT_MAVEN_GROUP = "org.quiltmc";

    // Maven
    public static final String QUILT_MAVEN_URL = "https://maven.quiltmc.org/repository/release";
    public static final String FABRIC_MAVEN_URL = "https://maven.fabricmc.net";

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Instant.class, new MojangInstantTypeAdapter()).create();

    public static final DateTimeFormatter MOJANG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx").withZone(ZoneOffset.UTC);
    public static final String MANIFEST_PATH = "_manifest.json.gz";

    private Constants() {}
}
