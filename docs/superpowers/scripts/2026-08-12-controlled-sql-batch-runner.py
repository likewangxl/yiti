#!/usr/bin/python3.10
"""受控 SQL batch runner：默认验证，执行时严格 fail-close。

仓库不是执行信任根。只有已部署到固定、root 管理目录的 release 才可能进入执行路径；
当前仓库故意不附带公钥、审批收据或部署根，因此 ``--execute`` 必须失败且不得启动 mysql。
"""

from __future__ import annotations

import argparse
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import datetime, timezone
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
DEPLOYMENT_RECEIPT_DIRECTORY = DEPLOYMENT_ROOT / "approval-receipts"
EMPTY_EXECUTOR_HOME = Path("/var/empty/controlled-sql-runner")
DEFAULTS_FILE = DEPLOYMENT_ROOT / "credentials/mysql-client.cnf"
MYSQL_CA_FILE = DEPLOYMENT_ROOT / "trust/mysql-ca.pem"

BASH_PATH = Path("/usr/bin/bash")
PYTHON_PATH = Path("/usr/bin/python3.10")
OPENSSL_PATH = Path("/usr/bin/openssl")
MYSQL_PATH = Path("/usr/bin/mysql")
TRUSTED_OWNER_UID = 0

RUNNER_SHELL_RELATIVE = Path("docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.sh")
RUNNER_PYTHON_RELATIVE = Path("docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.py")
CHECKER_SHELL_RELATIVE = Path("docs/superpowers/scripts/2026-08-12-verify-sql-target-identity-guard.sh")
CHECKER_PYTHON_RELATIVE = Path("docs/superpowers/scripts/2026-08-12-verify-sql-target-identity-guard.py")
MANIFEST_SCHEMA_RELATIVE = Path("docs/superpowers/sql/2026-08-12-controlled-sql-batch-manifest.schema.json")
SQL_RELATIVES = (
    Path("docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql"),
    Path("docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql"),
    Path("docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql"),
)
TRUSTED_PUBLIC_KEY_RELATIVE = Path(
    "docs/superpowers/sql/controlled-batch-manifest-trust/controlled-manifest-public.pem"
)
APPROVAL_RECEIPT_PUBLIC_KEY_RELATIVE = Path(
    "docs/superpowers/sql/controlled-batch-manifest-trust/approval-service-receipt-public.pem"
)
ARTIFACT_RELATIVES = {
    "runner_shell": RUNNER_SHELL_RELATIVE,
    "runner_python": RUNNER_PYTHON_RELATIVE,
    "preflight_checker_shell": CHECKER_SHELL_RELATIVE,
    "preflight_checker_python": CHECKER_PYTHON_RELATIVE,
    "manifest_schema": MANIFEST_SCHEMA_RELATIVE,
}
DEPLOYED_RUNNER_PYTHON = DEPLOYMENT_ROOT / RUNNER_PYTHON_RELATIVE
DEPLOYED_CHECKER_PYTHON = DEPLOYMENT_ROOT / CHECKER_PYTHON_RELATIVE
SCRIPT_PATH = Path(__file__)

# 当前仓库刻意未配发实际公钥。全零值不是可用 pin；经过审计的部署映像必须替换为真实 SHA-256。
UNPROVISIONED_FINGERPRINT = "0" * 64
PINNED_MANIFEST_PUBLIC_KEY_SHA256 = UNPROVISIONED_FINGERPRINT
PINNED_APPROVAL_RECEIPT_PUBLIC_KEY_SHA256 = UNPROVISIONED_FINGERPRINT

# mysql 子进程完全替换继承环境，故操作者无法注入 MYSQL_HOME、MYSQL_TEST_LOGIN_FILE 或 login-path。
MYSQL_EXECUTION_ENV = {
    "HOME": str(EMPTY_EXECUTOR_HOME),
    "PATH": "/usr/bin:/bin",
    "LC_ALL": "C",
    "LANG": "C",
}

MANIFEST_SCHEMA_ID = "https://bank.branch.platform/schemas/controlled-sql-batch-manifest-v2.json"
APPROVAL_SERVICE_ID = "bank-controlled-sql-approval-service"
MAX_MANIFEST_TTL_SECONDS = 900
MAX_RECEIPT_TTL_SECONDS = 300

HEX64 = re.compile(r"^[0-9a-f]{64}$", re.IGNORECASE)
UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", re.IGNORECASE)
NONCE = re.compile(r"^[0-9a-f]{32,128}$", re.IGNORECASE)
HOST = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")
HOST_ONE_CHAR = re.compile(r"^[A-Za-z0-9]$")
SERVER_HOSTNAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,252}$")
SCHEMA = re.compile(r"^[A-Za-z0-9_]{1,64}$")
TICKET = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{2,99}$")
MANAGED_MANIFEST_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{2,127}\.json$")
BANNED_MANIFEST_KEY = re.compile(r"password|passwd|secret|credential|private.?key|access.?key|token", re.IGNORECASE)


class ValidationError(RuntimeError):
    """可安全输出的 fail-close 原因；消息不得携带凭据或输入原文。"""


class DuplicateJsonKey(ValueError):
    """严格 JSON 解析中发现重复 object key。"""


@dataclass(frozen=True)
class ToolSpec:
    """受审运行时工具的绝对路径、二进制哈希和版本前缀。"""

    name: str
    path: Path
    expected_sha256: str
    expected_version: str
    version_args: tuple[str, ...]


# 固定值对应获审基础映像；版本或二进制升级必须经过新的部署审计，而非临时改 PATH。
BASH_TOOL = ToolSpec(
    "bash",
    BASH_PATH,
    "59474588a312b6b6e73e5a42a59bf71e62b55416b6c9d5e4a6e1c630c2a9ecd4",
    "GNU bash, version 5.1.16(1)-release",
    ("--version",),
)
PYTHON_TOOL = ToolSpec(
    "python",
    PYTHON_PATH,
    "7d51cd6b48b521277f5caa4610a82126e315fa2be4df069823a8b1eeb5bd4a86",
    "Python 3.10.12",
    ("--version",),
)
OPENSSL_TOOL = ToolSpec(
    "openssl",
    OPENSSL_PATH,
    "a55e3085b6a1df8887722f6cee7fc32c861d11d5fb584a63837d32d29602c65b",
    "OpenSSL 3.0.2 15 Mar 2022",
    ("version",),
)
MYSQL_TOOL = ToolSpec(
    "mysql",
    MYSQL_PATH,
    "c5ec7f8f780c2f88bd462ff68d241fef1fae780226812dd131aff3d842e81ce0",
    "/usr/bin/mysql  Ver 8.0.33",
    ("--version",),
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
    schema_contract: dict[str, Any] = field(default_factory=dict, repr=False, compare=False)


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
    schema = strict_json_loads(raw, "manifest schema")
    if not isinstance(schema, dict) or schema.get("$id") != MANIFEST_SCHEMA_ID or schema.get("type") != "object":
        raise ValidationError("manifest schema 契约不受信任")
    return schema, raw


def require_approved_tool(specification: ToolSpec) -> None:
    """校验固定工具本体、每级 parent、获审哈希与版本，再允许调用。"""
    actual_hash = sha256_bytes(read_trusted_file_bytes(specification.path, f"tool {specification.name}"))
    if actual_hash != specification.expected_sha256:
        raise ValidationError(f"tool {specification.name} SHA-256 不匹配")
    completed = subprocess.run(
        [str(specification.path), *specification.version_args],
        cwd="/",
        env=MYSQL_EXECUTION_ENV,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    output = completed.stdout or ""
    if completed.returncode != 0 or not output.startswith(specification.expected_version):
        raise ValidationError(f"tool {specification.name} version 不匹配")


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
    require_approved_tool(OPENSSL_TOOL)
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


def _verify_manifest_artifacts(payload: dict[str, Any], schema_raw: bytes) -> None:
    artifacts = payload["artifact_sha256"]
    if not isinstance(artifacts, dict) or set(artifacts) != set(ARTIFACT_RELATIVES):
        raise ValidationError("artifact_sha256 字段契约不匹配")
    for label, relative in ARTIFACT_RELATIVES.items():
        item = require_exact_object(artifacts[label], f"artifact_sha256.{label}", {"path", "sha256"})
        if item["path"] != str(relative):
            raise ValidationError(f"artifact_sha256.{label} 路径不在 allow-list")
        actual = schema_raw if relative == MANIFEST_SCHEMA_RELATIVE else read_trusted_file_bytes(
            _deployed_path(relative, f"artifact {label}"), f"artifact {label}"
        )
        _verify_bound_hash(actual, item["sha256"], f"artifact_sha256.{label}")


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

    target = payload["target"]
    recovery = payload["a2_recovery_point"]
    receipt = payload["approval_receipt"]
    if not isinstance(target, dict) or not isinstance(recovery, dict) or not isinstance(receipt, dict):
        raise ValidationError("manifest 嵌套对象类型非法")
    connect_host = require_string(target["connect_host"], "target.connect_host", HOST)
    if not (HOST.fullmatch(connect_host) or HOST_ONE_CHAR.fullmatch(connect_host)):
        raise ValidationError("target.connect_host 格式非法")
    connect_port = target["connect_port"]
    if isinstance(connect_port, bool) or not isinstance(connect_port, int) or not 1 <= connect_port <= 65535:
        raise ValidationError("target.connect_port 必须是 1..65535 整数")

    _verify_manifest_artifacts(payload, schema_raw)
    sql_batches = _load_bound_sql(payload)
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
        recovery_sha256=require_hash(recovery["sha256"], "a2_recovery_point.sha256"),
        recovery_signed_proof_sha256=require_hash(
            recovery["signed_proof_sha256"], "a2_recovery_point.signed_proof_sha256"
        ),
        receipt_id=require_string(receipt["receipt_id"], "approval_receipt.receipt_id", UUID),
        sql_batches=sql_batches,
        schema_contract=schema_contract,
    )


def validate_receipt_claims(claims: Any, manifest: ApprovedManifest, now: datetime | None = None) -> None:
    """核对外部不可伪造收据的 nonce、短 TTL、撤销边界和 A2 签名证明绑定。"""
    receipt = require_exact_object(
        claims,
        "approval receipt",
        {
            "receipt_version",
            "approval_service",
            "receipt_id",
            "manifest_sha256",
            "change_ticket",
            "issued_at_utc",
            "not_before_utc",
            "expires_at_utc",
            "execution_nonce",
            "nonce_policy",
            "revocation_epoch",
            "a2_recovery_point",
        },
    )
    if isinstance(receipt["receipt_version"], bool) or receipt["receipt_version"] != 1:
        raise ValidationError("approval receipt version 非法")
    if receipt["approval_service"] != APPROVAL_SERVICE_ID or receipt["nonce_policy"] != "single_use":
        raise ValidationError("approval receipt service 或 nonce policy 非法")
    if require_string(receipt["receipt_id"], "approval receipt.id", UUID) != manifest.receipt_id:
        raise ValidationError("approval receipt 未绑定 manifest receipt id")
    if require_hash(receipt["manifest_sha256"], "approval receipt.manifest_sha256") != manifest.manifest_sha256:
        raise ValidationError("approval receipt 未绑定 manifest hash")
    if require_string(receipt["change_ticket"], "approval receipt.change_ticket", TICKET) != manifest.change_ticket:
        raise ValidationError("approval receipt 未绑定 change ticket")
    if require_string(receipt["execution_nonce"], "approval receipt.execution_nonce", NONCE) != manifest.execution_nonce:
        raise ValidationError("approval receipt 未绑定唯一 nonce")
    require_string(receipt["revocation_epoch"], "approval receipt.revocation_epoch", TICKET)

    current = now or datetime.now(timezone.utc)
    issued = _parse_utc(receipt["issued_at_utc"], "approval receipt.issued_at_utc")
    receipt_start, receipt_end = validate_time_window(
        receipt["not_before_utc"],
        receipt["expires_at_utc"],
        "approval receipt",
        MAX_RECEIPT_TTL_SECONDS,
        current,
    )
    manifest_start = _parse_utc(manifest.not_before, "manifest.not_before_utc")
    manifest_end = _parse_utc(manifest.expiry, "manifest.expires_at_utc")
    if issued > receipt_start or issued > current or receipt_start < manifest_start or receipt_end > manifest_end:
        raise ValidationError("approval receipt 时间边界未严格绑定 manifest")

    recovery = require_exact_object(
        receipt["a2_recovery_point"],
        "approval receipt.a2_recovery_point",
        {"reference", "sha256", "signed_proof_sha256"},
    )
    if (
        require_string(recovery["reference"], "approval receipt.a2.reference", TICKET) != manifest.recovery_reference
        or require_hash(recovery["sha256"], "approval receipt.a2.sha256") != manifest.recovery_sha256
        or require_hash(recovery["signed_proof_sha256"], "approval receipt.a2.signed_proof_sha256")
        != manifest.recovery_signed_proof_sha256
    ):
        raise ValidationError("approval receipt 未绑定 A2 恢复点签名证明")


def validate_execution_receipt(manifest: ApprovedManifest) -> None:
    """验签独立审批服务收据；本地无法证明撤销/去重时绝不以文档替代该边界。"""
    definitions = manifest.schema_contract.get("$defs", {}) if isinstance(manifest.schema_contract, dict) else {}
    receipt_schema = definitions.get("approvalReceiptPayload")
    if not isinstance(receipt_schema, dict):
        raise ValidationError("已加载 manifest schema 缺少 approval receipt 契约")
    receipt_path = DEPLOYMENT_RECEIPT_DIRECTORY / f"{manifest.receipt_id}.json"
    signature_path = Path(f"{receipt_path}.sig")
    public_key_path = DEPLOYMENT_ROOT / APPROVAL_RECEIPT_PUBLIC_KEY_RELATIVE
    with open_trusted_file(receipt_path, "approval receipt") as receipt_file:
        with open_trusted_file(signature_path, "approval receipt detached signature") as signature_file:
            with open_trusted_file(public_key_path, "approval receipt public key") as public_key_file:
                _verify_pinned_key(
                    public_key_file,
                    PINNED_APPROVAL_RECEIPT_PUBLIC_KEY_SHA256,
                    "approval receipt public key",
                )
                verify_detached_signature(receipt_file.data, signature_file, public_key_file, "approval receipt")
                payload = strict_json_loads(receipt_file.data, "approval receipt")
    validate_against_schema(payload, receipt_schema, manifest.schema_contract, "approval receipt")
    validate_receipt_claims(payload, manifest)


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


def verify_mysql_option_contract() -> None:
    """基于已批准 mysql --help 的本机契约确认 only-defaults-file 语义仍存在。"""
    require_approved_tool(MYSQL_TOOL)
    completed = subprocess.run(
        [str(MYSQL_PATH), "--no-defaults", "--help"],
        cwd="/",
        env=MYSQL_EXECUTION_ENV,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    help_text = completed.stdout or ""
    if (
        completed.returncode != 0
        or "--defaults-file" not in help_text
        or "Use only the given option file" not in help_text
    ):
        raise ValidationError("mysql 本机 option-file 契约不可证明")


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
    """构造固定 TCP/VERIFY_IDENTITY 单进程 argv，不允许 caller 覆盖连接或凭据。"""
    if defaults_file != DEFAULTS_FILE:
        raise ValidationError("mysql defaults 路径不是固定受控文件")
    command = [
        str(MYSQL_PATH),
        f"--defaults-file={DEFAULTS_FILE}",
        "--protocol=TCP",
        "--ssl-mode=VERIFY_IDENTITY",
        f"--ssl-ca={MYSQL_CA_FILE}",
        "--batch",
        "--skip-reconnect",
        f"--host={manifest.connect_host}",
        f"--port={manifest.connect_port}",
        f"--database={manifest.schema}",
    ]
    forbidden_prefixes = ("--force", "--init-command", "--execute", "--login-path", "--defaults-extra-file")
    if any(argument == "--no-defaults" or argument.startswith(forbidden_prefixes) for argument in command):
        raise ValidationError("内部 mysql argv 含禁止 option")
    return command


def execute_mysql_once(manifest: ApprovedManifest) -> int:
    """只启动一次固定 mysql；stdin 内固定恰好两轮，非零退出不重连、不重试。"""
    require_login_path_isolation()
    verify_mysql_option_contract()
    defaults_file = validate_defaults_file()
    command = build_mysql_command(manifest, defaults_file)
    stdin = build_mysql_stdin(manifest)
    print(f"EXECUTION START: change_ticket={manifest.change_ticket}; one mysql process; fixed two rounds")
    completed = subprocess.run(command, input=stdin, env=MYSQL_EXECUTION_ENV, check=False)
    if completed.returncode != 0:
        print(f"FAIL-CLOSED: mysql exited {completed.returncode}; no reconnect/retry/second process", file=sys.stderr)
    return completed.returncode


def require_execution_environment() -> None:
    """执行前验证部署根、执行主体、绝对工具及其版本；当前仓库必然无法通过。"""
    if SCRIPT_PATH != DEPLOYED_RUNNER_PYTHON:
        raise ValidationError("--execute 只能由固定受信部署根内的 runner 启动")
    if os.geteuid() == TRUSTED_OWNER_UID:
        raise ValidationError("执行账户不得拥有信任根")
    require_trusted_directory(DEPLOYMENT_ROOT, "deployment root")
    require_approved_tool(BASH_TOOL)
    require_approved_tool(PYTHON_TOOL)
    require_approved_tool(OPENSSL_TOOL)
    require_approved_tool(MYSQL_TOOL)
    require_login_path_isolation()


def run_static_guard_checker() -> None:
    """静态 checker 只经绝对 Python 调用；它不参与 SQL 字节的二次读取。"""
    require_approved_tool(PYTHON_TOOL)
    completed = subprocess.run(
        [str(PYTHON_PATH), str(DEPLOYED_CHECKER_PYTHON)],
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
    """先验证不可替代的部署/外部审批边界，任何失败均不得抵达 mysql。"""
    require_execution_environment()
    validate_execution_receipt(manifest)
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
