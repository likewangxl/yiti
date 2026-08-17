#!/usr/bin/env python3
"""大屏范围管理菜单登记 SQL 的静态契约。"""

from __future__ import annotations

from pathlib import Path
import re
import unittest


THIS_FILE = Path(__file__).resolve()
REPO_ROOT = THIS_FILE.parents[4]
SQL_PATH = REPO_ROOT / "docs/superpowers/sql/2026-08-13-register-screen-scope-admin-menus.sql"


class ScreenScopeAdminMenuSqlTest(unittest.TestCase):
    """保证交付脚本只含可直接执行的菜单登记 DML。"""

    @classmethod
    def setUpClass(cls) -> None:
        cls.sql = SQL_PATH.read_text(encoding="utf-8")
        cls.executable_sql = re.sub(r"(?m)^\s*--.*$", "", cls.sql)

    def test_registers_both_report_menu_items_with_stable_identity(self) -> None:
        expected_fragments = (
            "'M_RPT_SCR_PROF', '/screen-admin/org-profiles', 'MENU', '机构经营画像', NULL, 9, 1, '1', 'M_GROUP_REPORT', 0, 'YITI'",
            "'M_RPT_SCR_GRP', '/screen-admin/org-groups', 'MENU', '命名机构组', NULL, 10, 1, '1', 'M_GROUP_REPORT', 0, 'YITI'",
        )
        for fragment in expected_fragments:
            with self.subTest(fragment=fragment):
                self.assertIn(fragment, self.sql)

    def test_grants_both_menu_items_to_enabled_system_admin_role(self) -> None:
        self.assertIn("ROLE_CODE = 'SYS_ADMIN'", self.sql)
        self.assertIn("RECORD_STATUS = 0", self.sql)
        self.assertIn("'M_RPT_SCR_PROF'", self.sql)
        self.assertIn("'M_RPT_SCR_GRP'", self.sql)

    def test_contains_only_direct_dml_and_no_checker_sql(self) -> None:
        statements = [part.strip() for part in self.executable_sql.split(";") if part.strip()]
        for statement in statements:
            with self.subTest(statement=statement[:80]):
                self.assertRegex(
                    statement.upper(),
                    r"^(START TRANSACTION|INSERT(?: IGNORE)? INTO|UPDATE|COMMIT)\b",
                    "交付 SQL 不得包含独立检查、过程或 DDL 语句",
                )

        forbidden = (
            "INFORMATION_SCHEMA",
            "CREATE PROCEDURE",
            "DROP PROCEDURE",
            "CALL ",
            "SIGNAL SQLSTATE",
            "SHOW ",
            "PRECHECK",
            "PREFLIGHT",
        )
        upper_sql = self.executable_sql.upper()
        for token in forbidden:
            with self.subTest(token=token):
                self.assertNotIn(token, upper_sql)

        self.assertNotIn("= VALUES(", upper_sql, "不得交付 MySQL 已弃用的 VALUES(col) 写法")
        self.assertNotIn("INSERT IGNORE", upper_sql, "幂等复跑不得依赖降级为 warning 的重复键忽略")


if __name__ == "__main__":
    unittest.main()
