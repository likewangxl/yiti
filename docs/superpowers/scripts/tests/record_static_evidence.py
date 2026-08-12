#!/usr/bin/env python3
"""为无网络、无数据库的静态测试生成不可手填的原始执行记录。"""

from __future__ import annotations

import argparse
import hashlib
import shlex
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--hash", action="append", type=Path, default=[])
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command
    if command[:1] == ["--"]:
        command = command[1:]
    if not command:
        parser.error("必须在 -- 后提供要记录的静态测试命令")

    lines = [
        "STATIC-EVIDENCE-V1",
        f"UTC_STARTED {utc_now()}",
        f"COMMAND {shlex.join(command)}",
    ]
    for path in args.hash:
        resolved = path.resolve()
        if not resolved.is_file():
            parser.error(f"--hash 目标不存在: {path}")
        lines.append(f"SHA256 {sha256(resolved)} {resolved}")

    completed = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    lines.append(f"EXIT_CODE {completed.returncode}")
    lines.append(f"UTC_FINISHED {utc_now()}")
    lines.append("--- RAW_STDOUT_STDERR ---")
    lines.append(completed.stdout.rstrip("\n"))
    record = "\n".join(lines) + "\n"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(record, encoding="utf-8", newline="\n")
    sys.stdout.write(record)
    return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())
