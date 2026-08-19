package com.bank.branch.platform.yundun.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.yundun.dto.CreditViolationSaveReq;
import com.bank.branch.platform.yundun.entity.CreditViolation;
import com.bank.branch.platform.yundun.mapper.CreditViolationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 信贷风险导入的批量写入契约。 */
@ExtendWith(MockitoExtension.class)
class CreditViolationBatchImportTest {

    @Mock
    private CreditViolationMapper mapper;

    @Test
    void importShouldSplit1201RowsInto500500And201AndNeverUseSingleInsert() {
        when(mapper.insertBatch(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        CreditViolationService service = new CreditViolationService(mapper);
        ArgumentCaptor<List<CreditViolation>> batches = ArgumentCaptor.forClass(List.class);

        int imported = service.importRows(rows(1201));

        assertThat(imported).isEqualTo(1201);
        verify(mapper, times(3)).insertBatch(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(500, 500, 201);
        verify(mapper, never()).insert(any(CreditViolation.class));
    }

    @Test
    void importShouldAcceptExactly3000RowsInSix500RowBatches() {
        when(mapper.insertBatch(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        CreditViolationService service = new CreditViolationService(mapper);
        ArgumentCaptor<List<CreditViolation>> batches = ArgumentCaptor.forClass(List.class);

        int imported = service.importRows(rows(3000));

        assertThat(imported).isEqualTo(3000);
        verify(mapper, times(6)).insertBatch(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size)
                .containsExactly(500, 500, 500, 500, 500, 500);
        verify(mapper, never()).insert(any(CreditViolation.class));
    }

    @Test
    void importShouldRejectMoreThan3000RowsBeforeAnyWrite() {
        CreditViolationService service = new CreditViolationService(mapper);

        assertThatThrownBy(() -> service.importRows(rows(3001)))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("YD-40003"));

        verifyNoInteractions(mapper);
    }

    @Test
    void importShouldRejectRowWithBlankRequiredEmployeeNumberBeforeAnyWrite() {
        CreditViolationSaveReq invalid = new CreditViolationSaveReq();
        invalid.setEmployeeNumber("  ");
        CreditViolationService service = new CreditViolationService(mapper);

        assertThatThrownBy(() -> service.importRows(List.of(invalid)))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("YD-40001"));

        verifyNoInteractions(mapper);
    }

    private static List<CreditViolationSaveReq> rows(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> {
                    CreditViolationSaveReq row = new CreditViolationSaveReq();
                    row.setEmployeeNumber("EMP-" + index);
                    return row;
                })
                .toList();
    }
}
