#!/usr/bin/env python3
"""AUTH 机构画像/机构组 SQL 的字符集静态契约。

本测试只读取 SQL 文本，不连接数据库、不执行 SQL，也不修改任何目标库。
"""

from __future__ import annotations

from pathlib import Path
import unittest


THIS_FILE = Path(__file__).resolve()
REPO_ROOT = THIS_FILE.parents[4]
SQL_PATH = REPO_ROOT / "docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql"


class AuthOrgProfileGroupSqlStaticContractTest(unittest.TestCase):
    """防止 AUTH 手工 SQL 的已知 MySQL 元数据兼容问题回归。"""

    @classmethod
    def setUpClass(cls) -> None:
        cls.sql = SQL_PATH.read_text(encoding="utf-8")

    def test_resource_comparison_operands_and_temp_table_use_legacy_target_collation(self) -> None:
        """派生资源值和临时资源表须与既有 PT_RESOURCE 使用同一排序规则。"""
        required_derived_operands = (
            "existing_resource.RESOURCE_ID = expected_resource.resource_id COLLATE utf8mb4_general_ci",
            "existing_resource.RESOURCE_URL <=> expected_resource.resource_url COLLATE utf8mb4_general_ci",
            "existing_resource.RESOURCE_METHOD <=> expected_resource.resource_method COLLATE utf8mb4_general_ci",
            "existing_resource.SYS_CODE <=> expected_resource.sys_code COLLATE utf8mb4_general_ci",
            "existing_resource.RESOURCE_URL = expected_resource.resource_url COLLATE utf8mb4_general_ci",
            "existing_resource.RESOURCE_METHOD = expected_resource.resource_method COLLATE utf8mb4_general_ci",
            "existing_resource.SYS_CODE = expected_resource.sys_code COLLATE utf8mb4_general_ci",
            "existing_resource.RESOURCE_ID <> expected_resource.resource_id COLLATE utf8mb4_general_ci",
        )
        for operand in required_derived_operands:
            with self.subTest(operand=operand):
                self.assertEqual(
                    2,
                    self.sql.count(operand),
                    f"派生资源比较缺少显式 general_ci：{operand}",
                )

        temporary_table_start = self.sql.index(
            "CREATE TEMPORARY TABLE tmp_auth_org_resources_20260811"
        )
        temporary_table_end = self.sql.index(
            "SET v_temp_resource_table_created = 1;", temporary_table_start
        )
        temporary_table_ddl = self.sql[temporary_table_start:temporary_table_end]
        self.assertTrue(
            "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;"
            in temporary_table_ddl,
            "临时资源表必须显式使用 utf8mb4_general_ci",
        )

    def test_role_id_local_variable_uses_legacy_target_collation(self) -> None:
        """新角色 ID 变量与既有 PT_ROLE.ROLE_ID 比较时不得继承数据库默认排序规则。"""
        declaration = (
            "DECLARE v_candidate_role_id VARCHAR(50) "
            "CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
        )
        self.assertTrue(
            declaration in self.sql,
            "v_candidate_role_id 必须显式使用 utf8mb4_general_ci",
        )

    def test_structured_audit_text_columns_match_mysql_text_metadata_in_both_guards(self) -> None:
        """TEXT 的 INFORMATION_SCHEMA 元数据必须允许脚本自身创建后的合法部分状态重跑。"""
        text_columns = (
            "before_snapshot",
            "after_snapshot",
            "added_items",
            "removed_items",
        )
        for column_name in text_columns:
            with self.subTest(column_name=column_name):
                self.assertIn(
                    f"UNION ALL SELECT '{column_name}', 'text', 'text', 65535, 'YES', NULL, ''",
                    self.sql,
                    "零 DDL guard 必须使用 MySQL TEXT 的完整元数据",
                )

        self.assertIn(
            "OR actual_column.column_type <> expected_audit_column.column_type",
            self.sql,
            "零 DDL guard 必须拒绝非 TEXT 的 column_type",
        )
        self.assertIn(
            "OR actual_column.extra <> expected_audit_column.extra",
            self.sql,
            "零 DDL guard 必须拒绝 TEXT 的错误 extra",
        )
        self.assertIn(
            "OR NOT (actual_column.column_default <=> expected_audit_column.column_default)",
            self.sql,
            "零 DDL guard 必须拒绝 TEXT 的错误默认值",
        )

        process_guard = (
            "AND data_type = 'text' AND column_type = 'text' "
            "AND character_maximum_length = 65535 AND is_nullable = 'YES'\n"
            "           AND column_default IS NULL AND extra = '';"
        )
        self.assertEqual(
            4,
            self.sql.count(process_guard),
            "过程内四个 TEXT guard 必须同步校验 data_type/column_type/长度/可空/默认值/extra",
        )


if __name__ == "__main__":
    unittest.main()
