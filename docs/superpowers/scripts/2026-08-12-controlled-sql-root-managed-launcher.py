#!/usr/bin/python3.10
"""root-managed execute launcher 模板。

只能由 bank-controlled-sql-batch-runner@.service 以 root 启动。它读取 root-only
attestation 私钥，签发仅对当前 exec PID/starttime 有效的短时 proof，并降权到 nologin
service account 后以固定 Python isolated mode 启动 runner。仓库不具备该部署私钥或 unit，
因此本文件不是本地 execute 入口。
"""

from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
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


DEPLOYMENT_ROOT = Path("/opt/bank/controlled-sql-batch-runner/release")
ROOT_LAUNCHER_PATH = DEPLOYMENT_ROOT / "docs/superpowers/scripts/2026-08-12-controlled-sql-root-managed-launcher.py"
RUNNER_PATH = DEPLOYMENT_ROOT / "docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.py"
SYSTEMD_UNIT_PATH = Path("/etc/systemd/system/bank-controlled-sql-batch-runner@.service")
MANIFEST_DIRECTORY = DEPLOYMENT_ROOT / "approved-manifests"
ATTESTATION_PRIVATE_KEY = DEPLOYMENT_ROOT / "root-only/launcher-attestation-private.pem"
CLAIM_HELPER_PATH = Path("/usr/local/libexec/bank-controlled-sql-receipt-claim-helper")
PYTHON_PATH = Path("/usr/bin/python3.10")
OPENSSL_PATH = Path("/usr/bin/openssl")
SERVICE_ACCOUNT = "controlled-sql-runner"
NOLOGIN_SHELL = Path("/usr/sbin/nologin")
SYSTEMD_INVOCATION_ID = "INVOCATION_ID"
LAUNCHER_PROOF_FD = 198
LAUNCHER_PROOF_SIGNATURE_FD = 199
ROOT_LAUNCHER_PROOF_ISSUER = "bank-controlled-sql-root-launcher"
MANIFEST_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$")
INVOCATION_ID = re.compile(r"^[0-9a-f]{32}$", re.IGNORECASE)
STRICT_ENV = {
    "HOME": "/var/empty/controlled-sql-runner",
    "PATH": "/usr/bin:/bin",
    "LC_ALL": "C",
    "LANG": "C",
    "LANGUAGE": "C",
    "TZ": "UTC",
}
FORBIDDEN_ENV = {
    "BASH_ENV",
    "ENV",
    "LD_PRELOAD",
    "LD_LIBRARY_PATH",
    "LD_AUDIT",
    "LD_DEBUG",
    "PYTHONHOME",
    "PYTHONPATH",
    "PYTHONSTARTUP",
    "PYTHONUSERBASE",
    "PYTHONINSPECT",
    "PYTHONWARNINGS",
    "PYTHONHASHSEED",
    "PYTHONIOENCODING",
    "PYTHONUTF8",
    "LC_CTYPE",
    "LC_COLLATE",
    "LC_MESSAGES",
    "LC_MONETARY",
    "LC_NUMERIC",
    "LC_TIME",
}


class LauncherError(RuntimeError):
    """不回显环境、manifest 或私钥内容的 launcher fail-close。"""


def fail(message: str) -> None:
    raise LauncherError(message)


def _validate_root_only_private_key_parent(metadata: os.stat_result, label: str) -> None:
    """私钥祖先目录必须只允许 root 读取或遍历，避免 service account 发现或访问 key。"""
    if not stat.S_ISDIR(metadata.st_mode) or metadata.st_uid != 0:
        fail(f"{label} 父目录不受信任")
    if stat.S_IMODE(metadata.st_mode) & 0o077:
        fail(f"{label} 父目录不是 root-only")


def _validate_attestation_private_key(metadata: os.stat_result, label: str) -> None:
    """attestation 私钥只能是 root-owned regular 0400/0600 风格文件。"""
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != 0:
        fail(f"{label} 不受信任")
    if stat.S_IMODE(metadata.st_mode) & 0o077:
        fail(f"{label} 不是 root-only")


@contextmanager
def open_attestation_private_key():
    """用逐级 O_NOFOLLOW 打开的同一 fd 向 openssl 提供 root-only attestation 私钥。"""
    path = ATTESTATION_PRIVATE_KEY
    if not path.is_absolute() or ".." in path.parts:
        fail("launcher attestation private key 路径非法")
    base_flags = os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW
    parent_descriptors: list[int] = []
    private_key_descriptor: int | None = None
    try:
        root_descriptor = os.open("/", base_flags | os.O_DIRECTORY)
        parent_descriptors.append(root_descriptor)
        root_metadata = os.fstat(root_descriptor)
        if not stat.S_ISDIR(root_metadata.st_mode) or root_metadata.st_uid != 0:
            fail("launcher attestation private key 根目录不受信任")
        # 根目录是受内核保护的路径锚点；其后的每一级私钥祖先都必须没有 group/other 权限。
        for component in path.parts[1:-1]:
            descriptor = os.open(component, base_flags | os.O_DIRECTORY, dir_fd=parent_descriptors[-1])
            parent_descriptors.append(descriptor)
            _validate_root_only_private_key_parent(
                os.fstat(descriptor),
                "launcher attestation private key",
            )
        private_key_descriptor = os.open(path.name, base_flags, dir_fd=parent_descriptors[-1])
        _validate_attestation_private_key(
            os.fstat(private_key_descriptor),
            "launcher attestation private key",
        )
        yield private_key_descriptor
    except OSError as error:
        raise LauncherError("launcher attestation private key 无法以 root-only 描述符打开") from error
    finally:
        if private_key_descriptor is not None:
            try:
                os.close(private_key_descriptor)
            except OSError:
                pass
        for descriptor in reversed(parent_descriptors):
            try:
                os.close(descriptor)
            except OSError:
                pass


def sha256_root_file(path: Path, label: str) -> str:
    """只读取 root-owned 常规文件，部署路径中任一非 root 可写段都拒绝。"""
    if not path.is_absolute() or ".." in path.parts:
        fail(f"{label} 路径非法")
    directory = Path("/")
    for component in path.parts[1:-1]:
        directory = directory / component
        metadata = os.lstat(directory)
        if not stat.S_ISDIR(metadata.st_mode) or metadata.st_uid != 0 or stat.S_IMODE(metadata.st_mode) & 0o022:
            fail(f"{label} 父目录不受信任")
    descriptor = os.open(path, os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW)
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != 0 or stat.S_IMODE(metadata.st_mode) & 0o022:
            fail(f"{label} 不受信任")
        content = bytearray()
        while True:
            fragment = os.read(descriptor, 1024 * 1024)
            if not fragment:
                break
            content.extend(fragment)
        return hashlib.sha256(content).hexdigest()
    finally:
        os.close(descriptor)


def root_file_bytes(path: Path, label: str) -> bytes:
    """读取时复用 root ownership 检查，且不向标准输出泄漏 bytes。"""
    sha256_root_file(path, label)
    descriptor = os.open(path, os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW)
    try:
        content = bytearray()
        while True:
            fragment = os.read(descriptor, 1024 * 1024)
            if not fragment:
                break
            content.extend(fragment)
        return bytes(content)
    finally:
        os.close(descriptor)


def current_pid_start_time() -> str:
    raw = Path("/proc/self/stat").read_text(encoding="ascii")
    fields = raw[raw.rfind(")") + 2 :].split()
    if len(fields) <= 19 or not fields[19].isdigit():
        fail("无法读取 PID starttime")
    return fields[19]


def require_systemd_context(manifest_id: str) -> None:
    """普通用户即使伪造环境也不能执行 root-only launcher；再校验 unit cgroup 以发现部署漂移。"""
    if os.geteuid() != 0 or Path(__file__) != ROOT_LAUNCHER_PATH:
        fail("launcher 不是受信 root 部署实例")
    invocation = os.environ.get(SYSTEMD_INVOCATION_ID, "")
    if not INVOCATION_ID.fullmatch(invocation):
        fail("缺少 systemd invocation 证明")
    try:
        cgroup = Path("/proc/self/cgroup").read_text(encoding="utf-8")
    except OSError as error:
        raise LauncherError("无法证明 systemd unit cgroup") from error
    if "bank-controlled-sql-batch-runner@" not in cgroup:
        fail("launcher 不在受控 systemd unit cgroup")
    if not MANIFEST_ID.fullmatch(manifest_id):
        fail("manifest 标识非法")
    for name in FORBIDDEN_ENV:
        if name in os.environ:
            fail("unit 未在解释器前清除危险环境")
    for name, expected in STRICT_ENV.items():
        if os.environ.get(name) != expected:
            fail("unit 净化环境契约不匹配")


def manifest_helper_hash(manifest_path: Path) -> str:
    """仅提取将被 runner 重新验签的 helper hash，用于在 launcher proof 中绑定 release。"""
    try:
        payload = json.loads(root_file_bytes(manifest_path, "manifest").decode("utf-8"))
        helper = payload["receipt_claim_helper"]
        value = helper["sha256"]
    except (KeyError, TypeError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise LauncherError("manifest 无法提供 claim helper hash") from error
    if not isinstance(value, str) or not re.fullmatch(r"[0-9A-Fa-f]{64}", value) or value == "0" * 64:
        fail("manifest claim helper hash 未获部署批准")
    return value.lower()


def sign_payload(payload: bytes) -> bytes:
    """仅将同一 root-only 私钥 FD 交给固定 openssl，禁止验前检查后按路径重开。"""
    with open_attestation_private_key() as private_key_descriptor:
        completed = subprocess.run(
            [
                str(OPENSSL_PATH),
                "dgst",
                "-sha256",
                "-sign",
                f"/proc/self/fd/{private_key_descriptor}",
            ],
            cwd="/",
            env=STRICT_ENV,
            input=payload,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            pass_fds=(private_key_descriptor,),
        )
    if completed.returncode != 0 or not completed.stdout:
        fail("root attestation signer 不可用")
    return completed.stdout


def sealed_memfd(label: str, data: bytes) -> int:
    """创建不可写、不可增缩且不可去 seal 的匿名 fd，runner 仅接受这种 proof 载体。"""
    descriptor = os.memfd_create(label, os.MFD_ALLOW_SEALING | os.MFD_CLOEXEC)
    os.write(descriptor, data)
    os.lseek(descriptor, 0, os.SEEK_SET)
    seals = fcntl.F_SEAL_WRITE | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_SEAL
    fcntl.fcntl(descriptor, fcntl.F_ADD_SEALS, seals)
    return descriptor


def drop_and_exec(manifest_path: Path, proof: bytes, signature: bytes) -> None:
    """在同一 PID 保留密封 proof，drop 到无登录 shell 的 service account 后 exec 固定 runner。"""
    account = pwd.getpwnam(SERVICE_ACCOUNT)
    if Path(account.pw_dir) != Path(STRICT_ENV["HOME"]) or Path(account.pw_shell) != NOLOGIN_SHELL:
        fail("service account 不是固定 nologin/empty-home 账户")
    proof_fd = sealed_memfd("controlled-sql-launcher-proof", proof)
    signature_fd = sealed_memfd("controlled-sql-launcher-proof-signature", signature)
    try:
        os.dup2(proof_fd, LAUNCHER_PROOF_FD, inheritable=True)
        os.dup2(signature_fd, LAUNCHER_PROOF_SIGNATURE_FD, inheritable=True)
        os.set_inheritable(LAUNCHER_PROOF_FD, True)
        os.set_inheritable(LAUNCHER_PROOF_SIGNATURE_FD, True)
        os.setgroups([])
        os.setgid(account.pw_gid)
        os.setuid(account.pw_uid)
        if os.geteuid() == 0 or os.getegid() == 0:
            fail("launcher 未能降权到 service account")
        os.execve(
            str(PYTHON_PATH),
            [
                str(PYTHON_PATH),
                "-I",
                str(RUNNER_PATH),
                "--manifest",
                str(manifest_path),
                "--execute",
            ],
            STRICT_ENV,
        )
    finally:
        for descriptor in (proof_fd, signature_fd):
            try:
                os.close(descriptor)
            except OSError:
                pass


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest-id", required=True)
    args = parser.parse_args()
    try:
        require_systemd_context(args.manifest_id)
        manifest_path = MANIFEST_DIRECTORY / f"{args.manifest_id}.json"
        manifest_sha256 = sha256_root_file(manifest_path, "manifest")
        now = datetime.now(timezone.utc)
        proof = {
            "proof_version": 1,
            "issuer": ROOT_LAUNCHER_PROOF_ISSUER,
            "launch_id": os.urandom(16).hex()[0:8] + "-0000-4000-8000-000000000000",
            "mode": "execute",
            "manifest_sha256": manifest_sha256,
            "subject_uid": pwd.getpwnam(SERVICE_ACCOUNT).pw_uid,
            "pid": os.getpid(),
            "pid_start_time": current_pid_start_time(),
            "issued_at_utc": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "expires_at_utc": (now + timedelta(seconds=60)).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "runner_sha256": sha256_root_file(RUNNER_PATH, "runner"),
            "root_launcher_sha256": sha256_root_file(ROOT_LAUNCHER_PATH, "launcher"),
            "systemd_unit_sha256": sha256_root_file(SYSTEMD_UNIT_PATH, "systemd unit"),
            "claim_helper_sha256": manifest_helper_hash(manifest_path),
        }
        payload = json.dumps(proof, sort_keys=True, separators=(",", ":")).encode("utf-8")
        drop_and_exec(manifest_path, payload, sign_payload(payload))
        return 70
    except LauncherError as error:
        print(f"FAIL-CLOSED: {error}", file=sys.stderr)
        return 64


if __name__ == "__main__":
    raise SystemExit(main())
