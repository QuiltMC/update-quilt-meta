package org.quiltmc;

import java.util.Objects;

public final class Constants {
    // General
    static final String TOOL_VERSION = Objects.requireNonNullElse(System.getenv("GIT_SHA"), "development").subSequence(0, 7).toString();
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

    // Internal
    static final String MANIFEST_FILE = "_manifest_01.gz";

    private Constants() {}
}
