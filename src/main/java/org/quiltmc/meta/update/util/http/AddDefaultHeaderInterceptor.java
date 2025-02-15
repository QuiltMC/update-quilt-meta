package org.quiltmc.meta.update.util.http;

import okhttp3.Interceptor;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public record AddDefaultHeaderInterceptor(String headerName, String headerValue) implements Interceptor {

    @Override
    public @NotNull Response intercept(@NotNull Interceptor.Chain chain) throws IOException {

        var request = chain.request();
        if(request.header(headerName()) == null) {
            request = request.newBuilder().addHeader(headerName(), headerValue()).build();
        }

        return chain.proceed(request);
    }
}
