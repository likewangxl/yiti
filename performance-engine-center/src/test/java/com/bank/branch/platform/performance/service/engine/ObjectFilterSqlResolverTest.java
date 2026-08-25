package com.bank.branch.platform.performance.service.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectFilterSqlResolverTest {

    @Test
    @DisplayName("新式 EMP_ID 谓词：有对象值时保留等值谓词")
    void directPredicate_withObjectId_keepsPredicate() {
        String sql = "SELECT emp_id AS base_key, SUM(curr_bal) AS metric_value "
                + "FROM h3 WHERE statis_dt = :dataDate\n  AND EMP_ID = :objectId GROUP BY emp_id";

        assertThat(ObjectFilterSqlResolver.resolve(sql, "11045575"))
                .isEqualTo(sql);
    }

    @Test
    @DisplayName("新式 EMP_ID 谓词：空白对象值移除完整 AND 谓词")
    void directPredicate_withBlankObjectId_removesPredicate() {
        String sql = "SELECT * FROM h3 WHERE statis_dt = :dataDate\n  AND EMP_ID = :objectId GROUP BY emp_id";

        String resolved = ObjectFilterSqlResolver.resolve(sql, "  \t");

        assertThat(resolved).doesNotContain("EMP_ID", ":objectId");
        assertThat(resolved).contains("WHERE statis_dt = :dataDate");
    }

    @Test
    @DisplayName("旧式可选条件：有对象值时化简为 EMP_ID 等值谓词")
    void legacyPredicate_withObjectId_simplifiesToEquality() {
        String sql = "SELECT * FROM h3 WHERE statis_dt = :dataDate "
                + "AND (:objectId IS NULL OR EMP_ID = :objectId) GROUP BY emp_id";

        String resolved = ObjectFilterSqlResolver.resolve(sql, "11045575");

        assertThat(resolved).contains("AND EMP_ID = :objectId");
        assertThat(resolved).doesNotContain("IS NULL", "OR EMP_ID");
    }

    @Test
    @DisplayName("旧式可选条件：空对象值移除完整 AND 谓词")
    void legacyPredicate_withNullObjectId_removesPredicate() {
        String sql = "SELECT * FROM h3 WHERE statis_dt = :dataDate "
                + "AND (:objectId IS NULL OR EMP_ID = :objectId) GROUP BY emp_id";

        String resolved = ObjectFilterSqlResolver.resolve(sql, null);

        assertThat(resolved).doesNotContain("EMP_ID", ":objectId", "IS NULL");
        assertThat(resolved).contains("WHERE statis_dt = :dataDate");
    }

    @Test
    @DisplayName("大小写和空白变化仍识别带别名的旧式谓词")
    void legacyPredicate_withAliasAndWhitespace_isRecognized() {
        String sql = "SELECT * FROM h3 e WHERE e.DATA_SRC = :dataSrc "
                + "aNd  ( :objectId is null OR e . emp_id\n=\t:objectId )";

        String resolved = ObjectFilterSqlResolver.resolve(sql, "11045575");

        assertThat(resolved).contains("AND e . emp_id = :objectId");
        assertThat(resolved).doesNotContain("is null", "OR e");
    }

    @Test
    @DisplayName("支持表别名，但只处理固定 EMP_ID + objectId")
    void aliasedPredicate_isSupported_withoutChangingOtherParameters() {
        String sql = "SELECT e.EMP_ID AS base_key FROM h3 e "
                + "WHERE e.DATA_SRC = :dataSrc AND e.EMP_ID = :objectId "
                + "AND e.EMP_CODE = :objectId";

        String resolved = ObjectFilterSqlResolver.resolve(sql, null);

        assertThat(resolved).doesNotContain("e.EMP_ID = :objectId");
        assertThat(resolved).contains("e.DATA_SRC = :dataSrc", "e.EMP_CODE = :objectId");
    }

    @Test
    @DisplayName("字符串、注释及非规范谓词保持不变")
    void literalsCommentsAndNonCanonicalPredicates_areNotChanged() {
        String sql = "SELECT 'AND EMP_ID = :objectId ' AS literal, "
                + "\"AND EMP_ID = :objectId \" AS quoted_literal, "
                + "`AND EMP_ID = :objectId ` AS quoted_identifier FROM h3 "
                + "-- AND EMP_ID = :objectId\n"
                + "WHERE note = :objectId AND EMP_CODE = :objectId /* AND EMP_ID = :objectId */";

        assertThat(ObjectFilterSqlResolver.resolve(sql, null)).isEqualTo(sql);
    }

    @Test
    void normalizeObjectId_trimsAndTreatsBlankAsNull() {
        assertThat(ObjectFilterSqlResolver.normalizeObjectId(" 11045575 "))
                .isEqualTo("11045575");
        assertThat(ObjectFilterSqlResolver.normalizeObjectId(" \t\n ")).isNull();
        assertThat(ObjectFilterSqlResolver.normalizeObjectId(null)).isNull();
    }
}
