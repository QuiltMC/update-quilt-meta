package org.quiltmc;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

public final class Constants {
    // General
    static final String TOOL_VERSION = Objects.requireNonNullElse(System.getenv("GIT_SHA"), "development").subSequence(0, 7).toString();
    static final String BASE_URL = "https://meta.quiltmc.org/";
    static final String USER_AGENT = "update-quilt-meta/" + TOOL_VERSION;

    static final String GROUP = "org.quiltmc";

    // Maven
    static final String BASE_MAVEN_URL = "https://maven.quiltmc.org/repository/release/";
    static final String FABRIC_MAVEN_URL = "https://maven.fabricmc.net/";

    static final String LOADER_ARTIFACT = "quilt-loader";
    static final String INSTALLER_ARTIFACT = "quilt-installer";

    // Backblaze
    static final String B2_BUCKET = "meta-quiltmc-org";
    static final String B2_APP_KEY_ID = System.getenv("B2_APP_KEY_ID");
    static final String B2_APP_KEY = System.getenv("B2_APP_KEY");

    // Cloudflare
    static final String CF_ZONE_ID = "73c99d057aa12563eb4cad4ef14f0796";
    static final String CF_KEY = System.getenv("CF_KEY");
    static final URL CF_PURGE_FILES_ENDPOINT;

    static {
        try {
            CF_PURGE_FILES_ENDPOINT = new URL("https://api.cloudflare.com/client/v4/zones/" + CF_ZONE_ID + "/purge_cache");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    static final int CF_PURGE_LIMIT_PER_MINUTE = 1000;
    static final int CF_PURGE_LIMIT_PER_REQUEST = 30;

    // Internal
    static final String MANIFEST_FILE = "_manifest_01.gz";

    private Constants() {}
}
