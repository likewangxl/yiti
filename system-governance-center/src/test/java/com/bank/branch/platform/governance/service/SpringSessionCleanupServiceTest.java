package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.governance.mapper.SpringSessionMaintenanceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SpringSessionCleanupService 单测：清理孤儿 session 属性委托 + 返回删除行数。
 */
@ExtendWith(MockitoExtension.class)
class SpringSessionCleanupServiceTest {

    @Mock
    SpringSessionMaintenanceMapper mapper;

    @InjectMocks
    SpringSessionCleanupService service;

    @Test
    void cleanOrphanAttributes_delegatesToMapper_andReturnsCount() {
        when(mapper.deleteOrphanAttributes()).thenReturn(7);

        int n = service.cleanOrphanAttributes();

        assertThat(n).isEqualTo(7);
        verify(mapper).deleteOrphanAttributes();
    }
}
