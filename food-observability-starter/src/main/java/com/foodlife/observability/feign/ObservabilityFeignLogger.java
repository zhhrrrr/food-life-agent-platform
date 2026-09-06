package com.foodlife.observability.feign;

import com.foodlife.observability.properties.ObservabilityProperties;
import feign.Logger;
import feign.Request;
import feign.Response;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ObservabilityFeignLogger extends Logger {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ObservabilityFeignLogger.class);

    private final ObservabilityProperties properties;

    public ObservabilityFeignLogger(ObservabilityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void log(String configKey, String format, Object... args) {
        log.debug(String.format(methodTag(configKey) + format, args));
    }

    @Override
    protected Response logAndRebufferResponse(String configKey,
                                              Level logLevel,
                                              Response response,
                                              long elapsedTime) throws IOException {
        Request request = response.request();
        String method = request == null || request.httpMethod() == null ? "UNKNOWN" : request.httpMethod().name();
        String url = request == null ? "UNKNOWN" : request.url();
        if (elapsedTime >= properties.getSlowFeignThresholdMs()) {
            log.warn("feign request slow, client={}, method={}, url={}, status={}, durationMs={}",
                    configKey, method, url, response.status(), elapsedTime);
        } else {
            log.info("feign request finished, client={}, method={}, url={}, status={}, durationMs={}",
                    configKey, method, url, response.status(), elapsedTime);
        }
        return super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
    }

    @Override
    protected IOException logIOException(String configKey,
                                         Level logLevel,
                                         IOException ioe,
                                         long elapsedTime) {
        log.warn("feign request failed, client={}, durationMs={}, error={}", configKey, elapsedTime, ioe.getMessage());
        return super.logIOException(configKey, logLevel, ioe, elapsedTime);
    }
}
