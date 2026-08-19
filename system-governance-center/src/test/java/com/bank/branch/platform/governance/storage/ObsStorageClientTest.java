package com.bank.branch.platform.governance.storage;

import com.obs.services.ObsClient;
import com.obs.services.model.ObsObject;
import com.obs.services.model.TemporarySignatureRequest;
import com.obs.services.model.TemporarySignatureResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ObsStorageClient 单测：验证对华为云 ObsClient 的委托（bucket/key/方法）。
 * 通过 ReflectionTestUtils 注入 mock obsClient，跳过 @PostConstruct 真连。
 */
@ExtendWith(MockitoExtension.class)
class ObsStorageClientTest {

    @Mock
    ObsClient obsClient;

    ObsStorageClient store;

    @BeforeEach
    void setUp() {
        store = new ObsStorageClient();
        ReflectionTestUtils.setField(store, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(store, "presignExpireSeconds", 600L);
        ReflectionTestUtils.setField(store, "obsClient", obsClient);
    }

    @Test
    void putObject_writesToBucketWithKey() {
        store.putObject("hello".getBytes(), "zybb/k1.xlsx");

        ArgumentCaptor<String> bucket = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(obsClient).putObject(bucket.capture(), key.capture(), any(InputStream.class));
        assertThat(bucket.getValue()).isEqualTo("test-bucket");
        assertThat(key.getValue()).isEqualTo("zybb/k1.xlsx");
    }

    @Test
    void putObject_streamsInputToBucketWithKey() {
        store.putObject(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "zybb/k1.xlsx");

        ArgumentCaptor<String> bucket = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(obsClient).putObject(bucket.capture(), key.capture(), any(InputStream.class));
        assertThat(bucket.getValue()).isEqualTo("test-bucket");
        assertThat(key.getValue()).isEqualTo("zybb/k1.xlsx");
    }

    @Test
    void getBytes_readsObjectContent() {
        ObsObject obj = mock(ObsObject.class);
        when(obj.getObjectContent()).thenReturn(new ByteArrayInputStream("data".getBytes()));
        when(obsClient.getObject("test-bucket", "k2")).thenReturn(obj);

        assertThat(store.getBytes("k2")).isEqualTo("data".getBytes());
    }

    @Test
    void writeTo_streamsObjectContentAndClosesObsInputButNotCallerOutput() {
        ObsObject obj = mock(ObsObject.class);
        TrackingInputStream input = new TrackingInputStream("streamed".getBytes(StandardCharsets.UTF_8));
        when(obj.getObjectContent()).thenReturn(input);
        when(obsClient.getObject("test-bucket", "k-stream")).thenReturn(obj);

        TrackingOutputStream output = new TrackingOutputStream();
        store.writeTo("k-stream", output);

        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("streamed");
        assertThat(input.closed).isTrue();
        assertThat(output.closed).isFalse();
    }

    @Test
    void deleteByKey_deletesObject() {
        store.deleteByKey("k3");
        verify(obsClient).deleteObject("test-bucket", "k3");
    }

    @Test
    void generatePresignedUrl_returnsSignedUrl() {
        TemporarySignatureResponse resp = mock(TemporarySignatureResponse.class);
        when(resp.getSignedUrl()).thenReturn("https://obs/test-bucket/k4?sig=x");
        when(obsClient.createTemporarySignature(any(TemporarySignatureRequest.class))).thenReturn(resp);

        assertThat(store.generatePresignedUrl("k4")).isEqualTo("https://obs/test-bucket/k4?sig=x");
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
