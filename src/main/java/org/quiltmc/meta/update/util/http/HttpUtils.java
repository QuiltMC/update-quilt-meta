package org.quiltmc.meta.update.util.http;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.meta.update.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public class HttpUtils {

    public static OkHttpClient.Builder newClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(new LoggingInterceptor())
                .addInterceptor(new AddDefaultHeaderInterceptor(HeaderNames.USER_AGENT, Constants.USER_AGENT))
                .followRedirects(true);
    }

    public static List<String> readBody(@Nullable ResponseBody body) throws IOException {
        if (body == null) {
            return List.of();
        }

        try (var reader = new BufferedReader(body.charStream())) {
            return reader.lines().toList();
        }
    }
}
