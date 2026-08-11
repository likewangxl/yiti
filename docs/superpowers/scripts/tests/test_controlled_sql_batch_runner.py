#!/usr/bin/env python3
"""第九轮受控 SQL runner 信任边界单元测试。

本测试只 import runner 并调用纯函数/模拟边界；不会启动 mysql、openssl、bash、socket 或数据库。
任何需要外部信任根的执行路径均通过 mock 验证 fail-close 顺序，不能成为生产 CLI 的绕过入口。
"""

from __future__ import annotations

from datetime import datetime, timezone
import importlib.util
import inspect
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


THIS_FILE = Path(__file__).resolve()
REPO_ROOT = THIS_FILE.parents[4]
RUNNER_SHELL = REPO_ROOT / "docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.sh"
RUNNER_PYTHON = REPO_ROOT / "docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.py"


def import_runner():
    """从源码路径导入，避免依赖 PATH 或安装包。"""
    module_name = "controlled_sql_batch_runner_round9_test_subject"
    spec = importlib.util.spec_from_file_location(module_name, RUNNER_PYTHON)
    if spec is None or spec.loader is None:
        raise AssertionError("无法加载受测 runner")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


class ControlledSqlBatchRunnerTrustTest(unittest.TestCase):
    """所有测试均为无网络、无数据库、无 socket 的纯单元测试。"""

    @classmethod
    def setUpClass(cls):
        if not RUNNER_SHELL.is_file() or not RUNNER_PYTHON.is_file():
            raise AssertionError("缺少受测 runner 产物")
        cls.runner = import_runner()

    def approved_manifest(self, sql_batches: tuple[bytes, ...] | None = None):
        """构造已完成前置验签后的最小执行视图，不包含任何凭据。"""
        return self.runner.ApprovedManifest(
            manifest_sha256="a" * 64,
            change_ticket="CHG-ROUND9-TEST",
            not_before="2030-01-01T00:00:00Z",
            expiry="2030-01-01T00:10:00Z",
            execution_nonce="b" * 32,
            connect_host="isolated-mysql.example.test",
            connect_port=3306,
            server_uuid="11111111-2222-3333-4444-555555555555",
            server_hostname="isolated-mysql-01",
            schema="yiti_isolated",
            recovery_reference="A2-ROUND9-RECOVERY",
            recovery_sha256="c" * 64,
            recovery_signed_proof_sha256="d" * 64,
            receipt_id="22222222-3333-4444-5555-666666666666",
            sql_batches=sql_batches or (b"SELECT 'first';\n", b"SELECT 'second';\n", b"SELECT 'third';\n"),
        )

    def receipt_claims(self, manifest):
        """构造外部审批服务已签名收据的 claims；签名本身另由边界函数验证。"""
        return {
            "receipt_version": 1,
            "approval_service": self.runner.APPROVAL_SERVICE_ID,
            "receipt_id": manifest.receipt_id,
            "manifest_sha256": manifest.manifest_sha256,
            "change_ticket": manifest.change_ticket,
            "issued_at_utc": "2029-12-31T23:59:30Z",
            "not_before_utc": "2030-01-01T00:00:00Z",
            "expires_at_utc": "2030-01-01T00:05:00Z",
            "execution_nonce": manifest.execution_nonce,
            "nonce_policy": "single_use",
            "revocation_epoch": "REV-20291231T235900Z",
            "a2_recovery_point": {
                "reference": manifest.recovery_reference,
                "sha256": manifest.recovery_sha256,
                "signed_proof_sha256": manifest.recovery_signed_proof_sha256,
            },
        }

    def test_shell_uses_fixed_absolute_interpreters_and_removes_double_run(self):
        shell = RUNNER_SHELL.read_text(encoding="utf-8")
        self.assertEqual("#!/usr/bin/bash", shell.splitlines()[0])
        self.assertIn("exec /usr/bin/python3.10", shell)
        self.assertNotIn("#!/usr/bin/env", shell)
        self.assertNotIn("--double-run", shell)

    def test_runner_never_selects_security_tools_from_path(self):
        source = RUNNER_PYTHON.read_text(encoding="utf-8")
        self.assertTrue("shutil.which" not in source, "runner 不得从 PATH 选择工具")
        for token in ("OPENSSL_PATH", "MYSQL_PATH", "BASH_PATH", "PYTHON_PATH", "ToolSpec", "expected_sha256", "expected_version"):
            self.assertTrue(token in source, f"runner 缺少固定工具信任契约：{token}")

    def test_strict_json_rejects_duplicate_keys_before_schema_validation(self):
        with self.assertRaises(self.runner.ValidationError):
            self.runner.strict_json_loads(b'{"schema_version": 2, "schema_version": 1}', "manifest")
        with self.assertRaises(self.runner.ValidationError):
            self.runner.strict_json_loads(b'{"target": {"schema": "a", "schema": "b"}}', "manifest")

    def test_schema_validator_rejects_boolean_for_integer(self):
        schema = {
            "type": "object",
            "additionalProperties": False,
            "required": ["schema_version"],
            "properties": {"schema_version": {"type": "integer", "const": 2}},
        }
        with self.assertRaises(self.runner.ValidationError):
            self.runner.validate_against_schema({"schema_version": True}, schema, schema, "manifest")
        self.runner.validate_against_schema({"schema_version": 2}, schema, schema, "manifest")

    def test_manifest_schema_is_loaded_and_executed_not_merely_documented(self):
        source = inspect.getsource(self.runner)
        for token in ("MANIFEST_SCHEMA_RELATIVE", "load_schema_contract", "validate_against_schema", "manifest_schema"):
            self.assertTrue(token in source, f"runner 未实际执行 schema 契约：{token}")

    def test_defaults_parser_allows_only_minimal_client_tls_contract(self):
        raw = (
            b"[client]\n"
            b"user=controlled_runner\n"
            b"password=not-to-be-logged\n"
            b"ssl-mode=VERIFY_IDENTITY\n"
            b"ssl-ca=/opt/bank/controlled-sql-batch-runner/release/trust/mysql-ca.pem\n"
        )
        parsed = self.runner.parse_mysql_defaults_bytes(raw)
        self.assertEqual({"user", "password", "ssl-mode", "ssl-ca"}, set(parsed))
        self.assertEqual("VERIFY_IDENTITY", parsed["ssl-mode"])

    def test_defaults_parser_rejects_injection_groups_and_unknown_options_without_secret_echo(self):
        base = (
            b"[client]\nuser=controlled_runner\npassword=not-to-be-logged\n"
            b"ssl-mode=VERIFY_IDENTITY\n"
            b"ssl-ca=/opt/bank/controlled-sql-batch-runner/release/trust/mysql-ca.pem\n"
        )
        attacks = (
            base + b"init-command=SELECT 1\n",
            base + b"execute=SELECT 1\n",
            base + b"host=attacker.example.test\n",
            base + b"port=3307\n",
            base + b"database=other\n",
            base + b"reconnect=1\n",
            base + b"force=1\n",
            base + b"login-path=operator\n",
            base + b"unknown-option=yes\n",
            base + b"[mysql]\nuser=attacker\n",
            b"!include /tmp/attacker.cnf\n" + base,
            b"[client]\nuser=one\nuser=two\npassword=not-to-be-logged\nssl-mode=VERIFY_IDENTITY\n"
            b"ssl-ca=/opt/bank/controlled-sql-batch-runner/release/trust/mysql-ca.pem\n",
        )
        for raw in attacks:
            with self.subTest(raw=raw.splitlines()[-1]):
                with self.assertRaises(self.runner.ValidationError) as captured:
                    self.runner.parse_mysql_defaults_bytes(raw)
                self.assertNotIn("not-to-be-logged", str(captured.exception))

    def test_managed_manifest_path_rejects_operator_selected_locations(self):
        with self.assertRaises(self.runner.ValidationError):
            self.runner.require_managed_manifest_path(Path("relative.json"))
        with self.assertRaises(self.runner.ValidationError):
            self.runner.require_managed_manifest_path(Path("/tmp/operator.json"))

    def test_secure_reader_rejects_symlink_without_socket_or_subprocess(self):
        with tempfile.TemporaryDirectory(prefix="runner-round9-symlink-") as directory:
            root = Path(directory)
            target = root / "target.json"
            target.write_bytes(b"{}")
            link = root / "manifest.json"
            link.symlink_to(target)
            # 此 mock 只绕开临时目录的 owner/mode，不影响 O_NOFOLLOW 的真实内核语义。
            with mock.patch.object(self.runner, "verify_trusted_metadata", return_value=None):
                with self.assertRaises(self.runner.ValidationError):
                    self.runner.read_trusted_file_bytes(link, "test symlink")

    def test_secure_reader_declares_descriptor_checks_and_no_resolve_reopen(self):
        source = inspect.getsource(self.runner)
        for token in ("os.O_NOFOLLOW", "os.fstat", "os.open"):
            self.assertTrue(token in source, f"安全读取缺少 {token}")
        self.assertTrue(".resolve()" not in source, "不得 resolve 后重新按路径打开")

    def test_mysql_stdin_contains_exactly_two_byte_preserved_rounds_without_source(self):
        manifest = self.approved_manifest()
        stdin = self.runner.build_mysql_stdin(manifest)
        self.assertIsInstance(stdin, bytes)
        self.assertNotIn(b"SOURCE ", stdin.upper())
        for batch in manifest.sql_batches:
            self.assertEqual(2, stdin.count(batch))
        self.assertLess(stdin.find(manifest.sql_batches[0]), stdin.find(manifest.sql_batches[1]))
        self.assertLess(stdin.find(manifest.sql_batches[1]), stdin.find(manifest.sql_batches[2]))

    def test_mysql_command_uses_only_fixed_defaults_file_and_tcp_tls(self):
        manifest = self.approved_manifest()
        command = self.runner.build_mysql_command(manifest, self.runner.DEFAULTS_FILE)
        self.assertEqual(str(self.runner.MYSQL_PATH), command[0])
        self.assertEqual(f"--defaults-file={self.runner.DEFAULTS_FILE}", command[1])
        for argument in (
            "--protocol=TCP",
            "--ssl-mode=VERIFY_IDENTITY",
            f"--ssl-ca={self.runner.MYSQL_CA_FILE}",
            "--batch",
            "--skip-reconnect",
        ):
            self.assertIn(argument, command)
        for forbidden in ("--no-defaults", "--no-login-paths", "--defaults-extra-file"):
            self.assertFalse(any(item == forbidden or item.startswith(f"{forbidden}=") for item in command))
        self.assertFalse(any("password" in item.lower() for item in command))
        self.assertFalse(any(item == "--force" or item.startswith("--force=") for item in command))

    def test_mysql_login_path_isolation_uses_fixed_empty_account_home_and_clean_child_environment(self):
        environment = self.runner.MYSQL_EXECUTION_ENV
        self.assertEqual(str(self.runner.EMPTY_EXECUTOR_HOME), environment["HOME"])
        self.assertNotIn("MYSQL_HOME", environment)
        self.assertNotIn("MYSQL_TEST_LOGIN_FILE", environment)
        self.assertNotIn("HOME", {key for key in environment if key != "HOME"})
        source = inspect.getsource(self.runner)
        self.assertIn("pwd.getpwuid", source)
        self.assertIn("require_login_path_isolation", source)

    def test_receipt_requires_short_window_nonce_and_a2_signed_proof_binding(self):
        manifest = self.approved_manifest()
        now = datetime(2030, 1, 1, 0, 1, tzinfo=timezone.utc)
        claims = self.receipt_claims(manifest)
        self.runner.validate_receipt_claims(claims, manifest, now)

        expired = dict(claims)
        expired["expires_at_utc"] = "2030-01-01T00:00:30Z"
        with self.assertRaises(self.runner.ValidationError):
            self.runner.validate_receipt_claims(expired, manifest, now)

        mismatch = dict(claims)
        mismatch["a2_recovery_point"] = dict(claims["a2_recovery_point"])
        mismatch["a2_recovery_point"]["signed_proof_sha256"] = "e" * 64
        with self.assertRaises(self.runner.ValidationError):
            self.runner.validate_receipt_claims(mismatch, manifest, now)

        long_window = dict(claims)
        long_window["expires_at_utc"] = "2030-01-01T02:00:00Z"
        with self.assertRaises(self.runner.ValidationError):
            self.runner.validate_receipt_claims(long_window, manifest, now)

    def test_manifest_window_rejects_future_expired_and_long_lived_authorizations(self):
        now = datetime(2030, 1, 1, 0, 1, tzinfo=timezone.utc)
        self.runner.validate_time_window(
            "2030-01-01T00:00:00Z", "2030-01-01T00:05:00Z", "manifest", 900, now
        )
        for start, end in (
            ("2030-01-01T00:02:00Z", "2030-01-01T00:04:00Z"),
            ("2029-12-31T23:00:00Z", "2030-01-01T00:00:00Z"),
            ("2030-01-01T00:00:00Z", "2030-01-01T01:00:00Z"),
        ):
            with self.subTest(start=start, end=end):
                with self.assertRaises(self.runner.ValidationError):
                    self.runner.validate_time_window(start, end, "manifest", 900, now)

    def test_execution_never_reaches_mysql_when_trust_or_receipt_boundary_fails(self):
        manifest = self.approved_manifest()
        with mock.patch.object(
            self.runner,
            "require_execution_environment",
            side_effect=self.runner.ValidationError("deployment trust root missing"),
        ):
            with mock.patch.object(self.runner, "execute_mysql_once") as mysql:
                with self.assertRaises(self.runner.ValidationError):
                    self.runner.execute_approved_manifest(manifest)
                mysql.assert_not_called()

        with mock.patch.object(self.runner, "require_execution_environment", return_value=None):
            with mock.patch.object(
                self.runner,
                "validate_execution_receipt",
                side_effect=self.runner.ValidationError("receipt unavailable"),
            ):
                with mock.patch.object(self.runner, "execute_mysql_once") as mysql:
                    with self.assertRaises(self.runner.ValidationError):
                        self.runner.execute_approved_manifest(manifest)
                    mysql.assert_not_called()

    def test_validate_only_never_reaches_execution_or_mysql(self):
        manifest = self.approved_manifest()
        with mock.patch.object(self.runner, "load_and_validate_manifest", return_value=manifest):
            with mock.patch.object(self.runner, "run_static_guard_checker", return_value=None):
                with mock.patch.object(self.runner, "execute_approved_manifest") as execute:
                    with mock.patch.object(sys, "argv", ["runner", "--manifest", "/ignored", "--validate-only"]):
                        self.assertEqual(0, self.runner.main())
                    execute.assert_not_called()

    def test_fixed_two_rounds_use_exactly_one_mocked_mysql_subprocess_and_never_log_password(self):
        manifest = self.approved_manifest()
        with mock.patch.object(self.runner, "require_login_path_isolation", return_value=None):
            with mock.patch.object(self.runner, "verify_mysql_option_contract", return_value=None):
                with mock.patch.object(self.runner, "validate_defaults_file", return_value=self.runner.DEFAULTS_FILE):
                    with mock.patch.object(
                        self.runner.subprocess,
                        "run",
                        return_value=SimpleNamespace(returncode=0),
                    ) as child:
                        self.assertEqual(0, self.runner.execute_mysql_once(manifest))
        child.assert_called_once()
        command = child.call_args.args[0]
        stdin = child.call_args.kwargs["input"]
        self.assertEqual(self.runner.build_mysql_stdin(manifest), stdin)
        self.assertFalse(any("password" in item.lower() for item in command))
        self.assertNotIn(b"not-to-be-logged", stdin)

    def test_reader_fails_when_fstat_changes_during_the_single_read(self):
        with tempfile.TemporaryDirectory(prefix="runner-round9-toctou-") as directory:
            target = Path(directory) / "manifest.json"
            target.write_bytes(b"{}")
            target_stat = target.stat()
            original_fstat = self.runner.os.fstat
            target_calls = 0

            def changed_fstat(descriptor):
                nonlocal target_calls
                current = original_fstat(descriptor)
                if current.st_ino == target_stat.st_ino and current.st_dev == target_stat.st_dev:
                    target_calls += 1
                    mtime = current.st_mtime_ns + (1 if target_calls >= 3 else 0)
                    return SimpleNamespace(
                        st_dev=current.st_dev,
                        st_ino=current.st_ino,
                        st_size=current.st_size,
                        st_mtime_ns=mtime,
                        st_ctime_ns=current.st_ctime_ns,
                        st_mode=current.st_mode,
                        st_uid=current.st_uid,
                        st_gid=current.st_gid,
                    )
                return current

            with mock.patch.object(self.runner, "verify_trusted_metadata", return_value=None):
                with mock.patch.object(self.runner.os, "fstat", side_effect=changed_fstat):
                    with self.assertRaises(self.runner.ValidationError):
                        self.runner.read_trusted_file_bytes(target, "TOCTOU manifest")

    def test_unprovisioned_key_pin_is_not_a_production_test_bypass(self):
        with self.assertRaises(self.runner.ValidationError):
            self.runner._require_configured_fingerprint(
                self.runner.UNPROVISIONED_FINGERPRINT,
                "manifest public key",
            )

    def test_signature_verification_uses_opened_fds_and_fixed_openssl(self):
        source = inspect.getsource(self.runner)
        for token in ("OPENSSL_PATH", "pass_fds", "/proc/self/fd/"):
            self.assertTrue(token in source, f"验签边界缺少 {token}")
        self.assertTrue("shutil.which" not in source, "验签不得从 PATH 选择 openssl")

    def test_no_legacy_environment_defaults_or_path_source_sql_remains(self):
        source = RUNNER_PYTHON.read_text(encoding="utf-8")
        self.assertNotIn("SQL_GUARD_MYSQL_DEFAULTS_EXTRA_FILE", source)
        self.assertNotIn("source_directive", source)
        self.assertNotIn("--double-run", source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
