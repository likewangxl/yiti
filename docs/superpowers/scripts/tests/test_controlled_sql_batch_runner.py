#!/usr/bin/env python3
"""第九轮受控 SQL runner 信任边界单元测试。

本测试只 import runner 并调用纯函数/模拟边界；不会启动 mysql、openssl、bash、socket 或数据库。
任何需要外部信任根的执行路径均通过 mock 验证 fail-close 顺序，不能成为生产 CLI 的绕过入口。
"""

from __future__ import annotations

import base64
from contextlib import contextmanager
from datetime import datetime, timezone
import importlib.util
import inspect
import json
from pathlib import Path
import stat
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


THIS_FILE = Path(__file__).resolve()
REPO_ROOT = THIS_FILE.parents[4]
RUNNER_SHELL = REPO_ROOT / "docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.sh"
RUNNER_PYTHON = REPO_ROOT / "docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.py"
ROOT_LAUNCHER = REPO_ROOT / "docs/superpowers/scripts/2026-08-12-controlled-sql-root-managed-launcher.py"
SYSTEMD_UNIT_TEMPLATE = REPO_ROOT / "docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner@.service.template"
CLAIM_SCHEMA = REPO_ROOT / "docs/superpowers/sql/2026-08-12-controlled-sql-receipt-claim-token.schema.json"
A2_PROOF_SCHEMA = REPO_ROOT / "docs/superpowers/sql/2026-08-12-controlled-sql-a2-recovery-proof.schema.json"
CLAIM_FIXTURE = REPO_ROOT / "docs/superpowers/scripts/tests/fixtures/controlled-sql-receipt-claim-token.fixture.json"
A2_PROOF_FIXTURE = REPO_ROOT / "docs/superpowers/scripts/tests/fixtures/controlled-sql-a2-recovery-proof.fixture.json"
MYSQL_RELEASE_CONTRACT = REPO_ROOT / "docs/superpowers/sql/2026-08-12-controlled-sql-mysql-release-contract.json"
MYSQL_RELEASE_CONTRACT_SCHEMA = REPO_ROOT / "docs/superpowers/sql/2026-08-12-controlled-sql-mysql-release-contract.schema.json"
TRUST_README = REPO_ROOT / "docs/superpowers/sql/controlled-batch-manifest-trust/README.md"


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


def import_root_launcher():
    """从源码路径导入 root launcher；不触发 CLI、systemd 或 openssl。"""
    module_name = "controlled_sql_root_managed_launcher_round11_test_subject"
    spec = importlib.util.spec_from_file_location(module_name, ROOT_LAUNCHER)
    if spec is None or spec.loader is None:
        raise AssertionError("无法加载受测 root launcher")
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
            "--binary-mode",
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

    def test_receipt_has_no_static_local_execution_fallback(self):
        source = RUNNER_PYTHON.read_text(encoding="utf-8")
        self.assertNotIn("DEPLOYMENT_RECEIPT_DIRECTORY", source)
        self.assertNotIn("validate_execution_receipt", source)
        self.assertIn("claim_receipt_atomically", source)
        self.assertIn("在线 receipt claim", source)

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

        launcher = SimpleNamespace(proof_sha256="1" * 64, expires_at="2030-01-01T00:05:00Z")
        with mock.patch.object(self.runner, "require_execution_environment", return_value=launcher):
            with mock.patch.object(
                self.runner,
                "claim_receipt_atomically",
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
        with mock.patch.object(self.runner, "require_approved_mysql_binary", return_value=None):
            with mock.patch.object(self.runner, "require_login_path_isolation", return_value=None):
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


class ControlledSqlBatchRunnerRound10Test(unittest.TestCase):
    """第十轮：所有外部边界均 mock，禁止创建 socket、服务或数据库连接。"""

    @classmethod
    def setUpClass(cls):
        cls.runner = import_runner()

    def protocol_manifest(self):
        """构造已完成 manifest 验签后的协议输入，避免测试依赖真实部署根。"""
        return SimpleNamespace(
            manifest_sha256="a" * 64,
            change_ticket="CHG-ROUND10-TEST",
            not_before="2030-01-01T00:00:00Z",
            expiry="2030-01-01T00:10:00Z",
            execution_nonce="b" * 32,
            receipt_id="22222222-3333-4444-5555-666666666666",
            connect_host="isolated-mysql.example.test",
            connect_port=3306,
            server_uuid="11111111-2222-3333-4444-555555555555",
            server_hostname="isolated-mysql-01",
            schema="yiti_isolated",
            recovery_reference="A2-ROUND10-RECOVERY",
            recovery_sha256="c" * 64,
            a2_proof_id="33333333-4444-5555-6666-777777777777",
            a2_proof_sha256="d" * 64,
            claim_helper_sha256="e" * 64,
            sql_batches=(b"SELECT 1;\n",),
            artifact_hashes={
                "runner_python": "f" * 64,
                "root_managed_launcher": "1" * 64,
                "systemd_unit_template": "2" * 64,
            },
            protocol_contracts={
                "receipt_claim": {
                    "$defs": {
                        "claimHelperResponse": {"type": "object"},
                        "claimToken": {"type": "object"},
                    }
                }
            },
        )

    @staticmethod
    def launcher_proof():
        return SimpleNamespace(
            proof_sha256="3" * 64,
            launch_id="11111111-2222-3333-4444-555555555555",
            expires_at="2030-01-01T00:05:00Z",
        )

    def claim_token_claims(self, manifest, launcher):
        return {
            "claim_version": 1,
            "issuer": "bank-controlled-sql-approval-service",
            "claim_id": "44444444-5555-6666-7777-888888888888",
            "receipt_id": manifest.receipt_id,
            "manifest_sha256": manifest.manifest_sha256,
            "change_ticket": manifest.change_ticket,
            "execution_nonce": manifest.execution_nonce,
            "launcher_proof_sha256": launcher.proof_sha256,
            "issued_at_utc": "2030-01-01T00:00:00Z",
            "expires_at_utc": "2030-01-01T00:05:00Z",
        }

    @staticmethod
    def claim_helper_response(token_bytes: bytes) -> bytes:
        return json.dumps(
            {
                "response_version": 1,
                "status": "claimed",
                "claim_token_b64": base64.b64encode(token_bytes).decode("ascii"),
                "claim_signature_b64": base64.b64encode(b"mock-claim-signature").decode("ascii"),
            },
            separators=(",", ":"),
        ).encode("ascii")

    def claimed_receipt(self, manifest, launcher):
        claims = self.claim_token_claims(manifest, launcher)
        raw = json.dumps(claims, sort_keys=True, separators=(",", ":")).encode("ascii")
        return SimpleNamespace(
            claim_id=claims["claim_id"],
            token_sha256=self.runner.sha256_bytes(raw),
            issued_at=claims["issued_at_utc"],
            expiry=claims["expires_at_utc"],
        )

    def a2_proof_claims(self, manifest, claim):
        return {
            "proof_version": 1,
            "issuer": "bank-a2-recovery-attestation-service",
            "proof_id": manifest.a2_proof_id,
            "manifest_sha256": manifest.manifest_sha256,
            "claim_id": claim.claim_id,
            "claim_token_sha256": claim.token_sha256,
            "issued_at_utc": "2030-01-01T00:00:00Z",
            "expires_at_utc": "2030-01-01T00:05:00Z",
            "target_quartet": {
                "server_uuid": manifest.server_uuid,
                "server_hostname": manifest.server_hostname,
                "connect_port": manifest.connect_port,
                "schema": manifest.schema,
            },
            "recovery": {
                "reference": manifest.recovery_reference,
                "state": "VERIFIED",
                "recovery_point_sha256": manifest.recovery_sha256,
            },
        }

    def test_shell_execute_is_permanently_rejected_and_validate_path_is_isolated(self):
        shell = RUNNER_SHELL.read_text(encoding="utf-8")
        execute_arm = shell.split("--execute|--execute=*)", 1)[1].split(";;", 1)[0]
        self.assertIn("die", execute_arm)
        self.assertNotIn("mode='execute'", shell)
        self.assertNotIn('"$DEPLOYED_HELPER" --manifest "$manifest" --execute', shell)
        self.assertIn("exec /usr/bin/python3.10 -I", shell)
        self.assertIn("普通 shell 入口永久拒绝", shell)

    def test_systemd_unit_declares_strict_pre_interpreter_environment_and_service_contract(self):
        self.assertTrue(SYSTEMD_UNIT_TEMPLATE.is_file(), "必须提交 root-managed systemd unit 模板")
        unit = SYSTEMD_UNIT_TEMPLATE.read_text(encoding="utf-8")
        for token in (
            "ExecStart=/usr/bin/python3.10 -I",
            "UnsetEnvironment=BASH_ENV",
            "UnsetEnvironment=ENV",
            "LD_PRELOAD",
            "LD_LIBRARY_PATH",
            "PYTHONPATH",
            "PYTHONHOME",
            "LC_ALL",
            "Environment=HOME=/var/empty/controlled-sql-runner",
            "Environment=PATH=/usr/bin:/bin",
            "NoNewPrivileges=yes",
            "ProtectSystem=strict",
            "ReadOnlyPaths=",
            "ProtectHome=yes",
            "PrivateTmp=yes",
            "User=root",
        ):
            self.assertIn(token, unit, f"unit 缺少严格执行契约：{token}")
        self.assertNotIn("/usr/bin/env ", unit)
        self.assertNotIn("EnvironmentFile=", unit)

    def test_root_launcher_produces_sealed_pid_bound_proof_and_drops_to_no_login_account(self):
        self.assertTrue(ROOT_LAUNCHER.is_file(), "必须提交 root-managed launcher 模板")
        launcher = ROOT_LAUNCHER.read_text(encoding="utf-8")
        for token in (
            "os.memfd_create",
            "F_ADD_SEALS",
            "F_SEAL_WRITE",
            "os.setgroups",
            "os.setgid",
            "os.setuid",
            "os.execve",
            "-I",
            "pid_start_time",
            "SYSTEMD_INVOCATION_ID",
            "bank-controlled-sql-batch-runner@",
            "SERVICE_ACCOUNT",
            "/usr/sbin/nologin",
        ):
            self.assertIn(token, launcher, f"launcher 缺少不可伪造部署证明契约：{token}")
        self.assertNotIn("#!/usr/bin/env", launcher)

    def test_runner_requires_signed_sealed_launcher_proof_not_an_environment_marker(self):
        source = inspect.getsource(self.runner)
        for token in (
            "LAUNCHER_PROOF_FD",
            "LAUNCHER_PROOF_SIGNATURE_FD",
            "F_GET_SEALS",
            "ROOT_LAUNCHER_PROOF_PUBLIC_KEY_RELATIVE",
            "validate_root_launcher_proof_claims",
            "pid_start_time",
            "root-managed launcher",
        ):
            self.assertIn(token, source, f"runner 缺少 launcher proof 边界：{token}")
        self.assertNotIn("LAUNCHER_PROOF_ENV", source)

    def test_new_protocol_schemas_and_nonexecutable_fixtures_are_checked_in(self):
        for path, expected_id in (
            (CLAIM_SCHEMA, "controlled-sql-receipt-claim-token-v1"),
            (A2_PROOF_SCHEMA, "controlled-sql-a2-recovery-proof-v1"),
        ):
            self.assertTrue(path.is_file(), f"缺少协议 schema：{path.name}")
            parsed = json.loads(path.read_text(encoding="utf-8"))
            self.assertIn(expected_id, parsed.get("$id", ""))
            self.assertFalse(parsed.get("additionalProperties", True))
        for fixture in (CLAIM_FIXTURE, A2_PROOF_FIXTURE):
            self.assertTrue(fixture.is_file(), f"缺少协议 fixture：{fixture.name}")
            self.assertIsInstance(json.loads(fixture.read_text(encoding="utf-8")), dict)

    def test_claim_helper_success_is_signed_and_bound_to_launcher_and_manifest(self):
        manifest = self.protocol_manifest()
        launcher = self.launcher_proof()
        token = json.dumps(self.claim_token_claims(manifest, launcher), separators=(",", ":")).encode("ascii")
        response = self.claim_helper_response(token)
        now = datetime(2030, 1, 1, 0, 1, tzinfo=timezone.utc)
        with mock.patch.object(self.runner, "require_claim_helper_contract", return_value=None):
            with mock.patch.object(self.runner, "verify_claim_token_signature", return_value=None):
                with mock.patch.object(
                    self.runner.subprocess,
                    "run",
                    return_value=SimpleNamespace(returncode=0, stdout=response, stderr=b""),
                ) as child:
                    claim = self.runner.claim_receipt_atomically(manifest, launcher, now=now)
        self.assertEqual("44444444-5555-6666-7777-888888888888", claim.claim_id)
        command = child.call_args.args[0]
        self.assertEqual(str(self.runner.CLAIM_HELPER_PATH), command[0])
        self.assertIn("--claim-once", command)
        self.assertNotIn(b"password", child.call_args.kwargs["input"].lower())

    def test_claim_helper_consumed_revoked_or_offline_reply_is_fail_closed_without_echo(self):
        manifest = self.protocol_manifest()
        launcher = self.launcher_proof()
        now = datetime(2030, 1, 1, 0, 1, tzinfo=timezone.utc)
        for label, return_code in (("already-consumed", 75), ("revoked", 76), ("offline", 1)):
            with self.subTest(label=label):
                with mock.patch.object(self.runner, "require_claim_helper_contract", return_value=None):
                    with mock.patch.object(
                        self.runner.subprocess,
                        "run",
                        return_value=SimpleNamespace(
                            returncode=return_code,
                            stdout=b"operator-secret-must-not-echo",
                            stderr=b"operator-secret-must-not-echo",
                        ),
                    ):
                        with self.assertRaises(self.runner.ValidationError) as captured:
                            self.runner.claim_receipt_atomically(manifest, launcher, now=now)
                self.assertNotIn("operator-secret-must-not-echo", str(captured.exception))

    def test_claim_token_rejects_duplicate_replay_style_fields_and_wrong_launcher_binding(self):
        manifest = self.protocol_manifest()
        launcher = self.launcher_proof()
        now = datetime(2030, 1, 1, 0, 1, tzinfo=timezone.utc)
        with self.assertRaises(self.runner.ValidationError):
            self.runner.strict_json_loads(
                b'{"claim_version":1,"claim_version":1}',
                "receipt claim token",
            )
        claims = self.claim_token_claims(manifest, launcher)
        self.runner.validate_claim_token_claims(claims, manifest, launcher, now=now)
        replay = dict(claims)
        replay["launcher_proof_sha256"] = "9" * 64
        with self.assertRaises(self.runner.ValidationError):
            self.runner.validate_claim_token_claims(replay, manifest, launcher, now=now)

    def test_a2_proof_rejects_duplicate_keys_target_state_and_claim_manifest_bind_failures(self):
        manifest = self.protocol_manifest()
        launcher = self.launcher_proof()
        claim = self.claimed_receipt(manifest, launcher)
        now = datetime(2030, 1, 1, 0, 1, tzinfo=timezone.utc)
        with self.assertRaises(self.runner.ValidationError):
            self.runner.strict_json_loads(b'{"proof_version":1,"proof_version":1}', "A2 proof")
        proof = self.a2_proof_claims(manifest, claim)
        self.runner.validate_a2_proof_claims(proof, manifest, claim, now=now)
        for mutate in (
            lambda item: item["target_quartet"].__setitem__("connect_port", 3307),
            lambda item: item["recovery"].__setitem__("state", "PENDING"),
            lambda item: item.__setitem__("claim_id", "55555555-6666-7777-8888-999999999999"),
            lambda item: item.__setitem__("manifest_sha256", "8" * 64),
        ):
            changed = json.loads(json.dumps(proof))
            mutate(changed)
            with self.assertRaises(self.runner.ValidationError):
                self.runner.validate_a2_proof_claims(changed, manifest, claim, now=now)

    def test_a2_signature_boundary_fails_before_mysql(self):
        manifest = self.protocol_manifest()
        launcher = self.launcher_proof()
        claim = self.claimed_receipt(manifest, launcher)
        with mock.patch.object(self.runner, "require_execution_environment", return_value=launcher):
            with mock.patch.object(self.runner, "claim_receipt_atomically", return_value=claim):
                with mock.patch.object(
                    self.runner,
                    "load_and_validate_a2_recovery_proof",
                    side_effect=self.runner.ValidationError("A2 detached signature invalid"),
                ):
                    with mock.patch.object(self.runner, "execute_mysql_once") as mysql:
                        with self.assertRaises(self.runner.ValidationError):
                            self.runner.execute_approved_manifest(manifest)
                        mysql.assert_not_called()

    def test_a2_loader_declares_same_fd_strict_schema_and_independent_pin(self):
        source = inspect.getsource(self.runner)
        for token in (
            "A2_PROOF_PUBLIC_KEY_RELATIVE",
            "PINNED_A2_PROOF_PUBLIC_KEY_SHA256",
            "A2_PROOF_DIRECTORY",
            "load_and_validate_a2_recovery_proof",
            "strict_json_loads(proof_file.data",
            "verify_detached_signature(proof_file.data",
            "validate_a2_proof_claims",
        ):
            self.assertIn(token, source, f"A2 proof 缺少受信边界：{token}")

    def test_mysql_runtime_contract_is_signed_release_data_not_version_or_help_probe(self):
        source = RUNNER_PYTHON.read_text(encoding="utf-8")
        for token in (
            "MYSQL_RELEASE_CONTRACT_RELATIVE",
            "require_approved_mysql_binary",
            "mysql_client_release_contract",
            "MYSQL_FIXED_OPTION_CONTRACT",
            "sbom_sha256",
            "manpage_sha256",
        ):
            self.assertIn(token, source, f"mysql release 契约缺少：{token}")
        self.assertNotIn("verify_mysql_option_contract", source)
        self.assertNotIn("MYSQL_TOOL =", source)

    def test_complete_execute_mock_sequence_allows_only_checker_claim_and_one_mysql(self):
        manifest = self.protocol_manifest()
        launcher = self.launcher_proof()
        claim_token = json.dumps(self.claim_token_claims(manifest, launcher), separators=(",", ":")).encode("ascii")
        claim_response = self.claim_helper_response(claim_token)
        commands = []

        def child(command, **_kwargs):
            commands.append(command)
            return SimpleNamespace(returncode=0, stdout=claim_response, stderr=b"")

        with mock.patch.object(self.runner, "require_approved_binary", return_value=None):
            with mock.patch.object(self.runner, "require_claim_helper_contract", return_value=None):
                with mock.patch.object(self.runner, "verify_claim_token_signature", return_value=None):
                    with mock.patch.object(self.runner, "validate_claim_token_claims", return_value=SimpleNamespace()):
                        with mock.patch.object(self.runner, "require_execution_environment", return_value=launcher):
                            with mock.patch.object(self.runner, "load_and_validate_a2_recovery_proof", return_value=SimpleNamespace()):
                                with mock.patch.object(self.runner, "require_approved_mysql_binary", return_value=None):
                                    with mock.patch.object(self.runner, "require_login_path_isolation", return_value=None):
                                        with mock.patch.object(
                                            self.runner,
                                            "validate_defaults_file",
                                            return_value=self.runner.DEFAULTS_FILE,
                                        ):
                                            with mock.patch.object(self.runner.subprocess, "run", side_effect=child):
                                                self.runner.run_static_guard_checker()
                                                self.assertEqual(0, self.runner.execute_approved_manifest(manifest))

        self.assertEqual(
            [
                str(self.runner.PYTHON_PATH),
                str(self.runner.CLAIM_HELPER_PATH),
                str(self.runner.MYSQL_PATH),
            ],
            [command[0] for command in commands],
        )
        flattened = [argument for command in commands for argument in command]
        self.assertFalse(any(argument in {"--version", "--help"} for argument in flattened))
        mysql_commands = [command for command in commands if command[0] == str(self.runner.MYSQL_PATH)]
        self.assertEqual(1, len(mysql_commands))
        self.assertIn("--binary-mode", mysql_commands[0])

    def test_missing_root_launcher_deployment_proof_fails_closed_before_claim_or_mysql(self):
        manifest = self.protocol_manifest()
        with mock.patch.object(self.runner, "claim_receipt_atomically") as claim:
            with mock.patch.object(self.runner, "execute_mysql_once") as mysql:
                with self.assertRaises(self.runner.ValidationError):
                    self.runner.require_execution_environment(manifest)
                claim.assert_not_called()
                mysql.assert_not_called()


class ControlledSqlBatchRunnerRound11Test(unittest.TestCase):
    """第十一轮：私钥仅 root 可读，mysql binary mode 由签名 release 契约冻结。"""

    @classmethod
    def setUpClass(cls):
        cls.runner = import_runner()
        cls.launcher = import_root_launcher()

    @contextmanager
    def mocked_root_only_metadata(self, key: Path, *, key_mode: int, parent_modes: dict[Path, int] | None = None):
        """以真实临时 inode 配合 mock stat 模拟 root 安装，测试不需要 root 权限。"""
        parent_modes = parent_modes or {}
        original_lstat = self.launcher.os.lstat
        original_fstat = self.launcher.os.fstat

        def mode_for(path: Path, metadata) -> int:
            candidate = Path(path)
            if candidate == key:
                return key_mode
            if stat.S_ISDIR(metadata.st_mode):
                return parent_modes.get(candidate, 0o700)
            return stat.S_IMODE(metadata.st_mode)

        def root_owned_lstat(path):
            metadata = original_lstat(path)
            return SimpleNamespace(
                st_mode=stat.S_IFMT(metadata.st_mode) | mode_for(Path(path), metadata),
                st_uid=0,
            )

        def root_owned_fstat(descriptor):
            metadata = original_fstat(descriptor)
            fd_path = Path(self.launcher.os.readlink(f"/proc/self/fd/{descriptor}"))
            return SimpleNamespace(
                st_mode=stat.S_IFMT(metadata.st_mode) | mode_for(fd_path, metadata),
                st_uid=0,
            )

        with mock.patch.object(self.launcher.os, "lstat", side_effect=root_owned_lstat):
            with mock.patch.object(self.launcher.os, "fstat", side_effect=root_owned_fstat):
                yield

    @contextmanager
    def private_key_tree(self, mode: int = 0o400):
        """建立实际常规文件和目录；所有 owner/mode 断言由上面的 stat mock 表达。"""
        with tempfile.TemporaryDirectory(prefix="runner-round11-private-key-") as directory:
            root = Path(directory)
            private_directory = root / "root-only"
            private_directory.mkdir()
            key = private_directory / "launcher-attestation-private.pem"
            key.write_bytes(b"test-attestation-private-key")
            key.chmod(mode)
            yield key, private_directory

    def test_sign_payload_rejects_group_or_other_readable_private_key_before_openssl(self):
        """0644 与 0440 当前会穿透旧的仅拒绝 group/other write 检查，必须成为 Red。"""
        for key_mode in (0o644, 0o440):
            with self.subTest(key_mode=oct(key_mode)):
                with self.private_key_tree(key_mode) as (key, _private_directory):
                    with mock.patch.object(self.launcher, "ATTESTATION_PRIVATE_KEY", key):
                        with self.mocked_root_only_metadata(key, key_mode=key_mode):
                            with mock.patch.object(
                                self.launcher.subprocess,
                                "run",
                                return_value=SimpleNamespace(returncode=0, stdout=b"signature"),
                            ) as openssl:
                                with self.assertRaises(self.launcher.LauncherError):
                                    self.launcher.sign_payload(b"round11-payload")
                            openssl.assert_not_called()

    def test_sign_payload_rejects_group_or_other_traversable_private_key_parent_before_openssl(self):
        """0755/0711 父目录泄露私钥路径并允许非 root 遍历，必须 fail-close。"""
        for parent_mode in (0o755, 0o711):
            with self.subTest(parent_mode=oct(parent_mode)):
                with self.private_key_tree() as (key, private_directory):
                    with mock.patch.object(self.launcher, "ATTESTATION_PRIVATE_KEY", key):
                        with self.mocked_root_only_metadata(
                            key,
                            key_mode=0o400,
                            parent_modes={private_directory: parent_mode},
                        ):
                            with mock.patch.object(
                                self.launcher.subprocess,
                                "run",
                                return_value=SimpleNamespace(returncode=0, stdout=b"signature"),
                            ) as openssl:
                                with self.assertRaises(self.launcher.LauncherError):
                                    self.launcher.sign_payload(b"round11-payload")
                            openssl.assert_not_called()

    def test_private_key_reader_rejects_actual_symlink(self):
        """最终 key 使用 O_NOFOLLOW，真实符号链接不能被签名器接收。"""
        with self.private_key_tree() as (key, private_directory):
            link = private_directory / "launcher-attestation-private-link.pem"
            link.symlink_to(key)
            with mock.patch.object(self.launcher, "ATTESTATION_PRIVATE_KEY", link):
                with self.mocked_root_only_metadata(link, key_mode=0o400):
                    with self.assertRaises(self.launcher.LauncherError):
                        with self.launcher.open_attestation_private_key():
                            pass

    def test_private_key_reader_accepts_0400_and_0600_with_root_only_ancestors(self):
        """合法私钥仅接受 root-owned regular 0400/0600；元数据由 mock 提供。"""
        for key_mode in (0o400, 0o600):
            with self.subTest(key_mode=oct(key_mode)):
                with self.private_key_tree(key_mode) as (key, _private_directory):
                    with mock.patch.object(self.launcher, "ATTESTATION_PRIVATE_KEY", key):
                        with self.mocked_root_only_metadata(key, key_mode=key_mode):
                            with self.launcher.open_attestation_private_key() as descriptor:
                                self.assertTrue(stat.S_ISREG(self.launcher.os.fstat(descriptor).st_mode))

    def test_sign_payload_passes_the_opened_private_key_fd_via_proc_without_path_reopen(self):
        """签名 argv 只能引用 inherited FD，不能重新把私钥 pathname 交给 openssl。"""
        with self.private_key_tree() as (key, _private_directory):
            descriptor = self.launcher.os.open(key, self.launcher.os.O_RDONLY | self.launcher.os.O_CLOEXEC)

            @contextmanager
            def opened_private_key():
                try:
                    yield descriptor
                finally:
                    self.launcher.os.close(descriptor)

            try:
                with mock.patch.object(self.launcher, "ATTESTATION_PRIVATE_KEY", key):
                    with mock.patch.object(self.launcher, "open_attestation_private_key", opened_private_key):
                        with mock.patch.object(
                            self.launcher.subprocess,
                            "run",
                            return_value=SimpleNamespace(returncode=0, stdout=b"signature"),
                        ) as openssl:
                            self.assertEqual(b"signature", self.launcher.sign_payload(b"round11-payload"))
                command = openssl.call_args.args[0]
                self.assertEqual(f"/proc/self/fd/{descriptor}", command[-1])
                self.assertNotIn(str(key), command)
                self.assertEqual((descriptor,), openssl.call_args.kwargs["pass_fds"])
            finally:
                try:
                    self.launcher.os.close(descriptor)
                except OSError:
                    pass

    def test_binary_mode_is_frozen_by_signed_release_contract_schema_and_example(self):
        """runner argv、schema 和签名 example 必须绑定同一 binary-mode option 契约。"""
        expected_contract = "fixed-defaults-file-tcp-verify-identity-binary-mode"
        schema = json.loads(MYSQL_RELEASE_CONTRACT_SCHEMA.read_text(encoding="utf-8"))
        example = json.loads(MYSQL_RELEASE_CONTRACT.read_text(encoding="utf-8"))
        self.assertEqual(
            expected_contract,
            schema["properties"]["mysql_client_release_contract"]["properties"]["option_contract"]["const"],
        )
        self.assertEqual(expected_contract, example["mysql_client_release_contract"]["option_contract"])
        self.assertEqual(expected_contract, self.runner.MYSQL_FIXED_OPTION_CONTRACT)

    def test_unit_and_trust_readme_require_root_only_private_key_installation_validation(self):
        """部署材料必须明示私钥本体、所有祖先目录及只读安装核验方式。"""
        unit = SYSTEMD_UNIT_TEMPLATE.read_text(encoding="utf-8")
        readme = TRUST_README.read_text(encoding="utf-8")
        combined = unit + "\n" + readme
        for token in ("root:root", "0400", "0600", "mode & 0o077 == 0", "逐级", "符号链接"):
            self.assertIn(token, combined, f"安装材料缺少私钥 root-only 契约：{token}")
        for token in ("namei -l", "stat -c", "安装验证"):
            self.assertIn(token, readme, f"README 缺少只读安装验证：{token}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
