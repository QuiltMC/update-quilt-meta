package org.quiltmc;
import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import com.backblaze.b2.client.contentHandlers.B2ContentMemoryWriter;
import com.backblaze.b2.client.contentSources.B2ByteArrayContentSource;
import com.backblaze.b2.client.contentSources.B2ContentSource;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.B2FileVersion;
import com.backblaze.b2.client.structures.B2UploadFileRequest;
import com.google.gson.*;

import java.io.*;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Main generator class for the meta.
 */
public class Main {
    private static final DateFormat ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private B2StorageClient client;
    private String bucketId;
    private final MavenRepository maven = new MavenRepository(Constants.BASE_MAVEN_URL);
    private final MavenRepository fabric = new MavenRepository(Constants.FABRIC_MAVEN_URL);
    private final Map<String, JsonArray> arrays = new ConcurrentHashMap<>();
    private final Map<String, JsonElement> launcherMetaData = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> gameIntermediaries = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> gameHashedMojmap = new ConcurrentHashMap<>();
    private final Deque<MavenRepository.ArtifactMetadata.Artifact> loaderVersions = new ConcurrentLinkedDeque<>();
    private final Map<String, FileUpload> files = new ConcurrentHashMap<>();
    private final Map<String, String> previousHashes = new ConcurrentHashMap<>();
    private final Map<String, String> newHashes = new ConcurrentHashMap<>();
    private final Set<String> seenFiles = new ConcurrentSkipListSet<>();
    private Integer skippedFiles = 0;

    public static void main(String[] args) {
        System.out.println("[INFO] Running build " + Constants.TOOL_VERSION);

        if (Constants.B2_APP_KEY_ID.isEmpty() || Constants.B2_APP_KEY.isEmpty() || Constants.CF_KEY.isEmpty()) {
            if (!Constants.TESTING) {
                System.err.println("[ERROR] B2_APP_KEY_ID, B2_APP_KEY and CF_KEY must be set in the environment. Please set Constants#TESTING to true or refer to the documentation to set up in production.");
                System.exit(1);
            }
        }

        Boolean success = new Main().build();

        if (!success) {
            System.out.println("[ERROR] Failed to build meta. Please refer to the logs and report this to the Infrastructure team.");
            System.exit(1);
        }
        System.exit(0);
    }

    public Boolean build() {
        if (!this.setupB2()) {
            return false;
        }

        deleteOldTestingBuild();

        try {
            this.populatePreviousHashes();

            System.out.println("[INFO] Gathering data..");

            ExecutorService executor = Executors.newCachedThreadPool();

            CompletableFuture.allOf(
                    this.populateHashedMojmapAndGame(executor),
                    this.populateIntermediaryAndGame(executor),
                    CompletableFuture.runAsync(this::populateQuiltMappings, executor),
                    CompletableFuture.runAsync(this::populateInstaller, executor),
                    CompletableFuture.runAsync(this.populateLoader(executor), executor)
            ).join();

            System.out.println("[INFO] Gathering loader data..");

            this.populateLoaderVersions();
            this.populateProfiles();

            JsonObject versions = new JsonObject();

            versions.add("game", this.arrays.get("game"));
            versions.add("mappings", this.arrays.get("mappings"));
            versions.add("hashed", this.arrays.get("hashed"));
            versions.add("loader", this.arrays.get("loader"));
            versions.add("installer", this.arrays.get("installer"));

            upload("v3/versions", this.gson.toJson(versions));
            upload("v3/versions/game", this.gson.toJson(this.arrays.get("game")));

            // Add static files
            ClassLoader classLoader = getClass().getClassLoader();
            upload("index.html", classLoader.getResourceAsStream("static/index.html").readAllBytes(), "text/html");
            upload("openapi.yaml", classLoader.getResourceAsStream("static/openapi.yaml").readAllBytes(), "text/yaml");
            upload("favicon.ico", classLoader.getResourceAsStream("static/favicon.ico").readAllBytes(), "image/x-icon");
            upload("dark-swagger.css", classLoader.getResourceAsStream("static/dark-swagger.css").readAllBytes(), "text/css");
            upload("swagger.css", classLoader.getResourceAsStream("static/swagger.css").readAllBytes(), "text/css");
            upload("swagger-ui-bundle.js", classLoader.getResourceAsStream("static/swagger-ui-bundle.js").readAllBytes(), "text/javascript");

            System.out.println("[INFO] Syncing files..");
            this.doUpload();

            System.out.println("[INFO] Purging cache..");
            this.purgeCache();

            System.out.println("[INFO] Updating manifest..");
            this.updateManifest();

            System.out.println("[INFO] Content synced");

            // Print a changed file collapsible (for GitHub Actions)
            System.out.println("::group::Changed file(s) (" + this.files.size() + ")");
            System.out.println(this.files.keySet().stream().sorted().collect(Collectors.joining("\n")));
            System.out.println("::endgroup::");

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void deleteOldTestingBuild() {
        if (Constants.TESTING) {
            deleteDirectory(new File("v3"));
        }
    }

    private void deleteDirectory(File toDelete) {
        File[] allContents = toDelete.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }

        toDelete.delete();
    }

    /**
     * {@return whether B2 was successfully set up (or ignored for testing)}
     */
    private boolean setupB2() {
        if (!Constants.TESTING) {
            this.client = B2StorageClientFactory
                    .createDefaultFactory()
                    .create(Constants.B2_APP_KEY_ID, Constants.B2_APP_KEY, Constants.USER_AGENT);
            try {
                this.bucketId = this.client.getBucketOrNullByName(Constants.B2_BUCKET).getBucketId();
                if (this.bucketId == null) {
                    throw new IOException("Failed to find bucket");
                }
            } catch (B2Exception | IOException e) {
                e.printStackTrace();
                System.err.println("[ERROR] Failed to get bucket ID for bucket " + Constants.B2_BUCKET);
                return false;
            }
        }

        return true;
    }

    private void populateQuiltMappings() {
        Collection<String> gameVersions = new LinkedHashSet<>();
        JsonArray qm = new JsonArray();
        Map<String, JsonArray> qmVersions = new HashMap<>();

        try {
            for (MavenRepository.ArtifactMetadata.Artifact artifact : this.maven.getMetadata(Constants.GROUP, "quilt-mappings")) {
                JsonObject object = new JsonObject();

                String gameVersion = stripInfo(artifact.version);
                object.addProperty("gameVersion", gameVersion);
                object.addProperty("separator", artifact.version.contains("+build.") ? "+build." : ".");
                object.addProperty("build", Integer.parseInt(artifact.version.substring(artifact.version.lastIndexOf(".") + 1)));
                object.addProperty("maven", artifact.mavenId());
                object.addProperty("version", artifact.version);

                object.addProperty("hashed", gameVersion);

                qm.add(object);
                gameVersions.add(gameVersion);
                qmVersions.computeIfAbsent(gameVersion, v -> new JsonArray()).add(object);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to get quilt mappings");
        }

        System.out.println("[INFO] Found " + qm.size() + " quilt mappings");

        JsonArray array = new JsonArray();
        gameVersions.forEach(array::add);

        this.arrays.put("mappings", qm);
        this.upload("v3/versions/game/quilt-mappings", this.gson.toJson(array));
        this.upload("v3/versions/quilt-mappings", this.gson.toJson(qm));

        for (Map.Entry<String, JsonArray> entry : qmVersions.entrySet()) {
            this.upload("v3/versions/quilt-mappings/" + entry.getKey(), this.gson.toJson(entry.getValue()));
        }
    }

    private void populateInstaller() {
        JsonArray installer = new JsonArray();

        try {
            for (MavenRepository.ArtifactMetadata.Artifact artifact : this.maven.getMetadata(Constants.GROUP, Constants.INSTALLER_ARTIFACT)) {
                JsonObject object = new JsonObject();

                object.addProperty("url", artifact.url());
                object.addProperty("maven", artifact.mavenId());
                object.addProperty("version", artifact.version);

                installer.add(object);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to get installer");
        }

        System.out.println("[INFO] Found " + installer.size() + " installers");

        this.arrays.put("installer", installer);
        this.upload("v3/versions/installer", this.gson.toJson(installer));
    }

    private Runnable populateLoader(ExecutorService executor) {
        return () -> {
            CompletableFuture.runAsync(this::populateLoader, executor).join();

            @SuppressWarnings("unchecked")
            CompletableFuture<Void>[] futures = (CompletableFuture<Void>[]) Array.newInstance(CompletableFuture.class, this.loaderVersions.size());
            int i = 0;

            System.out.println("[INFO] Found " + this.loaderVersions.size() + " loaders");

            for (MavenRepository.ArtifactMetadata.Artifact artifact : this.loaderVersions) {
                futures[i++] = CompletableFuture.runAsync(() -> {
                    try {
                        URL url = new URL(artifact.url().replace(".jar", ".json"));
                        JsonElement launcherMeta = JsonParser.parseReader(new InputStreamReader(url.openStream()));
                        this.launcherMetaData.put(artifact.mavenId(), launcherMeta);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException("Failed to get loader meta for " + artifact.mavenId());
                    }
                }, executor);
            }

            CompletableFuture.allOf(futures).join();
        };
    }

    private void populateLoader() {
        JsonArray loader = new JsonArray();

        try {
            for (MavenRepository.ArtifactMetadata.Artifact artifact : this.maven.getMetadata(Constants.GROUP, Constants.LOADER_ARTIFACT)) {
                JsonObject object = new JsonObject();

                object.addProperty("separator", artifact.version.contains("+build.") ? "+build." : ".");
                object.addProperty("build", Integer.parseInt(artifact.version.substring(artifact.version.lastIndexOf(".") + 1)));
                object.addProperty("maven", artifact.mavenId());

                String version = artifact.version.contains("+build.")
                        ? artifact.version.substring(0, artifact.version.lastIndexOf('+'))
                        : artifact.version;

                object.addProperty("version", version);

                loader.add(object);

                this.loaderVersions.add(artifact);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to get loader");
        }

        this.arrays.put("loader", loader);
        this.upload("v3/versions/loader", this.gson.toJson(loader));
    }

    private CompletableFuture<Void> populateIntermediaryAndGame(Executor executor) {
        return CompletableFuture.runAsync(() -> {
            Collection<String> gameIntermediary = new LinkedHashSet<>();
            JsonArray intermediary = new JsonArray();
            Map<String, JsonArray> intermediaryVersions = new HashMap<>();

            try {
                MavenRepository.ArtifactMetadata intermediaries = this.fabric.getMetadata("net.fabricmc", "intermediary");

                for (MavenRepository.ArtifactMetadata.Artifact artifact : intermediaries) {
                    JsonObject object = new JsonObject();

                    object.addProperty("maven", artifact.mavenId());
                    object.addProperty("version", artifact.version);

                    intermediary.add(object);
                    gameIntermediary.add(artifact.version);
                    this.gameIntermediaries.putIfAbsent(artifact.version, object);
                    intermediaryVersions.computeIfAbsent(artifact.version, v -> new JsonArray()).add(object);
                }

                System.out.println("[INFO] Found " + intermediaryVersions.size() + " intermediary mappings");

                JsonArray array = new JsonArray();
                gameIntermediary.forEach(array::add);

                this.arrays.put("intermediary", intermediary);
                this.upload("v3/versions/game/intermediary", this.gson.toJson(array));
                this.upload("v3/versions/intermediary", this.gson.toJson(intermediary));

                for (Map.Entry<String, JsonArray> entry : intermediaryVersions.entrySet()) {
                    this.upload("v3/versions/intermediary/" + entry.getKey(), this.gson.toJson(entry.getValue()));
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Failed to get intermediary");
            }
        });
    }

    private CompletableFuture<Void> populateHashedMojmapAndGame(Executor executor) {
        Collection<String> gameHashed = new LinkedHashSet<>();
        JsonArray hashed = new JsonArray();
        Map<String, JsonArray> hashedVersions = new HashMap<>();

        try {
            MavenRepository.ArtifactMetadata hashedMojmap = this.maven.getMetadata(Constants.GROUP, "hashed");

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                JsonArray meta = MinecraftMeta.get(hashedMojmap, gson);
                this.arrays.put("game", meta);
                this.upload("v3/versions/game", this.gson.toJson(meta));
            }, executor);

            for (MavenRepository.ArtifactMetadata.Artifact artifact : hashedMojmap) {
                JsonObject object = new JsonObject();

                object.addProperty("maven", artifact.mavenId());
                object.addProperty("version", artifact.version);

                hashed.add(object);
                gameHashed.add(artifact.version);
                this.gameHashedMojmap.putIfAbsent(artifact.version, object);
                hashedVersions.computeIfAbsent(artifact.version, v -> new JsonArray()).add(object);
            }

            System.out.println("[INFO] Found " + gameHashed.size() + " game versions");

            JsonArray array = new JsonArray();
            gameHashed.forEach(array::add);

            this.arrays.put("hashed", hashed);
            this.upload("v3/versions/game/hashed", this.gson.toJson(array));
            this.upload("v3/versions/hashed", this.gson.toJson(hashed));

            for (Map.Entry<String, JsonArray> entry : hashedVersions.entrySet()) {
                this.upload("v3/versions/hashed/" + entry.getKey(), this.gson.toJson(entry.getValue()));
            }

            return future;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to get hashed mojmap");
        }
    }

    private void populateLoaderVersions() {
        for (JsonElement gameVersionElement : this.arrays.get("game")) {
            String gameVersion = gameVersionElement.getAsJsonObject().get("version").getAsString();
            JsonArray gameLoaderVersion = new JsonArray();

            for (JsonElement loaderVersionElement : this.arrays.get("loader")) {
                String loaderVersion = loaderVersionElement.getAsJsonObject().get("version").getAsString();

                JsonObject object = new JsonObject();

                object.add("loader", loaderVersionElement);
                object.add("hashed", this.gameHashedMojmap.get(gameVersion));
                object.add("intermediary", this.gameIntermediaries.get(gameVersion));
                object.add("launcherMeta", this.launcherMetaData.get(
                        loaderVersionElement.getAsJsonObject().get("maven").getAsString()
                ));

                gameLoaderVersion.add(object);

                this.upload(String.format("v3/versions/loader/%s/%s", gameVersion, loaderVersion), this.gson.toJson(object));
            }

            this.upload(String.format("v3/versions/loader/%s", gameVersion), this.gson.toJson(gameLoaderVersion));
        }

        System.out.println("[INFO] Generated " + this.arrays.get("game").size() * this.arrays.get("loader").size() + " loader versions");
    }

    private void populateProfiles() {
        String currentTime = ISO_8601.format(new Date());

        for (Side side : Side.values()) {
            for (JsonElement gameVersionElement : this.arrays.get("game")) {
                String gameVersion = gameVersionElement.getAsJsonObject().get("version").getAsString();

                for (JsonElement loaderVersionElement : this.arrays.get("loader")) {
                    String loaderVersion = loaderVersionElement.getAsJsonObject().get("version").getAsString();

                    JsonObject hashed = this.gameHashedMojmap.get(gameVersion);
                    JsonObject intermediary = this.gameIntermediaries.get(gameVersion);

                    JsonObject launcherMeta = this.launcherMetaData.get(
                            loaderVersionElement.getAsJsonObject().get("maven").getAsString()
                    ).getAsJsonObject();

                    JsonArray libraries = new JsonArray();

                    libraries.addAll(launcherMeta.get("libraries").getAsJsonObject().get("common").getAsJsonArray());
                    libraries.add(getLibrary(hashed.get("maven").getAsString(), this.maven.url));
                    libraries.add(getLibrary(intermediary.get("maven").getAsString(), this.fabric.url));
                    libraries.add(getLibrary(loaderVersionElement.getAsJsonObject().get("maven").getAsString(), this.maven.url));

                    if (launcherMeta.get("libraries").getAsJsonObject().has(side.side)) {
                        libraries.addAll(launcherMeta.get("libraries").getAsJsonObject().get(side.side).getAsJsonArray());
                    }

                    JsonObject arguments = new JsonObject();
                    arguments.add("game", new JsonArray());

                    JsonObject object = new JsonObject();

                    object.addProperty("id", String.format("quilt-loader-%s-%s", loaderVersion, gameVersion));
                    object.addProperty("inheritsFrom", gameVersion);
                    object.addProperty("type", "release");

                    if (launcherMeta.get("mainClass").isJsonObject()) {
                        object.addProperty("mainClass", launcherMeta.get("mainClass").getAsJsonObject().get(side.side).getAsString());
                    }

                    if (side == Side.SERVER && launcherMeta.has("mainClass") && launcherMeta.get("mainClass").getAsJsonObject().has("serverLauncher")) {
                        // Add the server launch main class
                        object.addProperty("launcherMainClass", launcherMeta.get("mainClass").getAsJsonObject().get("serverLauncher").getAsString());
                    }

                    object.add("arguments", arguments);
                    object.add("libraries", libraries);

                    String cacheSnapshot = this.gson.toJson(object);

                    // Non-deterministic fields
                    object.addProperty("releaseTime", currentTime);
                    object.addProperty("time", currentTime);

                    this.uploadWithCacheSnapshot(String.format("v3/versions/loader/%s/%s/%s/json", gameVersion, loaderVersion, side.type), this.gson.toJson(object), cacheSnapshot);
                }
            }
        }

        System.out.println("[INFO] Generated " + this.arrays.get("game").size() * this.arrays.get("loader").size() + " loader profiles");
    }

    private void upload(String fileName, String fileContents) {
        this.upload(fileName, fileContents.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private void upload(String fileName, byte[] fileContents, String contentType) {
        this.uploadWithCacheSnapshot(fileName, fileContents, contentType, fileContents);
    }

    private void uploadWithCacheSnapshot(String fileName, String fileContents, String cacheSnapshot) {
        this.uploadWithCacheSnapshot(fileName, fileContents.getBytes(StandardCharsets.UTF_8), "application/json", cacheSnapshot.getBytes(StandardCharsets.UTF_8));
    }

    private void uploadWithCacheSnapshot(String fileName, byte[] fileContents, String contentType, byte[] cacheSnapshot) {
        this.seenFiles.add(fileName);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to get SHA-1 digest");
        }
        byte[] hash = digest.digest(cacheSnapshot);
        String hashString = Base64.getUrlEncoder().encodeToString(hash);

        newHashes.put(fileName, hashString);

        if (this.previousHashes.getOrDefault(fileName, "").equals(hashString)) {
            this.skippedFiles++;
            return;
        }
        this.files.put(fileName, new FileUpload(fileContents, contentType));
    }

    private void doUpload() {
        ExecutorService executor = Executors.newFixedThreadPool(50);

        System.out.println("[INFO] Uploading " + this.files.size() + " file(s) (skipping " + this.skippedFiles + ")");

        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = (CompletableFuture<Void>[]) Array.newInstance(CompletableFuture.class, this.files.size());
        int i = 0;

        for (String filePath : this.files.keySet()) {
            futures[i++] = CompletableFuture.runAsync(() -> {
                FileUpload file = this.files.get(filePath);

                if (!Constants.TESTING) {
                    B2ContentSource source = B2ByteArrayContentSource.build(file.content());

                    B2UploadFileRequest.Builder builder = B2UploadFileRequest
                            .builder(this.bucketId, filePath, file.contentType(), source);

                    try {
                        this.client.uploadSmallFile(builder.build());
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException("Failed to upload " + file);
                    }
                } else {
                    try {
                        Path path = Path.of(filePath + ".json");
                        path.resolve("..").toFile().mkdirs();
                        path.toFile().createNewFile();
                        Files.write(path, file.content());
                    } catch (IOException e) {
						throw new RuntimeException("Failed to write file for testing dump: " + filePath, e);
					}
				}
            }, executor);
        }
        CompletableFuture.allOf(futures).join();

        this.deleteOldFiles(executor);
    }

    private void deleteOldFiles(ExecutorService executor) {
        if (!Constants.TESTING) {
            Set<String> oldFiles = new HashSet<>(this.previousHashes.keySet());
            oldFiles.removeAll(this.seenFiles);

            System.out.println("[INFO] Deleting " + oldFiles.size() + " file(s)");

            @SuppressWarnings("unchecked")
            CompletableFuture<Void>[] deleteFutures = (CompletableFuture<Void>[]) Array.newInstance(CompletableFuture.class, oldFiles.size());
            int i = 0;

            for (String filePath : oldFiles) {
                deleteFutures[i++] = CompletableFuture.runAsync(() -> {
                    try {
                        B2FileVersion version = this.client.getFileInfoByName(Constants.B2_BUCKET, filePath);
                        this.client.deleteFileVersion(version.getFileName(), version.getFileId());
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException("Failed to delete " + filePath);
                    }
                }, executor);
            }
            CompletableFuture.allOf(deleteFutures).join();
        }
    }

    private void purgeCache() {
        if (Constants.TESTING) {
            return;
        }

        List<String> urls = new ArrayList<>(this.files.keySet()).stream().map(url -> Constants.BASE_URL + url).toList();

        int requestsRequired = urls.size() / Constants.CF_PURGE_LIMIT_PER_REQUEST + 1;
        int requestsRemainingPerMinute = Constants.CF_PURGE_LIMIT_PER_MINUTE;
        long bucketStartTime = System.currentTimeMillis();
        System.out.println("[INFO] Purging " + this.files.size() + " url (eta. " + urls.size() / Constants.CF_PURGE_LIMIT_PER_MINUTE + " min(s))");

        for (int i = 1; i <= requestsRequired; i++) {
            try {
                List<String> batch = urls.subList((i - 1) * Constants.CF_PURGE_LIMIT_PER_REQUEST, Math.min(i * Constants.CF_PURGE_LIMIT_PER_REQUEST, urls.size()));
                requestsRemainingPerMinute -= batch.size();

                JsonObject body = new JsonObject();
                body.add("files", this.gson.toJsonTree(batch));

                HttpURLConnection connection = (HttpURLConnection) Constants.CF_PURGE_FILES_ENDPOINT.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + Constants.CF_KEY);

                connection.setDoOutput(true);
                try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                    this.gson.toJson(body, writer);
                }

                // Check the status code
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    String response = new BufferedReader(new InputStreamReader(connection.getErrorStream())).lines().collect(Collectors.joining("\n"));
                    throw new RuntimeException("Failed to purge batch " + i + " (status code " + responseCode + "): " + response);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Failed to purge batch " + i);
            }

            if (requestsRemainingPerMinute < Constants.CF_PURGE_LIMIT_PER_REQUEST) {
                try {
                    Thread.sleep(60_000 - (System.currentTimeMillis() - bucketStartTime));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                requestsRemainingPerMinute = Constants.CF_PURGE_LIMIT_PER_MINUTE;
                bucketStartTime = System.currentTimeMillis();
            }
        }
    }

    /** Gather hashes from the manifest file currently in meta. **/
    private void populatePreviousHashes() {
        if (this.client == null && Constants.TESTING) {
            System.out.println("[INFO] Re-uploading all files for testing.");
            return;
        }

        try {
            B2ContentMemoryWriter writer = B2ContentMemoryWriter.build();
            this.client.downloadByName(Constants.B2_BUCKET, Constants.MANIFEST_FILE, writer);

            GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(writer.getBytes()));
            Scanner scanner = new Scanner(gzip);
            scanner.useDelimiter(";");

            while (scanner.hasNext()) {
                String line = scanner.next();
                String[] split = line.split(":");

                if (split.length == 2) {
                    previousHashes.put(split[0], split[1]);
                } else {
                    System.out.println("[WARN] Invalid line in manifest: " + line);
                }
            }

        } catch (B2Exception | IOException e) {
            if (e instanceof B2Exception && ((B2Exception) e).getStatus() == 404) {
                System.out.println("[WARN] No previous manifest found. All files will be re-uploaded.");
            } else {
                e.printStackTrace();
                throw new RuntimeException("Failed to download manifest file");
            }
        }

        System.out.println("[INFO] Loaded " + previousHashes.size() + " previous hashes from the manifest");
    }

    private void updateManifest() {
        StringBuilder builder = new StringBuilder();
        for (String file : this.newHashes.keySet()) {
            builder.append(file).append(":").append(this.newHashes.get(file)).append(";");
        }

        GZIPOutputStream gzip;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            gzip = new GZIPOutputStream(out);
            gzip.write(builder.toString().getBytes(StandardCharsets.UTF_8));
            gzip.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to compress manifest");
        }

        if (!Constants.TESTING) {
            B2UploadFileRequest request = B2UploadFileRequest
                    .builder(this.bucketId, Constants.MANIFEST_FILE, "application/gzip", B2ByteArrayContentSource.build(out.toByteArray()))
                    .build();
            try {
                this.client.uploadSmallFile(request);
            } catch (B2Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Failed to upload manifest");
            }
        } else {
            try {
                Files.write(Path.of(Constants.MANIFEST_FILE), out.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException("Failed to write manifest file for testing dump: " + Constants.MANIFEST_FILE);
            }
        }
    }

    private static String stripInfo(String version) {
        if (version.contains("+build.")) {
            return version.substring(0, version.lastIndexOf('+'));
        } else {
            //TODO legacy remove when no longer needed
            char verSep = version.contains("-") ? '-' : '.';
            return version.substring(0, version.lastIndexOf(verSep));
        }
    }

    private static <T> JsonArray toJson(Iterable<T> items, Function<T, JsonElement> function) {
        JsonArray array = new JsonArray();

        for (T t : items) {
            JsonElement element = function.apply(t);

            if (element != null && !array.contains(element)) {
                array.add(element);
            }
        }

        return array;
    }

    private static JsonObject getLibrary(String mavenPath, String url) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("name", mavenPath);
        jsonObject.addProperty("url", url);
        return jsonObject;
    }

    private enum Side {
        CLIENT("client", "profile"), SERVER("server", "server");

        final String side;
        final String type;

        Side(String side, String type) {
            this.side = side;
            this.type = type;
        }
    }
}
