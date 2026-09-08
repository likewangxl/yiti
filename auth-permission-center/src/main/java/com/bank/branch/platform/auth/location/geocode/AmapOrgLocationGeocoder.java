package com.bank.branch.platform.auth.location.geocode;

import com.bank.branch.platform.auth.location.OrgLocationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.nio.ByteBuffer;

/** 高德服务端地理编码适配器，固定使用 HTTPS 地址且不会记录含 Key 的请求 URL。 */
public class AmapOrgLocationGeocoder implements OrgLocationGeocoder {

    static final String ENDPOINT = "https://restapi.amap.com/v3/geocode/geo";
    private static final int MAX_CITY_CODE_LENGTH = 6;
    private static final String COORD_SYS_GCJ02 = "GCJ02";

    private final OrgLocationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AmapOrgLocationGeocoder(OrgLocationProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMillis())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    AmapOrgLocationGeocoder(OrgLocationProperties properties, ObjectMapper objectMapper,
                            HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean isAvailable() {
        return properties.isGeocodingEnabled() && hasKey();
    }

    @Override
    public String provider() {
        return "AMAP";
    }

    @Override
    public List<OrgLocationGeocodeCandidate> geocode(String address, String cityCode) {
        if (!isAvailable()) {
            throw new IllegalStateException("地址解析未启用或服务Key未配置");
        }
        String normalizedAddress = normalize(address);
        String normalizedCity = normalize(cityCode);
        if (normalizedAddress == null || normalizedCity == null) {
            throw new IllegalArgumentException("地址和城市编码不能为空");
        }
        if (!isStandardCityCode(normalizedCity)) {
            throw new IllegalArgumentException("城市编码必须为6位行政区划编码");
        }
        String queryCity = cityQuery(normalizedCity);
        String query = "address=" + encode(normalizedAddress)
                + "&city=" + encode(queryCity)
                + "&key=" + encode(properties.getApiKey())
                + "&output=JSON";
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + "?" + query))
                .timeout(Duration.ofMillis(Math.max(1, properties.getRequestTimeoutMillis())))
                .header("Accept", "application/json")
                .GET()
                .build();
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            future = httpClient.sendAsync(request,
                    boundedBodyHandler(Math.max(1, properties.getMaxResponseBytes())));
            HttpResponse<byte[]> response = future.get(
                    Math.max(1, properties.getRequestTimeoutMillis()), TimeUnit.MILLISECONDS);
            if (response == null || response.body() == null) {
                throw new IllegalStateException("地址解析响应为空");
            }
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("地址解析服务返回 HTTP " + response.statusCode());
            }
            String body = new String(response.body(), StandardCharsets.UTF_8);
            return parseCandidates(body, normalizedCity);
        } catch (InterruptedException ex) {
            cancel(future);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("地址解析请求被中断");
        } catch (TimeoutException ex) {
            cancel(future);
            throw new IllegalStateException("地址解析请求超时");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ResponseTooLargeException) {
                throw new IllegalStateException("地址解析响应超出大小限制");
            }
            // 不把 HttpRequest/URI 作为 cause 向外暴露，避免异常链携带服务 Key。
            throw new IllegalStateException("地址解析请求失败");
        } catch (CancellationException ex) {
            throw new IllegalStateException("地址解析请求被取消");
        }
    }

    private List<OrgLocationGeocodeCandidate> parseCandidates(String body, String requestedCity) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"1".equals(root.path("status").asText())) {
                throw new IllegalStateException("地址解析服务返回失败");
            }
            List<OrgLocationGeocodeCandidate> result = new ArrayList<>();
            JsonNode geocodes = root.path("geocodes");
            if (!geocodes.isArray()) {
                throw new IllegalStateException("地址解析响应格式无效");
            }
            int max = Math.max(1, properties.getMaxCandidates());
            for (JsonNode geocode : geocodes) {
                if (result.size() >= max) {
                    break;
                }
                String adcode = normalize(geocode.path("adcode").asText(null));
                String candidateCity = cityFromAdcode(adcode);
                if (candidateCity == null || !requestedCity.equals(candidateCity)) {
                    continue;
                }
                String[] location = geocode.path("location").asText("").split(",", -1);
                if (location.length != 2) {
                    continue;
                }
                BigDecimal lng = decimal(location[0]);
                BigDecimal lat = decimal(location[1]);
                if (!validCoordinate(lng, lat)) {
                    continue;
                }
                String formatted = normalize(geocode.path("formatted_address").asText(null));
                String level = normalize(geocode.path("level").asText(null));
                result.add(new OrgLocationGeocodeCandidate("AMAP", formatted, candidateCity,
                        lng, lat, COORD_SYS_GCJ02, level));
            }
            return result;
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("地址解析响应格式无效");
        }
    }

    /**
     * 在委托给 JDK byte-array subscriber 前统计响应字节数；超限立即取消订阅，
     * 避免先完整读入内存再检查上限。
     */
    static BodyHandler<byte[]> boundedBodyHandler(int maxBytes) {
        int limit = Math.max(1, maxBytes);
        return ignored -> new BoundedBodySubscriber(limit);
    }

    private static void cancel(CompletableFuture<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private static final class BoundedBodySubscriber implements BodySubscriber<byte[]> {

        private final BodySubscriber<byte[]> delegate = BodySubscribers.ofByteArray();
        private final int maxBytes;
        private Flow.Subscription subscription;
        private int totalBytes;
        private boolean failed;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();

        private BoundedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            delegate.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                }
            });
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (failed || items == null) {
                return;
            }
            long batchBytes = 0;
            for (ByteBuffer item : items) {
                if (item != null) {
                    batchBytes += item.remaining();
                    if ((long) totalBytes + batchBytes > maxBytes) {
                        fail(new ResponseTooLargeException());
                        return;
                    }
                }
            }
            totalBytes += (int) batchBytes;
            try {
                delegate.onNext(items);
            } catch (RuntimeException ex) {
                fail(ex);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (failed) {
                return;
            }
            failed = true;
            try {
                delegate.onError(throwable);
            } finally {
                result.completeExceptionally(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (failed) {
                return;
            }
            try {
                delegate.onComplete();
                delegate.getBody().whenComplete((body, failure) -> {
                    if (failure == null) {
                        result.complete(body);
                    } else {
                        result.completeExceptionally(failure);
                    }
                });
            } catch (RuntimeException ex) {
                fail(ex);
            }
        }

        private void fail(Throwable throwable) {
            if (failed) {
                return;
            }
            failed = true;
            if (subscription != null) {
                subscription.cancel();
            }
            try {
                delegate.onError(throwable);
            } finally {
                result.completeExceptionally(throwable);
            }
        }
    }

    private static final class ResponseTooLargeException extends RuntimeException {
        private ResponseTooLargeException() {
            super("response too large");
        }
    }

    private boolean hasKey() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    static String cityQuery(String cityCode) {
        if (isStandardCityCode(cityCode)) {
            return cityCode.substring(0, 4) + "00";
        }
        return cityCode;
    }

    static String cityFromAdcode(String adcode) {
        if (!isStandardCityCode(adcode)) {
            return null;
        }
        return adcode.substring(0, 4) + "00";
    }

    private static boolean isStandardCityCode(String value) {
        return value != null && value.length() == MAX_CITY_CODE_LENGTH
                && value.chars().allMatch(ch -> ch >= '0' && ch <= '9');
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean validCoordinate(BigDecimal lng, BigDecimal lat) {
        return lng != null && lat != null
                && lng.compareTo(BigDecimal.valueOf(-180)) >= 0
                && lng.compareTo(BigDecimal.valueOf(180)) <= 0
                && lat.compareTo(BigDecimal.valueOf(-90)) >= 0
                && lat.compareTo(BigDecimal.valueOf(90)) <= 0;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
