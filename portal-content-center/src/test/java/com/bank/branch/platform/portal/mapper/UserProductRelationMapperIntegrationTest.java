package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.UserProductRelation;
import com.bank.branch.platform.portal.support.AbstractMapperIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PORTAL_USER_PRODUCT_REL 自定义查询及批量写入 SQL 集成测试。 */
class UserProductRelationMapperIntegrationTest extends AbstractMapperIntegrationTest {

    private static final String USER_A = "REL_IT_USER_A";
    private static final String USER_B = "REL_IT_USER_B";
    private static final String PRODUCT_A = "REL_IT_PRODUCT_A";
    private static final String PRODUCT_B = "REL_IT_PRODUCT_B";

    @Autowired
    private UserProductRelationMapper relationMapper;

    @AfterEach
    void cleanFixtureRelations() {
        relationMapper.deleteByUserId(USER_A);
        relationMapper.deleteByUserId(USER_B);
    }

    @Test
    void insertBatch_listAndCountShouldUseCompositeBusinessKey() {
        relationMapper.insertBatch(List.of(
                relation(USER_A, PRODUCT_A), relation(USER_A, PRODUCT_B), relation(USER_B, PRODUCT_A)));

        assertThat(relationMapper.listByUserId(USER_A))
                .extracting(UserProductRelation::getProductId)
                .containsExactly(PRODUCT_A, PRODUCT_B);
        assertThat(relationMapper.listByProductId(PRODUCT_A))
                .extracting(UserProductRelation::getUserId)
                .containsExactly(USER_A, USER_B);
        assertThat(relationMapper.countByProductId(PRODUCT_A)).isEqualTo(2);
    }

    @Test
    void deleteByProductAndUserIdsShouldDeleteOnlySelectedOwners() {
        relationMapper.insertBatch(List.of(
                relation(USER_A, PRODUCT_A), relation(USER_B, PRODUCT_A), relation(USER_A, PRODUCT_B)));

        relationMapper.deleteByProductAndUserIds(PRODUCT_A, List.of(USER_A));

        assertThat(relationMapper.listByProductId(PRODUCT_A))
                .extracting(UserProductRelation::getUserId)
                .containsExactly(USER_B);
        assertThat(relationMapper.listByUserId(USER_A))
                .extracting(UserProductRelation::getProductId)
                .containsExactly(PRODUCT_B);
    }

    private UserProductRelation relation(String userId, String productId) {
        UserProductRelation relation = new UserProductRelation();
        relation.setUserId(userId);
        relation.setProductId(productId);
        relation.setAssignedTime(LocalDateTime.of(2026, 8, 31, 10, 0));
        relation.setUpdatedTime(LocalDateTime.of(2026, 8, 31, 10, 0));
        relation.setUpdatedBy("REL_IT");
        return relation;
    }
}
