package org.quiltmc.meta.update.util.http;

import okhttp3.Interceptor;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.meta.update.util.DurationFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

public class LoggingInterceptor implements Interceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public @NotNull Response intercept(@NotNull Interceptor.Chain chain) throws IOException {

        var start = System.nanoTime();
        var response = chain.proceed(chain.request());
        var end = System.nanoTime();

        LOGGER.debug("{} {} - {}: {}", response.request().method(), response.code(), response.request().url(), DurationFormat.prettyPrintDuration(Duration.ofNanos(end - start)));

        return response;
    }


}
