package com.bank.branch.platform.portal.listener;

import com.bank.branch.platform.portal.event.ProductResponsibleUpdatedEvent;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductResponsibleSyncListener 单元测试
 * <p>Mockito 直接实例化，不需要 Spring 容器</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductResponsibleSyncListenerTest {

    @Mock
    AddrbookEmployeeMapper addrbookMapper;

    @InjectMocks
    ProductResponsibleSyncListener listener;

    /**
     * source=ADDRBOOK_SIDE 时应跳过，mapper 不被调用
     */
    @Test
    void shouldSkipNonProductSideEvent() {
        ProductResponsibleUpdatedEvent event = new ProductResponsibleUpdatedEvent(
                "P001", "PROD_CODE",
                Collections.emptyList(),
                List.of("E001"),
                "ADDRBOOK_SIDE",
                "OP001",
                LocalDateTime.now()
        );

        listener.onProductResponsibleUpdated(event);

        verify(addrbookMapper, never()).selectByEmpId(any());
        verify(addrbookMapper, never()).updateResponsibleProductsWithOptimisticLock(
                any(), any(), any(), any());
    }

    /**
     * added=[E001] 时，mock emp 有 [P_OLD]，verify update called with [P_OLD, NEW_P]
     */
    @Test
    void shouldAppendProductIdToAddedEmployees() {
        String productId = "NEW_P";
        AddrbookEmployee emp = new AddrbookEmployee();
        emp.setEmpId("E001");
        emp.setResponsibleProductIds(new ArrayList<>(List.of("P_OLD")));
        emp.setUpdatedTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(addrbookMapper.selectByEmpId("E001")).thenReturn(emp);
        when(addrbookMapper.updateResponsibleProductsWithOptimisticLock(
                eq("E001"), eq(List.of("P_OLD", "NEW_P")),
                eq(emp.getUpdatedTime()), eq("OP001")))
                .thenReturn(1);

        // beforeEmpIds=[], afterEmpIds=[E001] → added=[E001]
        ProductResponsibleUpdatedEvent event = new ProductResponsibleUpdatedEvent(
                productId, "PROD_CODE",
                Collections.emptyList(),
                List.of("E001"),
                "PRODUCT_SIDE",
                "OP001",
                LocalDateTime.now()
        );

        listener.onProductResponsibleUpdated(event);

        verify(addrbookMapper).updateResponsibleProductsWithOptimisticLock(
                eq("E001"), eq(List.of("P_OLD", "NEW_P")),
                eq(emp.getUpdatedTime()), eq("OP001"));
    }

    /**
     * removed=[E001] 时，mock emp 有 [P001, P002]，verify update called with [P002]
     */
    @Test
    void shouldRemoveProductIdFromRemovedEmployees() {
        String productId = "P001";
        AddrbookEmployee emp = new AddrbookEmployee();
        emp.setEmpId("E001");
        emp.setResponsibleProductIds(new ArrayList<>(List.of("P001", "P002")));
        emp.setUpdatedTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(addrbookMapper.selectByEmpId("E001")).thenReturn(emp);
        when(addrbookMapper.updateResponsibleProductsWithOptimisticLock(
                eq("E001"), eq(List.of("P002")),
                eq(emp.getUpdatedTime()), eq("OP001")))
                .thenReturn(1);

        // beforeEmpIds=[E001], afterEmpIds=[] → removed=[E001]
        ProductResponsibleUpdatedEvent event = new ProductResponsibleUpdatedEvent(
                productId, "PROD_CODE",
                List.of("E001"),
                Collections.emptyList(),
                "PRODUCT_SIDE",
                "OP001",
                LocalDateTime.now()
        );

        listener.onProductResponsibleUpdated(event);

        verify(addrbookMapper).updateResponsibleProductsWithOptimisticLock(
                eq("E001"), eq(List.of("P002")),
                eq(emp.getUpdatedTime()), eq("OP001"));
    }

    /**
     * mock update 前2次返回0（乐观锁冲突），第3次返回1，verify selectByEmpId 被调用3次
     */
    @Test
    void shouldRetryOnOptimisticLockFailure() {
        String productId = "NEW_P";
        AddrbookEmployee emp = new AddrbookEmployee();
        emp.setEmpId("E001");
        emp.setResponsibleProductIds(new ArrayList<>(List.of("P_OLD")));
        emp.setUpdatedTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(addrbookMapper.selectByEmpId("E001")).thenReturn(emp);
        when(addrbookMapper.updateResponsibleProductsWithOptimisticLock(
                eq("E001"), any(), any(), eq("OP001")))
                .thenReturn(0)
                .thenReturn(0)
                .thenReturn(1);

        // beforeEmpIds=[], afterEmpIds=[E001] → added=[E001]
        ProductResponsibleUpdatedEvent event = new ProductResponsibleUpdatedEvent(
                productId, "PROD_CODE",
                Collections.emptyList(),
                List.of("E001"),
                "PRODUCT_SIDE",
                "OP001",
                LocalDateTime.now()
        );

        listener.onProductResponsibleUpdated(event);

        verify(addrbookMapper, times(3)).selectByEmpId("E001");
        verify(addrbookMapper, times(3)).updateResponsibleProductsWithOptimisticLock(
                eq("E001"), any(), any(), eq("OP001"));
    }
}
