package com.bank.branch.platform.customer.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TouchTaskStatusTest {

    @Test
    void hasAllFourStatusesInOrder() {
        assertThat(TouchTaskStatus.values())
            .extracting(TouchTaskStatus::getCode)
            .containsExactly("PENDING", "IN_PROGRESS", "SUCCESS", "CANCELLED");
    }

    @Test
    void inProgressLabelIsChinese() {
        assertThat(TouchTaskStatus.IN_PROGRESS.getLabel()).isEqualTo("进行中");
    }

    @Test
    void inProgressCodeEqualsName() {
        assertThat(TouchTaskStatus.IN_PROGRESS.getCode()).isEqualTo("IN_PROGRESS");
    }
}
