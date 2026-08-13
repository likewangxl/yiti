#!/usr/bin/env python3
"""REPORT 屏级角色白名单首次部署预检的静态契约。

本测试只读取 SQL 文本，不连接数据库、不执行 SQL，也不修改任何目标库。
"""

from __future__ import annotations

from pathlib import Path
import re
import unittest


THIS_FILE = Path(__file__).resolve()
REPO_ROOT = THIS_FILE.parents[4]
SQL_PATH = REPO_ROOT / "docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql"


class ScreenScopeMapAlignSqlPreflightTest(unittest.TestCase):
    """防止首次部署缺表时外置只读预检直接引用尚未创建的白名单表。"""

    @classmethod
    def setUpClass(cls) -> None:
        cls.sql = SQL_PATH.read_text(encoding="utf-8")

    def test_optional_access_role_table_is_not_resolved_before_procedure_and_existing_duplicates_still_stop(self) -> None:
        """首次部署预检只读元数据；已存在表的重复校验留在过程内 EXISTS 分支。"""
        display_preflight = self.sql[
            self.sql.index("-- 2) 任何返回行即失败") : self.sql.index("-- 3) 资源、角色、角色资源身份/长度/唯一性")
        ]
        self.assertNotRegex(
            display_preflight,
            r"\bFROM\s+RPT_SCREEN_ACCESS_ROLE\b",
            "首次部署时 RPT_SCREEN_ACCESS_ROLE 尚不存在，外置展示查询不得直接读取该表",
        )

        pre_procedure = self.sql[: self.sql.index("DROP PROCEDURE IF EXISTS sp_screen_scope_map_align_20260811")]
        self.assertNotRegex(
            pre_procedure,
            r"\bFROM\s+RPT_SCREEN_ACCESS_ROLE\b",
            "首次部署前的顶层预检不得解析尚未创建的白名单表",
        )

        process = self.sql[
            self.sql.index("CREATE PROCEDURE sp_screen_scope_map_align_20260811()") : self.sql.index(
                "CALL sp_screen_scope_map_align_20260811();"
            )
        ]
        self.assertRegex(
            process,
            re.compile(
                r"IF\s+EXISTS\s*\(\s*SELECT\s+1\s+FROM\s+information_schema\.tables"
                r".*?table_name\s*=\s*'RPT_SCREEN_ACCESS_ROLE'.*?\)\s+THEN"
                r".*?SELECT\s+COUNT\(\*\)\s+INTO\s+v_count\s+FROM\s*\("
                r"\s*SELECT\s+screen_id,\s*role_code\s+FROM\s+RPT_SCREEN_ACCESS_ROLE"
                r"\s+GROUP\s+BY\s+screen_id,\s*role_code\s+HAVING\s+COUNT\(\*\)\s*>\s*1"
                r"\s*\)\s+AS\s+duplicated_access_role",
                re.DOTALL,
            ),
            "白名单表已存在时，过程内仍必须检查重复屏角色",
        )

    def test_resource_identity_comparisons_use_legacy_general_ci(self) -> None:
        """派生资源值、临时表和局部字符串变量须与既有 auth 列的 general_ci 一致。"""
        resource_blocks = (
            self.sql[
                self.sql.index("WITH expected_resource AS (") : self.sql.index(
                    "-- 4) 机器硬停止门"
                )
            ],
            self.sql[
                self.sql.index("WHEN EXISTS (SELECT 1 FROM PT_RESOURCE p JOIN (") : self.sql.index(
                    "THEN 'REPORT align zero-DDL preflight: 待写资源双向身份冲突'"
                )
            ],
            self.sql[
                self.sql.index("SELECT COUNT(*) INTO v_count FROM PT_RESOURCE p JOIN (") : self.sql.index(
                    "-- ROLE_CODE 是跨模块业务身份"
                )
            ],
        )
        required_operands = (
            "resource_id COLLATE utf8mb4_general_ci",
            "resource_url COLLATE utf8mb4_general_ci",
            "resource_method COLLATE utf8mb4_general_ci",
            "sys_code COLLATE utf8mb4_general_ci",
        )
        for block_number, resource_block in enumerate(resource_blocks, start=1):
            for operand in required_operands:
                with self.subTest(block=block_number, operand=operand):
                    self.assertIn(
                        operand,
                        resource_block,
                        "派生资源值与 PT_RESOURCE 比较时必须显式采用 utf8mb4_general_ci",
                    )

        self.assertIn(
            "DECLARE v_columns VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;",
            self.sql,
            "过程局部 VARCHAR 不得继承 MySQL 8 默认 0900 排序规则",
        )
        temporary_table_start = self.sql.index(
            "CREATE TEMPORARY TABLE tmp_rpt_scope_resources_20260811"
        )
        temporary_table_end = self.sql.index(
            "INSERT INTO tmp_rpt_scope_resources_20260811 VALUES", temporary_table_start
        )
        temporary_table_ddl = self.sql[temporary_table_start:temporary_table_end]
        self.assertIn(
            "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;",
            temporary_table_ddl,
            "临时资源表必须显式沿用既有 PT_RESOURCE 的 utf8mb4_general_ci",
        )


if __name__ == "__main__":
    unittest.main()
