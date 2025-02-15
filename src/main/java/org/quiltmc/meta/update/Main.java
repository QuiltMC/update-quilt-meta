package org.quiltmc.meta.update;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.meta.update.cache.AbstractCacheService;
import org.quiltmc.meta.update.cache.cloudflare.CloudflareCacheService;
import org.quiltmc.meta.update.cache.noop.NoopCacheService;
import org.quiltmc.meta.update.cli.CacheOptions;
import org.quiltmc.meta.update.cli.StorageOptions;
import org.quiltmc.meta.update.collector.v3.*;
import org.quiltmc.meta.update.storage.AbstractStorageService;
import org.quiltmc.meta.update.storage.StorageManifest;
import org.quiltmc.meta.update.storage.backblaze.B2StorageService;
import org.quiltmc.meta.update.storage.file.FileBasedStorageService;
import org.quiltmc.meta.update.storage.s3.S3StorageService;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.upload.InputCollector;
import org.quiltmc.meta.update.util.maven.MavenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Main generator class for the meta.
 */
@CommandLine.Command(name = "update-quilt-meta", mixinStandardHelpOptions = true)
public class Main implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    @Nullable
    @CommandLine.ArgGroup
    private StorageOptions storageOptions;

    @Nullable
    @CommandLine.ArgGroup
    private CacheOptions cacheOptions;

    public static void main(String[] args) {
        LOGGER.info("Running build {}", Constants.TOOL_VERSION);

        int exitCode = new CommandLine(new Main()).execute(args);

        if (exitCode != 0) {
            LOGGER.error("Failed to build meta. Please refer to the logs and report this to the Infrastructure team.");
        }
        System.exit(exitCode);
    }

    private AbstractCacheService createCacheService() {
        if(cacheOptions == null) {
            LOGGER.info("No cache service configured.");
            return new NoopCacheService();
        }

        var cfOpts = cacheOptions.cloudflare;
        if(cfOpts != null) {
            LOGGER.info("Using cloudflare cache service.");
            return new CloudflareCacheService(cfOpts.zoneId, cfOpts.accessToken, cfOpts.baseUrl);
        }

        throw new AssertionError();
    }

    private AbstractStorageService createStorageService() {
        if(storageOptions == null) {
            throw new IllegalStateException("No storage backend configured!");
        }

        var b2 = storageOptions.b2;
        if(b2 != null) {
            LOGGER.info("Using B2 storage backend.");
            return new B2StorageService(b2);
        }

        var file = storageOptions.file;
        if(file != null) {
            LOGGER.info("Using file based storage backend.");
            return new FileBasedStorageService(file);
        }

        var s3 = storageOptions.s3;
        if(s3 != null) {
            LOGGER.info("Using S3 storage backend.");
            return new S3StorageService(s3);
        }

        throw new AssertionError();
    }

    @Override
    public Integer call() throws Exception {
        AbstractCacheService cache = createCacheService();
        try (AbstractStorageService storage = createStorageService(); var quiltMaven = MavenRepository.of(Constants.QUILT_MAVEN_URL); var fabricMaven = MavenRepository.of(Constants.FABRIC_MAVEN_URL)) {
            storage.init();

            Optional<StorageManifest> existingManifest;
            try {
                existingManifest = storage.readExistingManifest();
            } catch (Exception e) {
                LOGGER.error("Unable to read existing manifest", e);
                existingManifest = Optional.empty();
            }
            existingManifest.ifPresentOrElse(existing -> LOGGER.info("Loaded {} previous hashes from the manifest", existing.files().size()), () -> LOGGER.warn("No previous manifest found. All files will be re-uploaded."));

            LOGGER.info("Gathering data...");
            // @formatter:off
            List<InputCollector> inputs = List.of(
                    new HashedMojmapAndGameVersionCollectorV3(quiltMaven),
                    new IntermediaryVersionCollectorV3(fabricMaven),
                    new QuiltMappingsCollectorV3(quiltMaven),
                    new QuiltInstallerVersionCollectorV3(quiltMaven),
                    new QuiltLoaderVersionCollectorV3(quiltMaven),
                    new QuiltLauncherMetadataCollectorV3(quiltMaven, fabricMaven),
                    new LauncherProfileCollectorV3(quiltMaven, fabricMaven),
                    new RootVersionListCollectorV3(quiltMaven),
                    new StaticFileCollectorV3()
            );
            // @formatter:on

            Map<String, FileInfo> allFiles = Collections.synchronizedMap(new LinkedHashMap<>());
            BiConsumer<String, FileInfo> uploadConsumer = (path, file) -> {
                var previous = allFiles.put(path, file);
                if (previous != null && !Arrays.equals(previous.content(), file.content())) {
                    throw new IllegalStateException("Tried to cache duplicate file: " + path);
                }
            };

            try (ExecutorService workerExecutor = Executors.newCachedThreadPool()) {
                CompletableFuture.allOf(inputs.parallelStream().map(it -> it.gatherData(uploadConsumer, workerExecutor)).toArray(CompletableFuture[]::new)).join();
                workerExecutor.shutdown();
                if (!workerExecutor.awaitTermination(30L, TimeUnit.SECONDS)) {
                    LOGGER.error("Worker thread deadlock detected!");
                    return 1;
                }
            }

            List<String> filesChanged = new ArrayList<>();
            try (var uploadExecutor = Executors.newFixedThreadPool(20)) {
                BiPredicate<String, String> needsUpdate = existingManifest.map(BiPredicate::negate).orElse((s, s2) -> true);
                Map<String, FileInfo> filesToUpdate = allFiles.entrySet().stream().filter(fileEntry -> needsUpdate.test(fileEntry.getKey(), fileEntry.getValue().manifestSha1())).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

                // TODO progress indicator because this can run for a few hours
                if (!filesToUpdate.isEmpty()) {
                    LOGGER.info("Writing {} changed files...", filesToUpdate.size());
                    storage.uploadFiles(filesToUpdate, uploadExecutor).join();
                    filesChanged.addAll(filesToUpdate.keySet());
                }

                var manifest = StorageManifest.ofFiles(allFiles);

                var filesToRemove = existingManifest.map(existing -> existing.files().keySet().stream().filter(Predicate.not(manifest.files()::containsKey)).toList()).orElseGet(List::of);
                if (!filesToRemove.isEmpty()) {
                    LOGGER.info("Deleting {} old files...", filesToRemove.size());
                    storage.deleteFiles(filesToRemove, uploadExecutor).join();
                    filesChanged.addAll(filesToRemove);
                }

                if (!filesChanged.isEmpty()) {
                    LOGGER.info("Writing new manifest...");
                    storage.uploadManifest(manifest, uploadExecutor).join();
                } else {
                    LOGGER.info("No file changes detected, not writing new manifest.");
                }
            }

            if (!filesChanged.isEmpty()) {
                cache.purgeCache(filesChanged);
            }

            LOGGER.info("Content synced!");

            // Print a changed file collapsible for GitHub Actions
            System.out.printf("::group::Changed files: (%d total)%n", filesChanged.size());
            filesChanged.stream().sorted().forEachOrdered(System.out::println);
            System.out.println("::endgroup::");
        }

        return 0;
    }
}
