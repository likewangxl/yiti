#!/usr/bin/python3.10
"""受控 SQL batch runner：默认验证，执行时严格 fail-close。

仓库不是执行信任根。只有已部署到固定、root 管理目录的 release 才可能进入执行路径；
当前仓库故意不附带公钥、部署证明或在线 claim helper，因此 execute 必须在 mysql 前 fail-close。
"""

from __future__ import annotations

import argparse
import base64
import binascii
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import stat
import subprocess
import sys
from typing import Any, Iterator


# 这些值是部署映像的一部分，不能由 CLI、环境变量或仓库路径覆盖。
DEPLOYMENT_ROOT = Path("/opt/bank/controlled-sql-batch-runner/release")
DEPLOYMENT_MANIFEST_DIRECTORY = DEPLOYMENT_ROOT / "approved-manifests"
A2_PROOF_DIRECTORY = DEPLOYMENT_ROOT / "a2-recovery-proofs"
EMPTY_EXECUTOR_HOME = Path("/var/empty/controlled-sql-runner")
DEFAULTS_FILE = DEPLOYMENT_ROOT / "credentials/mysql-client.cnf"
MYSQL_CA_FILE = DEPLOYMENT_ROOT / "trust/mysql-ca.pem"

BASH_PATH = Path("/usr/bin/bash")
PYTHON_PATH = Path("/usr/bin/python3.10")
OPENSSL_PATH = Path("/usr/bin/openssl")
MYSQL_PATH = Path("/usr/bin/mysql")
CLAIM_HELPER_PATH = Path("/usr/local/libexec/bank-controlled-sql-receipt-claim-helper")
SYSTEMD_UNIT_PATH = Path("/etc/systemd/system/bank-controlled-sql-batch-runner@.service")
TRUSTED_OWNER_UID = 0

RUNNER_SHELL_RELATIVE = Path("docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.sh")
RUNNER_PYTHON_RELATIVE = Path("docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.py")
ROOT_LAUNCHER_RELATIVE = Path("docs/superpowers/scripts/2026-08-12-controlled-sql-root-managed-launcher.py")
SYSTEMD_UNIT_TEMPLATE_RELATIVE = Path(
    "docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner@.service.template"
)
CHECKER_SHELL_RELATIVE = Path("docs/superpowers/scripts/2026-08-12-verify-sql-target-identity-guard.sh")
CHECKER_PYTHON_RELATIVE = Path("docs/superpowers/scripts/2026-08-12-verify-sql-target-identity-guard.py")
MANIFEST_SCHEMA_RELATIVE = Path("docs/superpowers/sql/2026-08-12-controlled-sql-batch-manifest.schema.json")
RECEIPT_CLAIM_SCHEMA_RELATIVE = Path(
    "docs/superpowers/sql/2026-08-12-controlled-sql-receipt-claim-token.schema.json"
)
A2_PROOF_SCHEMA_RELATIVE = Path(
    "docs/superpowers/sql/2026-08-12-controlled-sql-a2-recovery-proof.schema.json"
)
LAUNCHER_PROOF_SCHEMA_RELATIVE = Path(
    "docs/superpowers/sql/2026-08-12-controlled-sql-root-launcher-proof.schema.json"
)
MYSQL_RELEASE_CONTRACT_SCHEMA_RELATIVE = Path(
    "docs/superpowers/sql/2026-08-12-controlled-sql-mysql-release-contract.schema.json"
)
MYSQL_RELEASE_CONTRACT_RELATIVE = Path(
    "docs/superpowers/sql/2026-08-12-controlled-sql-mysql-release-contract.json"
)
SQL_RELATIVES = (
    Path("docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql"),
    Path("docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql"),
    Path("docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql"),
)
TRUSTED_PUBLIC_KEY_RELATIVE = Path(
    "docs/superpowers/sql/controlled-batch-manifest-trust/controlled-manifest-public.pem"
)
ROOT_LAUNCHER_PROOF_PUBLIC_KEY_RELATIVE = Path(
    "docs/superpowers/sql/controlled-batch-manifest-trust/root-launcher-proof-public.pem"
)
CLAIM_TOKEN_PUBLIC_KEY_RELATIVE = Path(
    "docs/superpowers/sql/controlled-batch-manifest-trust/approval-service-claim-public.pem"
)
A2_PROOF_PUBLIC_KEY_RELATIVE = Path(
    "docs/superpowers/sql/controlled-batch-manifest-trust/a2-recovery-proof-public.pem"
)
MYSQL_RELEASE_CONTRACT_PUBLIC_KEY_RELATIVE = Path(
    "docs/superpowers/sql/controlled-batch-manifest-trust/mysql-release-contract-public.pem"
)
ARTIFACT_RELATIVES = {
    "runner_shell": RUNNER_SHELL_RELATIVE,
    "runner_python": RUNNER_PYTHON_RELATIVE,
    "root_managed_launcher": ROOT_LAUNCHER_RELATIVE,
    "systemd_unit_template": SYSTEMD_UNIT_TEMPLATE_RELATIVE,
    "preflight_checker_shell": CHECKER_SHELL_RELATIVE,
    "preflight_checker_python": CHECKER_PYTHON_RELATIVE,
    "manifest_schema": MANIFEST_SCHEMA_RELATIVE,
    "receipt_claim_schema": RECEIPT_CLAIM_SCHEMA_RELATIVE,
    "a2_proof_schema": A2_PROOF_SCHEMA_RELATIVE,
    "launcher_proof_schema": LAUNCHER_PROOF_SCHEMA_RELATIVE,
    "mysql_release_contract_schema": MYSQL_RELEASE_CONTRACT_SCHEMA_RELATIVE,
    "mysql_release_contract": MYSQL_RELEASE_CONTRACT_RELATIVE,
}
DEPLOYED_RUNNER_PYTHON = DEPLOYMENT_ROOT / RUNNER_PYTHON_RELATIVE
DEPLOYED_CHECKER_PYTHON = DEPLOYMENT_ROOT / CHECKER_PYTHON_RELATIVE
SCRIPT_PATH = Path(__file__)
LAUNCHER_PROOF_FD = 198
LAUNCHER_PROOF_SIGNATURE_FD = 199

# 当前仓库刻意未配发实际公钥。全零值不是可用 pin；经过审计的部署映像必须替换为真实 SHA-256。
UNPROVISIONED_FINGERPRINT = "0" * 64
PINNED_MANIFEST_PUBLIC_KEY_SHA256 = UNPROVISIONED_FINGERPRINT
PINNED_ROOT_LAUNCHER_PROOF_PUBLIC_KEY_SHA256 = UNPROVISIONED_FINGERPRINT
PINNED_CLAIM_TOKEN_PUBLIC_KEY_SHA256 = UNPROVISIONED_FINGERPRINT
PINNED_A2_PROOF_PUBLIC_KEY_SHA256 = UNPROVISIONED_FINGERPRINT
PINNED_MYSQL_RELEASE_CONTRACT_PUBLIC_KEY_SHA256 = UNPROVISIONED_FINGERPRINT

# mysql 子进程完全替换继承环境，故操作者无法注入 MYSQL_HOME、MYSQL_TEST_LOGIN_FILE 或 login-path。
MYSQL_EXECUTION_ENV = {
    "HOME": str(EMPTY_EXECUTOR_HOME),
    "PATH": "/usr/bin:/bin",
    "LC_ALL": "C",
    "LANG": "C",
    "LANGUAGE": "C",
    "TZ": "UTC",
}

MANIFEST_SCHEMA_ID = "https://bank.branch.platform/schemas/controlled-sql-batch-manifest-v3.json"
RECEIPT_CLAIM_SCHEMA_ID = "https://bank.branch.platform/schemas/controlled-sql-receipt-claim-token-v1.json"
A2_PROOF_SCHEMA_ID = "https://bank.branch.platform/schemas/controlled-sql-a2-recovery-proof-v1.json"
LAUNCHER_PROOF_SCHEMA_ID = "https://bank.branch.platform/schemas/controlled-sql-root-launcher-proof-v1.json"
MYSQL_RELEASE_CONTRACT_SCHEMA_ID = "https://bank.branch.platform/schemas/controlled-sql-mysql-release-contract-v1.json"
APPROVAL_SERVICE_ID = "bank-controlled-sql-approval-service"
ROOT_LAUNCHER_PROOF_ISSUER = "bank-controlled-sql-root-launcher"
A2_PROOF_ISSUER = "bank-a2-recovery-attestation-service"
MYSQL_RELEASE_CONTRACT_ISSUER = "bank-controlled-sql-release-signing"
MYSQL_FIXED_OPTION_CONTRACT = "fixed-defaults-file-tcp-verify-identity-binary-mode"
MAX_MANIFEST_TTL_SECONDS = 900
MAX_CLAIM_TTL_SECONDS = 300
MAX_A2_PROOF_TTL_SECONDS = 300
MAX_LAUNCHER_PROOF_TTL_SECONDS = 60
CLAIM_HELPER_TIMEOUT_SECONDS = 15

HEX64 = re.compile(r"^[0-9a-f]{64}$", re.IGNORECASE)
UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", re.IGNORECASE)
NONCE = re.compile(r"^[0-9a-f]{32,128}$", re.IGNORECASE)
HOST = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")
HOST_ONE_CHAR = re.compile(r"^[A-Za-z0-9]$")
SERVER_HOSTNAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,252}$")
SCHEMA = re.compile(r"^[A-Za-z0-9_]{1,64}$")
TICKET = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{2,99}$")
MANAGED_MANIFEST_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{2,127}\.json$")
B64 = re.compile(r"^[A-Za-z0-9+/]+={0,2}$")
BANNED_MANIFEST_KEY = re.compile(r"password|passwd|secret|credential|private.?key|access.?key|token", re.IGNORECASE)


class ValidationError(RuntimeError):
    """可安全输出的 fail-close 原因；消息不得携带凭据或输入原文。"""


class DuplicateJsonKey(ValueError):
    """严格 JSON 解析中发现重复 object key。"""


@dataclass(frozen=True)
class ToolSpec:
    """受审运行时工具的绝对路径与签名 release 冻结的哈希/版本。"""

    name: str
    path: Path
    expected_sha256: str
    expected_version: str


# 固定值对应获审基础映像；运行时只 hash 固定文件，绝不以自描述命令探测版本。
PYTHON_TOOL = ToolSpec(
    "python",
    PYTHON_PATH,
    "7d51cd6b48b521277f5caa4610a82126e315fa2be4df069823a8b1eeb5bd4a86",
    "Python 3.10.12",
)
OPENSSL_TOOL = ToolSpec(
    "openssl",
    OPENSSL_PATH,
    "a55e3085b6a1df8887722f6cee7fc32c861d11d5fb584a63837d32d29602c65b",
    "OpenSSL 3.0.2 15 Mar 2022",
)


@dataclass(frozen=True)
class OpenedTrustedFile:
    """从受信描述符一次读取的不可变字节视图。"""

    path: Path
    fd: int
    data: bytes


@dataclass(frozen=True)
class ApprovedManifest:
    """验签、schema、生命周期及本地字节哈希通过后的最小执行视图。"""

    manifest_sha256: str
    change_ticket: str
    not_before: str
    expiry: str
    execution_nonce: str
    connect_host: str
    connect_port: int
    server_uuid: str
    server_hostname: str
    schema: str
    recovery_reference: str
    recovery_sha256: str
    recovery_signed_proof_sha256: str
    receipt_id: str
    sql_batches: tuple[bytes, ...]
    a2_proof_id: str = "00000000-0000-0000-0000-000000000000"
    a2_proof_sha256: str = UNPROVISIONED_FINGERPRINT
    claim_helper_sha256: str = UNPROVISIONED_FINGERPRINT
    artifact_hashes: dict[str, str] = field(default_factory=dict, repr=False, compare=False)
    schema_contract: dict[str, Any] = field(default_factory=dict, repr=False, compare=False)
    protocol_contracts: dict[str, dict[str, Any]] = field(default_factory=dict, repr=False, compare=False)
    mysql_release_contract: dict[str, Any] = field(default_factory=dict, repr=False, compare=False)


@dataclass(frozen=True)
class LauncherProof:
    """root launcher 对当前 exec 进程签发的短时、不可重放部署证明。"""

    proof_sha256: str
    launch_id: str
    expires_at: str


@dataclass(frozen=True)
class ClaimedReceipt:
    """在线原子 claim 后的签名 token 最小视图，不接受离线文件回退。"""

    claim_id: str
    token_sha256: str
    issued_at: str
    expiry: str


@dataclass(frozen=True)
class A2RecoveryProof:
    """独立 issuer 签发并绑定当前 claim 的恢复证明。"""

    proof_id: str
    proof_sha256: str
    expiry: str


def sha256_bytes(data: bytes) -> str:
    """计算已打开文件字节的 SHA-256，禁止后续按路径重读。"""
    return hashlib.sha256(data).hexdigest()


def strict_json_loads(raw: bytes, label: str) -> Any:
    """UTF-8 JSON 严格解码，拒绝任意层级的重复键与非 JSON 常量。"""

    def reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise DuplicateJsonKey("duplicate JSON key")
            result[key] = value
        return result

    def reject_constant(_value: str) -> None:
        raise ValueError("non-standard JSON constant")

    try:
        return json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=reject_duplicate_keys,
            parse_constant=reject_constant,
        )
    except (UnicodeDecodeError, json.JSONDecodeError, DuplicateJsonKey, ValueError) as error:
        raise ValidationError(f"{label} 必须是无重复键的 UTF-8 JSON") from error


def require_string(value: Any, label: str, pattern: re.Pattern[str]) -> str:
    """精确字符串类型与正则白名单校验。"""
    if not isinstance(value, str) or not pattern.fullmatch(value):
        raise ValidationError(f"{label} 格式非法")
    return value


def require_hash(value: Any, label: str) -> str:
    """读取规范化的 SHA-256 文本。"""
    return require_string(value, label, HEX64).lower()


def require_exact_object(value: Any, label: str, expected: set[str]) -> dict[str, Any]:
    """拒绝少字段、多字段、数组和其他隐式结构。"""
    if not isinstance(value, dict) or set(value) != expected:
        raise ValidationError(f"{label} 字段契约不匹配")
    return value


def ensure_no_credential_fields(value: Any, trail: str = "manifest") -> None:
    """manifest/receipt 永不接受凭据字段，且错误中不回显字段名。"""
    if isinstance(value, dict):
        for key, child in value.items():
            if BANNED_MANIFEST_KEY.search(key):
                raise ValidationError(f"{trail} 含禁止的凭据字段")
            ensure_no_credential_fields(child, trail)
    elif isinstance(value, list):
        for child in value:
            ensure_no_credential_fields(child, trail)


def _schema_failure(label: str) -> None:
    raise ValidationError(f"{label} 不符合已加载 schema 契约")


def validate_against_schema(value: Any, schema: dict[str, Any], root_schema: dict[str, Any], label: str) -> None:
    """执行本 runner 所用 JSON Schema 子集，并显式拒绝 bool 伪装 integer。"""
    if not isinstance(schema, dict):
        _schema_failure(label)
    reference = schema.get("$ref")
    if reference is not None:
        if not isinstance(reference, str) or not reference.startswith("#/$defs/"):
            _schema_failure(label)
        definition = root_schema.get("$defs", {}).get(reference.removeprefix("#/$defs/"))
        if not isinstance(definition, dict):
            _schema_failure(label)
        validate_against_schema(value, definition, root_schema, label)
        return

    expected_type = schema.get("type")
    type_matches = {
        "object": lambda item: isinstance(item, dict),
        "string": lambda item: isinstance(item, str),
        "integer": lambda item: isinstance(item, int) and not isinstance(item, bool),
        "boolean": lambda item: isinstance(item, bool),
        "array": lambda item: isinstance(item, list),
    }
    if expected_type is not None:
        matcher = type_matches.get(expected_type)
        if matcher is None or not matcher(value):
            _schema_failure(label)

    if "const" in schema and value != schema["const"]:
        _schema_failure(label)
    if "enum" in schema and value not in schema["enum"]:
        _schema_failure(label)
    if isinstance(value, str):
        pattern = schema.get("pattern")
        if pattern is not None and (not isinstance(pattern, str) or re.fullmatch(pattern, value) is None):
            _schema_failure(label)
        if "minLength" in schema and len(value) < schema["minLength"]:
            _schema_failure(label)
        if "maxLength" in schema and len(value) > schema["maxLength"]:
            _schema_failure(label)
    if isinstance(value, int) and not isinstance(value, bool):
        if "minimum" in schema and value < schema["minimum"]:
            _schema_failure(label)
        if "maximum" in schema and value > schema["maximum"]:
            _schema_failure(label)
    if isinstance(value, dict):
        required = schema.get("required", [])
        properties = schema.get("properties", {})
        if not isinstance(required, list) or not isinstance(properties, dict):
            _schema_failure(label)
        if any(not isinstance(key, str) or key not in value for key in required):
            _schema_failure(label)
        additional = schema.get("additionalProperties", True)
        for key, child in value.items():
            child_schema = properties.get(key)
            if child_schema is None:
                if additional is False:
                    _schema_failure(label)
                if isinstance(additional, dict):
                    validate_against_schema(child, additional, root_schema, label)
                continue
            if not isinstance(child_schema, dict):
                _schema_failure(label)
            validate_against_schema(child, child_schema, root_schema, label)


def _parse_utc(value: Any, label: str) -> datetime:
    if not isinstance(value, str):
        raise ValidationError(f"{label} 必须是 UTC 时间")
    try:
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except ValueError as error:
        raise ValidationError(f"{label} 必须是 YYYY-MM-DDTHH:MM:SSZ") from error


def validate_time_window(
    not_before: Any,
    expiry: Any,
    label: str,
    maximum_seconds: int,
    now: datetime | None = None,
) -> tuple[datetime, datetime]:
    """验证 not-before、生效期和短 TTL；时钟不可证明时宁可拒绝。"""
    start = _parse_utc(not_before, f"{label}.not_before_utc")
    end = _parse_utc(expiry, f"{label}.expires_at_utc")
    current = now or datetime.now(timezone.utc)
    if end <= start or (end - start).total_seconds() > maximum_seconds:
        raise ValidationError(f"{label} TTL 超过受控上限或时间窗口非法")
    if current < start or current >= end:
        raise ValidationError(f"{label} 尚未生效或已过期")
    return start, end


def _absolute_components(path: Path, label: str) -> tuple[str, ...]:
    candidate = Path(path)
    if not candidate.is_absolute() or any(part in {".", ".."} for part in candidate.parts):
        raise ValidationError(f"{label} 必须是无跳转段的绝对路径")
    return candidate.parts


def verify_trusted_metadata(metadata: os.stat_result, label: str, *, directory: bool) -> None:
    """每一级目录及文件均须 root 所有且对组/其他用户不可写。"""
    expected_type = stat.S_ISDIR(metadata.st_mode) if directory else stat.S_ISREG(metadata.st_mode)
    if not expected_type:
        raise ValidationError(f"{label} 类型不受信任")
    if metadata.st_uid != TRUSTED_OWNER_UID:
        raise ValidationError(f"{label} owner 不受信任")
    if stat.S_IMODE(metadata.st_mode) & 0o022:
        raise ValidationError(f"{label} mode 允许非信任主体写入")


def _open_trusted_path(path: Path, label: str, *, final_directory: bool) -> int:
    """以 openat + O_NOFOLLOW 逐级打开，避免检查后再按名称重开。"""
    parts = _absolute_components(path, label)
    base_flags = os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW
    opened: list[int] = []
    try:
        root_fd = os.open("/", base_flags | os.O_DIRECTORY)
        opened.append(root_fd)
        verify_trusted_metadata(os.fstat(root_fd), "trusted path root", directory=True)
        path_parts = parts[1:]
        for index, component in enumerate(path_parts):
            is_last = index == len(path_parts) - 1
            directory = final_directory if is_last else True
            flags = base_flags | (os.O_DIRECTORY if directory else 0)
            next_fd = os.open(component, flags, dir_fd=opened[-1])
            opened.append(next_fd)
            verify_trusted_metadata(os.fstat(next_fd), label, directory=directory)
        final_fd = opened[-1]
        for parent_fd in opened[:-1]:
            os.close(parent_fd)
        return final_fd
    except OSError as error:
        for descriptor in opened:
            try:
                os.close(descriptor)
            except OSError:
                pass
        raise ValidationError(f"{label} 无法以受信描述符打开") from error
    except Exception:
        for descriptor in opened:
            try:
                os.close(descriptor)
            except OSError:
                pass
        raise


def require_trusted_directory(path: Path, label: str) -> None:
    """验证目录及从根到目录的每一级 parent。"""
    descriptor = _open_trusted_path(path, label, final_directory=True)
    try:
        return None
    finally:
        os.close(descriptor)


@contextmanager
def open_trusted_file(path: Path, label: str) -> Iterator[OpenedTrustedFile]:
    """单次 read 后以同一 fd 的字节完成后续验签、hash 与解析。"""
    descriptor = _open_trusted_path(path, label, final_directory=False)
    try:
        before = os.fstat(descriptor)
        verify_trusted_metadata(before, label, directory=False)
        chunks: list[bytes] = []
        while True:
            fragment = os.read(descriptor, 1024 * 1024)
            if not fragment:
                break
            chunks.append(fragment)
        after = os.fstat(descriptor)
        stable_fields = ("st_dev", "st_ino", "st_size", "st_mtime_ns", "st_ctime_ns", "st_mode", "st_uid", "st_gid")
        if any(getattr(before, field_name) != getattr(after, field_name) for field_name in stable_fields):
            raise ValidationError(f"{label} 在读取期间发生变化")
        yield OpenedTrustedFile(path=Path(path), fd=descriptor, data=b"".join(chunks))
    except OSError as error:
        raise ValidationError(f"{label} 安全读取失败") from error
    finally:
        os.close(descriptor)


def read_trusted_file_bytes(path: Path, label: str) -> bytes:
    """对外提供一次性安全读取字节，不返回可重新按路径获取的句柄。"""
    with open_trusted_file(path, label) as opened:
        return opened.data


@contextmanager
def open_sealed_inherited_file(descriptor: int, label: str) -> Iterator[OpenedTrustedFile]:
    """读取 root launcher 传入的固定 memfd；缺密封或读取变化一律 fail-close。"""
    required_seals = (
        getattr(fcntl, "F_SEAL_WRITE", 0)
        | getattr(fcntl, "F_SEAL_GROW", 0)
        | getattr(fcntl, "F_SEAL_SHRINK", 0)
        | getattr(fcntl, "F_SEAL_SEAL", 0)
    )
    get_seals = getattr(fcntl, "F_GET_SEALS", None)
    if get_seals is None or required_seals == 0:
        raise ValidationError(f"{label} 平台无法证明 root-managed launcher memfd 密封")
    try:
        before = os.fstat(descriptor)
        if not stat.S_ISREG(before.st_mode):
            raise ValidationError(f"{label} 不是 launcher 传入的常规 memfd")
        if fcntl.fcntl(descriptor, get_seals) & required_seals != required_seals:
            raise ValidationError(f"{label} 未完成 root-managed launcher 密封")
        os.lseek(descriptor, 0, os.SEEK_SET)
        chunks: list[bytes] = []
        while True:
            fragment = os.read(descriptor, 1024 * 1024)
            if not fragment:
                break
            chunks.append(fragment)
        after = os.fstat(descriptor)
        if (
            before.st_dev != after.st_dev
            or before.st_ino != after.st_ino
            or before.st_size != after.st_size
            or fcntl.fcntl(descriptor, get_seals) & required_seals != required_seals
        ):
            raise ValidationError(f"{label} 在读取期间发生变化")
        yield OpenedTrustedFile(Path(f"/proc/self/fd/{descriptor}"), descriptor, b"".join(chunks))
    except OSError as error:
        raise ValidationError(f"{label} 缺失或不可读取") from error
    finally:
        try:
            os.close(descriptor)
        except OSError:
            pass


@contextmanager
def open_memory_signature(data: bytes, label: str) -> Iterator[OpenedTrustedFile]:
    """把 helper stdout 中的 detached signature 放入 memfd，避免临时文件和路径重开。"""
    memfd_create = getattr(os, "memfd_create", None)
    if memfd_create is None:
        raise ValidationError(f"{label} 平台不支持内存验签")
    descriptor = memfd_create(f"controlled-sql-{label}", os.MFD_CLOEXEC)
    try:
        os.write(descriptor, data)
        os.lseek(descriptor, 0, os.SEEK_SET)
        yield OpenedTrustedFile(Path(f"/proc/self/fd/{descriptor}"), descriptor, data)
    except OSError as error:
        raise ValidationError(f"{label} 无法装载") from error
    finally:
        os.close(descriptor)


def require_managed_manifest_path(path: Path) -> Path:
    """manifest 只能由 root 管理的固定收件目录直接提供。"""
    candidate = Path(path)
    _absolute_components(candidate, "manifest")
    if candidate.parent != DEPLOYMENT_MANIFEST_DIRECTORY or not MANAGED_MANIFEST_NAME.fullmatch(candidate.name):
        raise ValidationError("manifest 不在固定受控收件目录")
    return candidate


def _deployed_path(relative: Path, label: str) -> Path:
    """仅允许代码常量中的 release 内相对路径。"""
    if relative not in set(ARTIFACT_RELATIVES.values()) | set(SQL_RELATIVES):
        raise ValidationError(f"{label} 不在部署 allow-list")
    return DEPLOYMENT_ROOT / relative


def load_schema_contract() -> tuple[dict[str, Any], bytes]:
    """从可信 release 读取并实际执行 manifest schema。"""
    raw = read_trusted_file_bytes(DEPLOYMENT_ROOT / MANIFEST_SCHEMA_RELATIVE, "manifest schema")
    return _schema_from_raw(raw, "manifest schema", MANIFEST_SCHEMA_ID), raw


def _schema_from_raw(raw: bytes, label: str, expected_schema_id: str) -> dict[str, Any]:
    """从已打开的 artifact bytes 解析严格 schema，不给文档式 schema 留旁路。"""
    schema = strict_json_loads(raw, label)
    if not isinstance(schema, dict) or schema.get("$id") != expected_schema_id or schema.get("type") != "object":
        raise ValidationError(f"{label} 契约不受信任")
    return schema


def require_approved_binary(specification: ToolSpec) -> None:
    """只 hash 固定绝对路径二进制；版本已由签名 release 冻结，运行时绝不探测。"""
    _require_configured_fingerprint(specification.expected_sha256, f"tool {specification.name}")
    actual_hash = sha256_bytes(read_trusted_file_bytes(specification.path, f"tool {specification.name}"))
    if actual_hash != specification.expected_sha256.lower():
        raise ValidationError(f"tool {specification.name} SHA-256 不匹配")


def _require_configured_fingerprint(fingerprint: str, label: str) -> None:
    if fingerprint == UNPROVISIONED_FINGERPRINT or HEX64.fullmatch(fingerprint) is None:
        raise ValidationError(f"{label} 未配置获审固定指纹")


def _verify_pinned_key(opened_key: OpenedTrustedFile, expected_fingerprint: str, label: str) -> None:
    _require_configured_fingerprint(expected_fingerprint, label)
    if sha256_bytes(opened_key.data) != expected_fingerprint.lower():
        raise ValidationError(f"{label} 固定指纹不匹配")


def verify_detached_signature(
    payload: bytes,
    signature: OpenedTrustedFile,
    public_key: OpenedTrustedFile,
    label: str,
) -> None:
    """使用已打开 fd 验签；payload 从内存输入，避免验签后再次按路径读取。"""
    require_approved_binary(OPENSSL_TOOL)
    completed = subprocess.run(
        [
            str(OPENSSL_PATH),
            "dgst",
            "-sha256",
            "-verify",
            f"/proc/self/fd/{public_key.fd}",
            "-signature",
            f"/proc/self/fd/{signature.fd}",
        ],
        cwd="/",
        env=MYSQL_EXECUTION_ENV,
        input=payload,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        pass_fds=(public_key.fd, signature.fd),
        check=False,
    )
    if completed.returncode != 0:
        raise ValidationError(f"{label} detached signature 验证失败")


def _read_and_verify_manifest(manifest_path: Path) -> bytes:
    signature_path = Path(f"{manifest_path}.sig")
    public_key_path = DEPLOYMENT_ROOT / TRUSTED_PUBLIC_KEY_RELATIVE
    with open_trusted_file(manifest_path, "manifest") as manifest_file:
        with open_trusted_file(signature_path, "manifest detached signature") as signature_file:
            with open_trusted_file(public_key_path, "manifest trusted public key") as public_key_file:
                _verify_pinned_key(public_key_file, PINNED_MANIFEST_PUBLIC_KEY_SHA256, "manifest public key")
                verify_detached_signature(manifest_file.data, signature_file, public_key_file, "manifest")
                return manifest_file.data


def _verify_bound_hash(actual: bytes, expected: Any, label: str) -> None:
    if sha256_bytes(actual) != require_hash(expected, label):
        raise ValidationError(f"{label} 本地 SHA-256 不匹配")


def _verify_manifest_artifacts(payload: dict[str, Any], schema_raw: bytes) -> dict[str, bytes]:
    """读取每个 artifact 一次并返回已验 hash 的 bytes，避免 schema/hash 后路径重开。"""
    artifacts = payload["artifact_sha256"]
    if not isinstance(artifacts, dict) or set(artifacts) != set(ARTIFACT_RELATIVES):
        raise ValidationError("artifact_sha256 字段契约不匹配")
    verified: dict[str, bytes] = {}
    for label, relative in ARTIFACT_RELATIVES.items():
        item = require_exact_object(artifacts[label], f"artifact_sha256.{label}", {"path", "sha256"})
        if item["path"] != str(relative):
            raise ValidationError(f"artifact_sha256.{label} 路径不在 allow-list")
        actual = schema_raw if relative == MANIFEST_SCHEMA_RELATIVE else read_trusted_file_bytes(
            _deployed_path(relative, f"artifact {label}"), f"artifact {label}"
        )
        _verify_bound_hash(actual, item["sha256"], f"artifact_sha256.{label}")
        verified[label] = actual
    return verified


def _load_bound_sql(payload: dict[str, Any]) -> tuple[bytes, ...]:
    sql_hashes = payload["sql_sha256"]
    expected_paths = {str(relative) for relative in SQL_RELATIVES}
    if not isinstance(sql_hashes, dict) or set(sql_hashes) != expected_paths:
        raise ValidationError("sql_sha256 必须恰好绑定固定三份 SQL")
    batches: list[bytes] = []
    for relative in SQL_RELATIVES:
        raw = read_trusted_file_bytes(_deployed_path(relative, "SQL"), f"SQL {relative.name}")
        if not raw.endswith(b"\n"):
            raise ValidationError(f"SQL {relative.name} 未以换行结束，拒绝拼接歧义")
        _verify_bound_hash(raw, sql_hashes[str(relative)], f"sql_sha256[{relative}]")
        batches.append(raw)
    return tuple(batches)


def _load_signed_mysql_release_contract(raw: bytes, schema: dict[str, Any]) -> dict[str, Any]:
    """验签 release/SBOM/manpage 冻结契约；不执行 mysql 自描述命令。"""
    signature_path = Path(f"{DEPLOYMENT_ROOT / MYSQL_RELEASE_CONTRACT_RELATIVE}.sig")
    public_key_path = DEPLOYMENT_ROOT / MYSQL_RELEASE_CONTRACT_PUBLIC_KEY_RELATIVE
    with open_trusted_file(signature_path, "mysql release contract detached signature") as signature_file:
        with open_trusted_file(public_key_path, "mysql release contract public key") as public_key_file:
            _verify_pinned_key(
                public_key_file,
                PINNED_MYSQL_RELEASE_CONTRACT_PUBLIC_KEY_SHA256,
                "mysql release contract public key",
            )
            verify_detached_signature(raw, signature_file, public_key_file, "mysql release contract")
    payload = strict_json_loads(raw, "mysql release contract")
    validate_against_schema(payload, schema, schema, "mysql release contract")
    contract = require_exact_object(
        payload,
        "mysql release contract",
        {"schema_version", "issuer", "mysql_client_release_contract"},
    )
    if contract["issuer"] != MYSQL_RELEASE_CONTRACT_ISSUER:
        raise ValidationError("mysql release contract issuer 非法")
    mysql_contract = require_exact_object(
        contract["mysql_client_release_contract"],
        "mysql_client_release_contract",
        {"path", "sha256", "version", "option_contract", "sbom_sha256", "manpage_sha256"},
    )
    if mysql_contract["path"] != str(MYSQL_PATH):
        raise ValidationError("mysql release contract 路径不是固定 mysql")
    require_hash(mysql_contract["sha256"], "mysql release contract.sha256")
    require_hash(mysql_contract["sbom_sha256"], "mysql release contract.sbom_sha256")
    require_hash(mysql_contract["manpage_sha256"], "mysql release contract.manpage_sha256")
    if (
        not isinstance(mysql_contract["version"], str)
        or not mysql_contract["version"]
        or mysql_contract["option_contract"] != MYSQL_FIXED_OPTION_CONTRACT
    ):
        raise ValidationError("mysql release contract version 或 option 契约非法")
    return mysql_contract


def load_and_validate_manifest(manifest_path: Path) -> ApprovedManifest:
    """只用首次打开的 manifest bytes 完成验签、hash、严格 JSON、schema 与语义校验。"""
    managed_path = require_managed_manifest_path(manifest_path)
    schema_contract, schema_raw = load_schema_contract()
    raw = _read_and_verify_manifest(managed_path)
    payload = strict_json_loads(raw, "manifest")
    validate_against_schema(payload, schema_contract, schema_contract, "manifest")
    if not isinstance(payload, dict):
        raise ValidationError("manifest 顶层必须是 object")
    ensure_no_credential_fields(payload)
    validate_time_window(
        payload["not_before_utc"],
        payload["expires_at_utc"],
        "manifest",
        MAX_MANIFEST_TTL_SECONDS,
    )

    artifact_bytes = _verify_manifest_artifacts(payload, schema_raw)
    protocol_contracts = {
        "receipt_claim": _schema_from_raw(
            artifact_bytes["receipt_claim_schema"], "receipt claim schema", RECEIPT_CLAIM_SCHEMA_ID
        ),
        "a2_proof": _schema_from_raw(artifact_bytes["a2_proof_schema"], "A2 proof schema", A2_PROOF_SCHEMA_ID),
        "launcher_proof": _schema_from_raw(
            artifact_bytes["launcher_proof_schema"], "launcher proof schema", LAUNCHER_PROOF_SCHEMA_ID
        ),
    }
    mysql_contract_schema = _schema_from_raw(
        artifact_bytes["mysql_release_contract_schema"],
        "mysql release contract schema",
        MYSQL_RELEASE_CONTRACT_SCHEMA_ID,
    )
    mysql_release_contract = _load_signed_mysql_release_contract(
        artifact_bytes["mysql_release_contract"], mysql_contract_schema
    )
    target = payload["target"]
    recovery = payload["a2_recovery_point"]
    receipt = payload["approval_receipt"]
    helper = payload["receipt_claim_helper"]
    if not isinstance(target, dict) or not isinstance(recovery, dict) or not isinstance(receipt, dict):
        raise ValidationError("manifest 嵌套对象类型非法")
    connect_host = require_string(target["connect_host"], "target.connect_host", HOST)
    if not (HOST.fullmatch(connect_host) or HOST_ONE_CHAR.fullmatch(connect_host)):
        raise ValidationError("target.connect_host 格式非法")
    connect_port = target["connect_port"]
    if isinstance(connect_port, bool) or not isinstance(connect_port, int) or not 1 <= connect_port <= 65535:
        raise ValidationError("target.connect_port 必须是 1..65535 整数")

    helper_contract = require_exact_object(helper, "receipt_claim_helper", {"path", "sha256"})
    if helper_contract["path"] != str(CLAIM_HELPER_PATH):
        raise ValidationError("receipt claim helper 路径不是固定绝对路径")
    sql_batches = _load_bound_sql(payload)
    artifact_hashes = {
        label: require_hash(payload["artifact_sha256"][label]["sha256"], f"artifact_sha256.{label}.sha256")
        for label in ARTIFACT_RELATIVES
    }
    proof_sha256 = require_hash(recovery["proof_sha256"], "a2_recovery_point.proof_sha256")
    return ApprovedManifest(
        manifest_sha256=sha256_bytes(raw),
        change_ticket=require_string(payload["change_ticket"], "change_ticket", TICKET),
        not_before=payload["not_before_utc"],
        expiry=payload["expires_at_utc"],
        execution_nonce=require_string(payload["execution_nonce"], "execution_nonce", NONCE),
        connect_host=connect_host,
        connect_port=connect_port,
        server_uuid=require_string(target["server_uuid"], "target.server_uuid", UUID),
        server_hostname=require_string(target["server_hostname"], "target.server_hostname", SERVER_HOSTNAME),
        schema=require_string(target["schema"], "target.schema", SCHEMA),
        recovery_reference=require_string(recovery["reference"], "a2_recovery_point.reference", TICKET),
        recovery_sha256=require_hash(
            recovery["recovery_point_sha256"], "a2_recovery_point.recovery_point_sha256"
        ),
        recovery_signed_proof_sha256=proof_sha256,
        receipt_id=require_string(receipt["receipt_id"], "approval_receipt.receipt_id", UUID),
        sql_batches=sql_batches,
        a2_proof_id=require_string(recovery["proof_id"], "a2_recovery_point.proof_id", UUID),
        a2_proof_sha256=proof_sha256,
        claim_helper_sha256=require_hash(helper_contract["sha256"], "receipt_claim_helper.sha256"),
        artifact_hashes=artifact_hashes,
        schema_contract=schema_contract,
        protocol_contracts=protocol_contracts,
        mysql_release_contract=mysql_release_contract,
    )


def _proc_self_start_time() -> str:
    """读取当前 PID 的 kernel start tick，拒绝捕获 proof 的跨进程重放。"""
    try:
        raw = Path("/proc/self/stat").read_text(encoding="ascii")
        closing = raw.rfind(")")
        fields = raw[closing + 2 :].split()
        pid_start_time = fields[19]
    except (OSError, UnicodeError, IndexError) as error:
        raise ValidationError("无法证明当前 launcher 子进程的 PID 起始时间") from error
    if not pid_start_time.isdigit():
        raise ValidationError("当前 PID 起始时间格式非法")
    return pid_start_time


def validate_root_launcher_proof_claims(
    claims: Any,
    manifest: ApprovedManifest,
    now: datetime | None = None,
) -> LauncherProof:
    """校验 root-managed launcher 对本 exec 实例签发的签名、PID 绑定部署证明。"""
    proof = require_exact_object(
        claims,
        "root launcher proof",
        {
            "proof_version",
            "issuer",
            "launch_id",
            "mode",
            "manifest_sha256",
            "subject_uid",
            "pid",
            "pid_start_time",
            "issued_at_utc",
            "expires_at_utc",
            "runner_sha256",
            "root_launcher_sha256",
            "systemd_unit_sha256",
            "claim_helper_sha256",
        },
    )
    if isinstance(proof["proof_version"], bool) or proof["proof_version"] != 1:
        raise ValidationError("root launcher proof version 非法")
    if proof["issuer"] != ROOT_LAUNCHER_PROOF_ISSUER or proof["mode"] != "execute":
        raise ValidationError("root launcher proof issuer 或 mode 非法")
    require_string(proof["launch_id"], "root launcher proof.launch_id", UUID)
    if require_hash(proof["manifest_sha256"], "root launcher proof.manifest_sha256") != manifest.manifest_sha256:
        raise ValidationError("root launcher proof 未绑定 manifest")
    if (
        isinstance(proof["subject_uid"], bool)
        or not isinstance(proof["subject_uid"], int)
        or proof["subject_uid"] != os.geteuid()
        or isinstance(proof["pid"], bool)
        or proof["pid"] != os.getpid()
        or not isinstance(proof["pid_start_time"], str)
        or proof["pid_start_time"] != _proc_self_start_time()
    ):
        raise ValidationError("root launcher proof 未绑定当前受限 service account 进程")
    expected_hashes = {
        "runner_sha256": manifest.artifact_hashes.get("runner_python"),
        "root_launcher_sha256": manifest.artifact_hashes.get("root_managed_launcher"),
        "systemd_unit_sha256": manifest.artifact_hashes.get("systemd_unit_template"),
        "claim_helper_sha256": manifest.claim_helper_sha256,
    }
    for field_name, expected in expected_hashes.items():
        if expected is None or require_hash(proof[field_name], f"root launcher proof.{field_name}") != expected:
            raise ValidationError("root launcher proof 未绑定已审 release hash")
    current = now or datetime.now(timezone.utc)
    start, end = validate_time_window(
        proof["issued_at_utc"],
        proof["expires_at_utc"],
        "root launcher proof",
        MAX_LAUNCHER_PROOF_TTL_SECONDS,
        current,
    )
    manifest_start = _parse_utc(manifest.not_before, "manifest.not_before_utc")
    manifest_end = _parse_utc(manifest.expiry, "manifest.expires_at_utc")
    if start < manifest_start or end > manifest_end:
        raise ValidationError("root launcher proof 时间未绑定 manifest")
    canonical = json.dumps(proof, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return LauncherProof(sha256_bytes(canonical), proof["launch_id"], proof["expires_at_utc"])


def load_root_launcher_proof(manifest: ApprovedManifest) -> LauncherProof:
    """从固定密封 fd 加载同一 bytes，使用独立 key 验证 launcher 部署证明。"""
    schema = manifest.protocol_contracts.get("launcher_proof")
    if not isinstance(schema, dict):
        raise ValidationError("缺少已签名 launcher proof schema")
    public_key_path = DEPLOYMENT_ROOT / ROOT_LAUNCHER_PROOF_PUBLIC_KEY_RELATIVE
    with open_sealed_inherited_file(LAUNCHER_PROOF_FD, "root-managed launcher proof") as proof_file:
        with open_sealed_inherited_file(
            LAUNCHER_PROOF_SIGNATURE_FD, "root-managed launcher proof detached signature"
        ) as signature_file:
            with open_trusted_file(public_key_path, "root launcher proof public key") as public_key_file:
                _verify_pinned_key(
                    public_key_file,
                    PINNED_ROOT_LAUNCHER_PROOF_PUBLIC_KEY_SHA256,
                    "root launcher proof public key",
                )
                verify_detached_signature(
                    proof_file.data,
                    signature_file,
                    public_key_file,
                    "root-managed launcher proof",
                )
                payload = strict_json_loads(proof_file.data, "root-managed launcher proof")
    validate_against_schema(payload, schema, schema, "root-managed launcher proof")
    return validate_root_launcher_proof_claims(payload, manifest)


def require_claim_helper_contract(manifest: ApprovedManifest) -> None:
    """确认在线原子 claim helper 是 root-owned 固定路径且 bytes 匹配已审 hash。"""
    _require_configured_fingerprint(manifest.claim_helper_sha256, "receipt claim helper")
    actual = sha256_bytes(read_trusted_file_bytes(CLAIM_HELPER_PATH, "receipt claim helper"))
    if actual != manifest.claim_helper_sha256:
        raise ValidationError("receipt claim helper SHA-256 不匹配")


def _strict_b64(value: Any, label: str) -> bytes:
    if not isinstance(value, str) or not value or not B64.fullmatch(value) or len(value) % 4 != 0:
        raise ValidationError(f"{label} 编码非法")
    try:
        return base64.b64decode(value.encode("ascii"), validate=True)
    except (UnicodeEncodeError, binascii.Error) as error:
        raise ValidationError(f"{label} 编码非法") from error


def _validate_claim_protocol_schema(response: Any, token: Any, manifest: ApprovedManifest) -> None:
    """执行已签名 claim response/token schema，禁止仅用局部字段比较伪装在线 claim。"""
    schema = manifest.protocol_contracts.get("receipt_claim")
    if not isinstance(schema, dict):
        raise ValidationError("缺少已签名 receipt claim schema")
    definitions = schema.get("$defs", {})
    response_schema = definitions.get("claimHelperResponse") if isinstance(definitions, dict) else None
    token_schema = definitions.get("claimToken") if isinstance(definitions, dict) else None
    if not isinstance(response_schema, dict) or not isinstance(token_schema, dict):
        raise ValidationError("receipt claim schema 缺少 response/token 契约")
    validate_against_schema(response, response_schema, schema, "receipt claim helper response")
    validate_against_schema(token, token_schema, schema, "receipt claim token")


def validate_claim_token_claims(
    claims: Any,
    manifest: ApprovedManifest,
    launcher: LauncherProof,
    now: datetime | None = None,
) -> ClaimedReceipt:
    """校验在线服务原子消费 receipt 后返回的签名 claim token 绑定。"""
    token = require_exact_object(
        claims,
        "receipt claim token",
        {
            "claim_version",
            "issuer",
            "claim_id",
            "receipt_id",
            "manifest_sha256",
            "change_ticket",
            "execution_nonce",
            "launcher_proof_sha256",
            "issued_at_utc",
            "expires_at_utc",
        },
    )
    if isinstance(token["claim_version"], bool) or token["claim_version"] != 1:
        raise ValidationError("receipt claim token version 非法")
    if token["issuer"] != APPROVAL_SERVICE_ID:
        raise ValidationError("receipt claim token issuer 非法")
    if require_string(token["claim_id"], "receipt claim token.claim_id", UUID) == manifest.receipt_id:
        raise ValidationError("receipt claim token claim id 不得复用 receipt id")
    if require_string(token["receipt_id"], "receipt claim token.receipt_id", UUID) != manifest.receipt_id:
        raise ValidationError("receipt claim token 未绑定 receipt id")
    if require_hash(token["manifest_sha256"], "receipt claim token.manifest_sha256") != manifest.manifest_sha256:
        raise ValidationError("receipt claim token 未绑定 manifest")
    if require_string(token["change_ticket"], "receipt claim token.change_ticket", TICKET) != manifest.change_ticket:
        raise ValidationError("receipt claim token 未绑定 change ticket")
    if require_string(token["execution_nonce"], "receipt claim token.execution_nonce", NONCE) != manifest.execution_nonce:
        raise ValidationError("receipt claim token 未绑定 execution nonce")
    if (
        require_hash(token["launcher_proof_sha256"], "receipt claim token.launcher_proof_sha256")
        != launcher.proof_sha256
    ):
        raise ValidationError("receipt claim token 未绑定当前 root launcher proof")
    current = now or datetime.now(timezone.utc)
    start, end = validate_time_window(
        token["issued_at_utc"],
        token["expires_at_utc"],
        "receipt claim token",
        MAX_CLAIM_TTL_SECONDS,
        current,
    )
    manifest_start = _parse_utc(manifest.not_before, "manifest.not_before_utc")
    manifest_end = _parse_utc(manifest.expiry, "manifest.expires_at_utc")
    launcher_end = _parse_utc(launcher.expires_at, "root launcher proof.expires_at_utc")
    if start < manifest_start or end > manifest_end or end > launcher_end:
        raise ValidationError("receipt claim token 时间未绑定 manifest/launcher proof")
    canonical = json.dumps(token, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return ClaimedReceipt(token["claim_id"], sha256_bytes(canonical), token["issued_at_utc"], token["expires_at_utc"])


def verify_claim_token_signature(token_bytes: bytes, signature_bytes: bytes) -> None:
    """使用审批服务独立固定 pin 验签 helper 返回的 raw claim token。"""
    public_key_path = DEPLOYMENT_ROOT / CLAIM_TOKEN_PUBLIC_KEY_RELATIVE
    with open_memory_signature(signature_bytes, "claim-token-signature") as signature_file:
        with open_trusted_file(public_key_path, "receipt claim token public key") as public_key_file:
            _verify_pinned_key(
                public_key_file,
                PINNED_CLAIM_TOKEN_PUBLIC_KEY_SHA256,
                "receipt claim token public key",
            )
            verify_detached_signature(token_bytes, signature_file, public_key_file, "receipt claim token")


def claim_receipt_atomically(
    manifest: ApprovedManifest,
    launcher: LauncherProof,
    now: datetime | None = None,
) -> ClaimedReceipt:
    """通过独立 helper 在线原子消费 receipt；没有本地离线 receipt 回退。"""
    require_claim_helper_contract(manifest)
    request = {
        "request_version": 1,
        "receipt_id": manifest.receipt_id,
        "manifest_sha256": manifest.manifest_sha256,
        "change_ticket": manifest.change_ticket,
        "execution_nonce": manifest.execution_nonce,
        "launcher_proof_sha256": launcher.proof_sha256,
        "a2_proof_id": manifest.a2_proof_id,
        "a2_proof_sha256": manifest.a2_proof_sha256,
    }
    request_bytes = json.dumps(request, sort_keys=True, separators=(",", ":")).encode("utf-8")
    try:
        completed = subprocess.run(
            [str(CLAIM_HELPER_PATH), "--protocol=v1", "--claim-once"],
            cwd="/",
            env=MYSQL_EXECUTION_ENV,
            input=request_bytes,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=CLAIM_HELPER_TIMEOUT_SECONDS,
            check=False,
        )
    except FileNotFoundError as error:
        raise ValidationError("在线 receipt claim helper 缺失；拒绝执行") from error
    except subprocess.TimeoutExpired as error:
        raise ValidationError("在线 receipt claim helper 超时或断线；拒绝执行") from error
    except OSError as error:
        raise ValidationError("在线 receipt claim helper 不可用；拒绝执行") from error
    if completed.returncode != 0:
        if completed.returncode == 75:
            raise ValidationError("在线 receipt 已被原子消费；拒绝重放执行")
        if completed.returncode == 76:
            raise ValidationError("在线 receipt 已撤销；拒绝执行")
        raise ValidationError("在线 receipt claim 未获确认；拒绝执行")
    response = strict_json_loads(completed.stdout, "receipt claim helper response")
    response_object = require_exact_object(
        response,
        "receipt claim helper response",
        {"response_version", "status", "claim_token_b64", "claim_signature_b64"},
    )
    if isinstance(response_object["response_version"], bool) or response_object["response_version"] != 1:
        raise ValidationError("receipt claim helper response version 非法")
    if response_object["status"] != "claimed":
        raise ValidationError("在线 receipt claim 未获 claimed 状态")
    token_bytes = _strict_b64(response_object["claim_token_b64"], "receipt claim token")
    signature_bytes = _strict_b64(response_object["claim_signature_b64"], "receipt claim signature")
    token = strict_json_loads(token_bytes, "receipt claim token")
    _validate_claim_protocol_schema(response_object, token, manifest)
    verify_claim_token_signature(token_bytes, signature_bytes)
    return validate_claim_token_claims(token, manifest, launcher, now=now)


def validate_a2_proof_claims(
    claims: Any,
    manifest: ApprovedManifest,
    claim: ClaimedReceipt,
    now: datetime | None = None,
) -> A2RecoveryProof:
    """核对 A2 proof 的独立 issuer、目标四元组、VERIFIED 状态及 manifest/claim 绑定。"""
    proof = require_exact_object(
        claims,
        "A2 proof",
        {
            "proof_version",
            "issuer",
            "proof_id",
            "manifest_sha256",
            "claim_id",
            "claim_token_sha256",
            "issued_at_utc",
            "expires_at_utc",
            "target_quartet",
            "recovery",
        },
    )
    if isinstance(proof["proof_version"], bool) or proof["proof_version"] != 1:
        raise ValidationError("A2 proof version 非法")
    if proof["issuer"] != A2_PROOF_ISSUER:
        raise ValidationError("A2 proof issuer 非法")
    if require_string(proof["proof_id"], "A2 proof.proof_id", UUID) != manifest.a2_proof_id:
        raise ValidationError("A2 proof 未绑定 manifest proof id")
    if require_hash(proof["manifest_sha256"], "A2 proof.manifest_sha256") != manifest.manifest_sha256:
        raise ValidationError("A2 proof 未绑定 manifest")
    if require_string(proof["claim_id"], "A2 proof.claim_id", UUID) != claim.claim_id:
        raise ValidationError("A2 proof 未绑定在线 claim")
    if require_hash(proof["claim_token_sha256"], "A2 proof.claim_token_sha256") != claim.token_sha256:
        raise ValidationError("A2 proof 未绑定 claim token bytes")
    quartet = require_exact_object(
        proof["target_quartet"],
        "A2 proof.target_quartet",
        {"server_uuid", "server_hostname", "connect_port", "schema"},
    )
    if (
        require_string(quartet["server_uuid"], "A2 proof.target.server_uuid", UUID) != manifest.server_uuid
        or require_string(quartet["server_hostname"], "A2 proof.target.server_hostname", SERVER_HOSTNAME)
        != manifest.server_hostname
        or isinstance(quartet["connect_port"], bool)
        or quartet["connect_port"] != manifest.connect_port
        or require_string(quartet["schema"], "A2 proof.target.schema", SCHEMA) != manifest.schema
    ):
        raise ValidationError("A2 proof 目标四元组未绑定 manifest")
    recovery = require_exact_object(
        proof["recovery"],
        "A2 proof.recovery",
        {"reference", "state", "recovery_point_sha256"},
    )
    if (
        require_string(recovery["reference"], "A2 proof.recovery.reference", TICKET) != manifest.recovery_reference
        or recovery["state"] != "VERIFIED"
        or require_hash(recovery["recovery_point_sha256"], "A2 proof.recovery.recovery_point_sha256")
        != manifest.recovery_sha256
    ):
        raise ValidationError("A2 proof 恢复点未处于 VERIFIED 或未绑定 recovery point hash")
    current = now or datetime.now(timezone.utc)
    start, end = validate_time_window(
        proof["issued_at_utc"],
        proof["expires_at_utc"],
        "A2 proof",
        MAX_A2_PROOF_TTL_SECONDS,
        current,
    )
    manifest_start = _parse_utc(manifest.not_before, "manifest.not_before_utc")
    manifest_end = _parse_utc(manifest.expiry, "manifest.expires_at_utc")
    claim_end = _parse_utc(claim.expiry, "receipt claim token.expires_at_utc")
    if start < manifest_start or end > manifest_end or end > claim_end:
        raise ValidationError("A2 proof 时间未绑定 manifest/claim")
    return A2RecoveryProof(proof["proof_id"], manifest.a2_proof_sha256, proof["expires_at_utc"])


def load_and_validate_a2_recovery_proof(manifest: ApprovedManifest, claim: ClaimedReceipt) -> A2RecoveryProof:
    """同一 proof FD bytes 完成独立验签、严格 JSON、schema、恢复与 claim 绑定校验。"""
    schema = manifest.protocol_contracts.get("a2_proof")
    if not isinstance(schema, dict):
        raise ValidationError("缺少已签名 A2 proof schema")
    proof_path = A2_PROOF_DIRECTORY / f"{manifest.a2_proof_id}.json"
    signature_path = Path(f"{proof_path}.sig")
    public_key_path = DEPLOYMENT_ROOT / A2_PROOF_PUBLIC_KEY_RELATIVE
    with open_trusted_file(proof_path, "A2 proof") as proof_file:
        _verify_bound_hash(proof_file.data, manifest.a2_proof_sha256, "A2 proof")
        with open_trusted_file(signature_path, "A2 proof detached signature") as signature_file:
            with open_trusted_file(public_key_path, "A2 proof public key") as public_key_file:
                _verify_pinned_key(
                    public_key_file,
                    PINNED_A2_PROOF_PUBLIC_KEY_SHA256,
                    "A2 proof public key",
                )
                verify_detached_signature(proof_file.data, signature_file, public_key_file, "A2 proof")
                payload = strict_json_loads(proof_file.data, "A2 proof")
    validate_against_schema(payload, schema, schema, "A2 proof")
    return validate_a2_proof_claims(payload, manifest, claim)


def parse_mysql_defaults_bytes(raw: bytes) -> dict[str, str]:
    """解析唯一 [client]，仅允许 user/password/VERIFY_IDENTITY/固定 CA 四项。"""
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ValidationError("mysql defaults 必须为 UTF-8") from error
    allowed = {"user", "password", "ssl-mode", "ssl-ca"}
    result: dict[str, str] = {}
    section_seen = False
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", ";")):
            continue
        if stripped.startswith("!"):
            raise ValidationError("mysql defaults 禁止 include 指令")
        if stripped.startswith("[") or stripped.endswith("]"):
            if stripped != "[client]" or section_seen:
                raise ValidationError("mysql defaults 只允许唯一 [client] section")
            section_seen = True
            continue
        if not section_seen or "=" not in stripped:
            raise ValidationError("mysql defaults option 格式非法")
        raw_key, value = stripped.split("=", 1)
        key = raw_key.strip().lower().replace("_", "-")
        if not key or key not in allowed or key in result:
            raise ValidationError("mysql defaults 含未授权或重复 option")
        result[key] = value.strip()
    if not section_seen or set(result) != allowed:
        raise ValidationError("mysql defaults 必须恰好含最小 client/TLS option")
    if not result["user"] or result["ssl-mode"] != "VERIFY_IDENTITY" or result["ssl-ca"] != str(MYSQL_CA_FILE):
        raise ValidationError("mysql defaults client/TLS 约束非法")
    return result


def validate_defaults_file() -> Path:
    """验证固定凭据文件和固定 CA；解析中不会输出 user/password 值。"""
    parse_mysql_defaults_bytes(read_trusted_file_bytes(DEFAULTS_FILE, "mysql defaults"))
    read_trusted_file_bytes(MYSQL_CA_FILE, "mysql CA")
    return DEFAULTS_FILE


def require_login_path_isolation() -> None:
    """证明当前执行账户本身及 child HOME 都是 root 管理的空目录。"""
    account = pwd.getpwuid(os.geteuid())
    if Path(account.pw_dir) != EMPTY_EXECUTOR_HOME:
        raise ValidationError("执行账户 home 不是固定受控空目录")
    descriptor = _open_trusted_path(EMPTY_EXECUTOR_HOME, "empty executor home", final_directory=True)
    try:
        if os.listdir(descriptor):
            raise ValidationError("执行账户 home 非空，无法排除 login path")
    finally:
        os.close(descriptor)


def require_approved_mysql_binary(manifest: ApprovedManifest) -> None:
    """仅 hash 固定 mysql bytes；版本、SBOM、manpage 和 option 契约均由签名 release 冻结。"""
    contract = manifest.mysql_release_contract
    if not isinstance(contract, dict):
        raise ValidationError("缺少已签名 mysql release contract")
    expected = require_hash(contract.get("sha256"), "mysql release contract.sha256")
    actual = sha256_bytes(read_trusted_file_bytes(MYSQL_PATH, "tool mysql"))
    if actual != expected:
        raise ValidationError("tool mysql SHA-256 不匹配")


def sql_literal(value: str, label: str) -> str:
    """对输入 mysql stdin 的 manifest 值再次白名单校验。"""
    patterns = {
        "server_uuid": UUID,
        "server_hostname": SERVER_HOSTNAME,
        "connect_port": re.compile(r"^[1-9][0-9]{0,4}$"),
        "schema": SCHEMA,
        "change_ticket": TICKET,
        "manifest_sha256": HEX64,
    }
    if not patterns[label].fullmatch(value):
        raise ValidationError(f"注入 SQL 的 {label} 不符合白名单")
    return "'" + value.replace("'", "''").replace("\\", "\\\\") + "'"


def build_mysql_stdin(manifest: ApprovedManifest) -> bytes:
    """将同一已审 SQL bytes 固定拼接两轮到同一 mysql stdin，绝不重新按路径加载。"""
    variables = (
        ("approved_target_server_uuid", manifest.server_uuid, "server_uuid"),
        ("approved_target_hostname", manifest.server_hostname, "server_hostname"),
        ("approved_target_port", str(manifest.connect_port), "connect_port"),
        ("approved_target_schema", manifest.schema, "schema"),
        ("approved_change_ticket", manifest.change_ticket, "change_ticket"),
        ("approved_manifest_sha256", manifest.manifest_sha256, "manifest_sha256"),
    )
    header = [b"-- controlled SQL batch runner: signed values only"]
    header.extend(
        f"SET @{name} = {sql_literal(value, label)};".encode("ascii") for name, value, label in variables
    )
    output = b"\n".join(header) + b"\n"
    for _round in range(2):
        for batch in manifest.sql_batches:
            if not isinstance(batch, bytes) or not batch.endswith(b"\n"):
                raise ValidationError("已审 SQL bytes 不是可安全拼接的完整批次")
            output += batch
    return output


def build_mysql_command(manifest: ApprovedManifest, defaults_file: Path) -> list[str]:
    """构造冻结的 TCP/VERIFY_IDENTITY/binary-mode 单进程 argv，不允许 caller 覆盖连接或凭据。"""
    if defaults_file != DEFAULTS_FILE:
        raise ValidationError("mysql defaults 路径不是固定受控文件")
    command = [
        str(MYSQL_PATH),
        f"--defaults-file={DEFAULTS_FILE}",
        "--protocol=TCP",
        "--ssl-mode=VERIFY_IDENTITY",
        f"--ssl-ca={MYSQL_CA_FILE}",
        "--batch",
        "--binary-mode",
        "--skip-reconnect",
        f"--host={manifest.connect_host}",
        f"--port={manifest.connect_port}",
        f"--database={manifest.schema}",
    ]
    forbidden_prefixes = ("--force", "--init-command", "--execute", "--login-path", "--defaults-extra-file")
    if any(argument == "--no-defaults" or argument.startswith(forbidden_prefixes) for argument in command):
        raise ValidationError("内部 mysql argv 含禁止 option")
    if command.count("--binary-mode") != 1:
        raise ValidationError("内部 mysql argv 未固定 binary-mode")
    return command


def execute_mysql_once(manifest: ApprovedManifest) -> int:
    """只启动一次固定 mysql；stdin 内固定恰好两轮，非零退出不重连、不重试。"""
    require_approved_mysql_binary(manifest)
    require_login_path_isolation()
    defaults_file = validate_defaults_file()
    command = build_mysql_command(manifest, defaults_file)
    stdin = build_mysql_stdin(manifest)
    print(f"EXECUTION START: change_ticket={manifest.change_ticket}; one mysql process; fixed two rounds")
    completed = subprocess.run(command, input=stdin, env=MYSQL_EXECUTION_ENV, check=False)
    if completed.returncode != 0:
        print(f"FAIL-CLOSED: mysql exited {completed.returncode}; no reconnect/retry/second process", file=sys.stderr)
    return completed.returncode


def require_execution_environment(manifest: ApprovedManifest) -> LauncherProof:
    """证明当前进程来自 root-managed launcher；代码无法证明时明确 fail-close。"""
    if SCRIPT_PATH != DEPLOYED_RUNNER_PYTHON:
        raise ValidationError(
            "--execute 缺少 root-managed launcher 部署证明：runner 不在固定 release，拒绝执行"
        )
    if os.geteuid() == TRUSTED_OWNER_UID:
        raise ValidationError("执行账户不得拥有信任根")
    require_trusted_directory(DEPLOYMENT_ROOT, "deployment root")
    require_approved_binary(PYTHON_TOOL)
    require_approved_binary(OPENSSL_TOOL)
    require_login_path_isolation()
    return load_root_launcher_proof(manifest)


def run_static_guard_checker() -> None:
    """validate-only 只可启动固定 Python/checker；它不启动 mysql。"""
    require_approved_binary(PYTHON_TOOL)
    completed = subprocess.run(
        [str(PYTHON_PATH), "-I", str(DEPLOYED_CHECKER_PYTHON)],
        cwd=str(DEPLOYMENT_ROOT),
        env=MYSQL_EXECUTION_ENV,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if completed.returncode != 0:
        raise ValidationError("静态 target-identity preflight checker 未通过")


def execute_approved_manifest(manifest: ApprovedManifest) -> int:
    """依序要求 launcher proof、在线一次性 claim、A2 proof，最后只启动一次 mysql。"""
    launcher = require_execution_environment(manifest)
    claim = claim_receipt_atomically(manifest, launcher)
    load_and_validate_a2_recovery_proof(manifest, claim)
    return execute_mysql_once(manifest)


def main() -> int:
    """CLI 入口：validate-only 从不启动 mysql，execute 无降级轮次入口。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--validate-only", action="store_true")
    mode.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    try:
        manifest = load_and_validate_manifest(args.manifest)
        run_static_guard_checker()
        print(
            "VALIDATION PASS: "
            f"change_ticket={manifest.change_ticket}; expires_at_utc={manifest.expiry}; "
            f"approved_manifest_sha256={manifest.manifest_sha256}"
        )
        if args.validate_only:
            return 0
        return execute_approved_manifest(manifest)
    except ValidationError as error:
        print(f"FAIL-CLOSED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
