package com.bank.branch.platform.auth.location.geocode;

import com.bank.branch.platform.auth.location.OrgLocationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 高德适配器的响应状态、结构和响应体上限测试，不发起外部网络请求。 */
class AmapOrgLocationGeocoderTest {

    @Test
    void providerFailureStatusIsNotMisreportedAsNoCandidates() throws Exception {
        AmapOrgLocationGeocoder geocoder = geocoderWithBody("{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\"}");

        assertThatThrownBy(() -> geocoder.geocode("陕西省西安市雁塔区1号", "610100"))
                .hasMessageContaining("服务返回失败");
    }

    @Test
    void nonArrayGeocodesIsMalformedResponse() throws Exception {
        AmapOrgLocationGeocoder geocoder = geocoderWithBody("{\"status\":\"1\",\"geocodes\":{}}");

        assertThatThrownBy(() -> geocoder.geocode("陕西省西安市雁塔区1号", "610100"))
                .hasMessageContaining("响应格式无效");
    }

    @Test
    void boundedBodySubscriberCancelsBeforeDelegatingOversizedBody() {
        @SuppressWarnings("unchecked")
        HttpResponse.ResponseInfo responseInfo = mock(HttpResponse.ResponseInfo.class);
        HttpResponse.BodySubscriber<byte[]> subscriber =
                AmapOrgLocationGeocoder.boundedBodyHandler(8).apply(responseInfo);
        boolean[] cancelled = {false};
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancelled[0] = true;
            }
        });
        subscriber.onNext(List.of(ByteBuffer.wrap("123456789".getBytes(StandardCharsets.UTF_8))));

        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .hasRootCauseMessage("response too large");
        assertThat(cancelled[0]).isTrue();
    }

    @Test
    void slowResponseFutureIsCancelledAtRequestTimeout() {
        OrgLocationProperties properties = properties();
        properties.setRequestTimeoutMillis(20);
        HttpClient client = mock(HttpClient.class);
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
        when(client.sendAsync(any(), any())).thenAnswer(invocation -> pending);
        AmapOrgLocationGeocoder geocoder = new AmapOrgLocationGeocoder(properties,
                new ObjectMapper(), client);

        assertThatThrownBy(() -> geocoder.geocode("陕西省西安市雁塔区1号", "610100"))
                .hasMessageContaining("请求超时");
        assertThat(pending).isCancelled();
    }

    private static AmapOrgLocationGeocoder geocoderWithBody(String body) throws Exception {
        OrgLocationProperties properties = properties();
        HttpClient client = mock(HttpClient.class);
        when(client.sendAsync(any(), any())).thenAnswer(invocation ->
                CompletableFuture.completedFuture(response(200, body)));
        return new AmapOrgLocationGeocoder(properties, new ObjectMapper(), client);
    }

    private static OrgLocationProperties properties() {
        OrgLocationProperties properties = new OrgLocationProperties();
        properties.setGeocodingEnabled(true);
        properties.setApiKey("test-key");
        properties.setMaxResponseBytes(1024);
        return properties;
    }

    private static HttpResponse<byte[]> response(int status, String body) {
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return response;
    }
}
