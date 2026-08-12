package com.bank.branch.platform.governance.storage;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ObsStorageClient} 的隔离测试：禁用 OBS 时保留 FileService 所需 Bean，且拒绝所有外联操作。
 */
class ObsStorageClientIsolationTest {

    private ObsStorageClient store;

    @BeforeEach
    void setUp() {
        store = new ObsStorageClient();
        ReflectionTestUtils.setField(store, "endPoint", "https://example.invalid");
        ReflectionTestUtils.setField(store, "accessKey", "test-access-key");
        ReflectionTestUtils.setField(store, "secretKey", "test-secret-key");
        ReflectionTestUtils.setField(store, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(store, "presignExpireSeconds", 600L);
        ReflectionTestUtils.setField(store, "enabled", false);
    }

    @Test
    @DisplayName("obs.enabled=false 时不构造真实客户端，且所有对象操作均明确拒绝")
    void disabled_doesNotInitializeClientAndFailsClosedForEveryOperation() {
        store.init();

        assertThat(ReflectionTestUtils.getField(store, "obsClient")).isNull();
        assertDisabled(() -> store.putObject("content".getBytes(), "isolated/file.txt"));
        assertDisabled(() -> store.getBytes("isolated/file.txt"));
        assertDisabled(() -> store.deleteByKey("isolated/file.txt"));
        assertDisabled(() -> store.generatePresignedUrl("isolated/file.txt"));
    }

    private void assertDisabled(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getCode()).isEqualTo(GovErrorCode.OBS_DISABLED.getCode());
                    assertThat(exception.getMessage()).contains("obs.enabled=false");
                });
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }
}
