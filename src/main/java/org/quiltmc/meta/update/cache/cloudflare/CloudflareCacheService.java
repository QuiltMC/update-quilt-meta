package org.quiltmc.meta.update.cache.cloudflare;

import com.google.common.collect.Iterables;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.cache.AbstractCacheService;
import org.quiltmc.meta.update.util.DurationFormat;
import org.quiltmc.meta.update.util.http.AddDefaultHeaderInterceptor;
import org.quiltmc.meta.update.util.http.ContentType;
import org.quiltmc.meta.update.util.http.HeaderNames;
import org.quiltmc.meta.update.util.http.HttpUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CloudflareCacheService extends AbstractCacheService {

    public static final int PURGE_REQUEST_LIMIT_PER_MINUTE = 1000;
    public static final int PURGE_URL_LIMIT_PER_REQUEST = 30;

    private final OkHttpClient client;
    private final String zoneId;
    private final String baseUrl;

    public CloudflareCacheService(String zoneId, String accessToken, URI baseUrl) {
        this.zoneId = zoneId;
        this.client = HttpUtils.newClient().addInterceptor(new AddDefaultHeaderInterceptor(HeaderNames.ACCEPT, ContentType.APPLICATION_JSON.value())).addInterceptor(new AddDefaultHeaderInterceptor(HeaderNames.AUTHORIZATION, "Bearer " + accessToken)).build();
        var baseUrlStr = baseUrl.toString();
        if (!baseUrlStr.endsWith("/")) {
            baseUrlStr += '/';
        }
        this.baseUrl = baseUrlStr;
    }

    @Override
    public void purgeCache(List<String> paths) throws Exception {
        LOGGER.info("Purging {} urls (eta. {} min(s))", paths.size(), DurationFormat.prettyPrintDuration(Duration.ofSeconds((long) ((paths.size() / (float) PURGE_REQUEST_LIMIT_PER_MINUTE) * 60))));

        var semaphore = new Semaphore(PURGE_REQUEST_LIMIT_PER_MINUTE);
        try (var scheduler = Executors.newScheduledThreadPool(1)) {
            scheduler.scheduleAtFixedRate(() -> semaphore.release(PURGE_REQUEST_LIMIT_PER_MINUTE - semaphore.availablePermits()), 1, 1, TimeUnit.MINUTES);

            for (List<String> strings : Iterables.partition(paths, PURGE_URL_LIMIT_PER_REQUEST)) {
                // block until quota resets
                semaphore.acquire();

                var urls = strings.stream().map(p -> {
                    try {
                        return new URI(baseUrl + p).toURL();
                    } catch (URISyntaxException | MalformedURLException e) {
                        throw new RuntimeException("Unable to construct valid URL for '%s%s'".formatted(baseUrl, p), e);
                    }
                }).toList();

                var jsonBody = Constants.GSON.toJson(new PurgeRequest(urls)).getBytes(StandardCharsets.UTF_8);

                var request = new Request.Builder().url("https://api.cloudflare.com/client/v4/zones/%s/purge_cache".formatted(zoneId)).post(RequestBody.create(jsonBody, MediaType.parse(ContentType.APPLICATION_JSON.value() + "; charset=utf8"))).build();

                try (var response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        var errMsg = new StringBuilder("Error making purge request for").append(response.request().url()).append(": ").append(response.code()).append(" - ").append(response.message());

                        var body = response.body();
                        if (body != null) {
                            try (var reader = new BufferedReader(body.charStream())) {
                                reader.lines().forEachOrdered(line -> errMsg.append("\n\t").append(line));
                            }
                        }

                        throw new IOException(errMsg.toString());
                    }
                }
            }
        }
    }
}
