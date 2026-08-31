package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.entity.UserProductRelation;
import com.bank.branch.platform.portal.mapper.UserProductRelationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProductRelationServiceTest {

    @Mock
    private UserProductRelationMapper relationMapper;

    @InjectMocks
    private UserProductRelationService relationService;

    @Test
    void listProductIdsByUserId_readsRelationRows() {
        when(relationMapper.listByUserId("U001")).thenReturn(List.of(
                relation("U001", "P001"), relation("U001", "P002")));

        assertThat(relationService.listProductIdsByUserId("U001"))
                .containsExactly("P001", "P002");
    }

    @Test
    void replaceProductsForUser_replacesWholeSetWithBatchRows() {
        when(relationMapper.listByUserId("U001")).thenReturn(List.of(relation("U001", "P001")));

        relationService.replaceProductsForUser("U001", List.of("P002", "P003", "P003"), "OP001");

        verify(relationMapper).deleteByUserId("U001");
        verify(relationMapper).insertBatch(any());
    }

    @Test
    void listUserIdsByProductId_readsReverseRelation() {
        when(relationMapper.listByProductId("P001")).thenReturn(List.of(
                relation("U001", "P001"), relation("U002", "P001")));

        assertThat(relationService.listUserIdsByProductId("P001"))
                .containsExactly("U001", "U002");
    }

    @Test
    void countByProductId_delegatesReferenceCheck() {
        when(relationMapper.countByProductId("P001")).thenReturn(2);

        assertThat(relationService.countUsersByProductId("P001")).isEqualTo(2);
        verify(relationMapper).countByProductId(eq("P001"));
    }

    @Test
    void mapUserIdsByProductIds_splitsLargeQueryIntoBoundedBatches() {
        List<String> productIds = IntStream.range(0, 1001)
                .mapToObj(index -> "P" + index)
                .toList();
        when(relationMapper.listByProductIds(any())).thenAnswer(invocation ->
                ((List<String>) invocation.getArgument(0)).stream()
                        .map(productId -> relation("U001", productId))
                        .toList());

        Map<String, List<String>> result = relationService.mapUserIdsByProductIds(productIds);

        assertThat(result.get("P0")).containsExactly("U001");
        assertThat(result).hasSize(1001);
        verify(relationMapper, org.mockito.Mockito.times(3))
                .listByProductIds(org.mockito.ArgumentMatchers.argThat(batch -> batch.size() <= 500));
    }

    @Test
    void replaceProductsForUser_splitsLargeInsertIntoBoundedBatches() {
        List<String> productIds = IntStream.range(0, 1001)
                .mapToObj(index -> "P" + index)
                .toList();
        when(relationMapper.listByUserId("U001")).thenReturn(List.of());

        relationService.replaceProductsForUser("U001", productIds, "OP001");

        verify(relationMapper, org.mockito.Mockito.times(3))
                .insertBatch(org.mockito.ArgumentMatchers.argThat(batch -> batch.size() <= 500));
    }

    private UserProductRelation relation(String userId, String productId) {
        UserProductRelation relation = new UserProductRelation();
        relation.setUserId(userId);
        relation.setProductId(productId);
        return relation;
    }
}
