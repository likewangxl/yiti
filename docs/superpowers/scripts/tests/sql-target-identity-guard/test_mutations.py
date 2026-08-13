#!/usr/bin/env python3
"""SQL target-identity guard 的静态 mutation 测试。

此脚本只复制/改写临时文本并调用静态 checker；绝不创建数据库连接或执行 SQL。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


THIS_FILE = Path(__file__).resolve()
REPO_ROOT = THIS_FILE.parents[5]
CONFIG_PATH = THIS_FILE.with_name("mutation-fixtures.json")
DEFAULT_CHECKER = REPO_ROOT / "docs/superpowers/scripts/2026-08-12-verify-sql-target-identity-guard.sh"
SQL_PATHS = (
    "docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql",
    "docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql",
    "docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql",
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_fixtures() -> list[dict[str, object]]:
    payload = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    if payload.get("schema_version") != 1:
        raise ValueError("mutation fixture schema_version 必须为 1")
    fixtures = payload.get("fixtures")
    if not isinstance(fixtures, list) or not fixtures:
        raise ValueError("mutation fixture 列表不能为空")
    return fixtures


def materialize_fixture(spec: dict[str, object], destination: Path) -> Path:
    source_name = spec["source"]
    if not isinstance(source_name, str):
        raise ValueError(f"fixture {spec.get('name')} 缺少 source")
    source = REPO_ROOT / source_name
    text = source.read_text(encoding="utf-8")
    patches = spec["patches"]
    if not isinstance(patches, list) or not patches:
        raise ValueError(f"fixture {spec.get('name')} 缺少 patches")
    for patch in patches:
        if not isinstance(patch, dict):
            raise ValueError(f"fixture {spec.get('name')} patch 格式错误")
        old = patch.get("old")
        new = patch.get("new")
        if not isinstance(old, str) or not isinstance(new, str):
            raise ValueError(f"fixture {spec.get('name')} patch 必须含文本 old/new")
        occurrences = text.count(old)
        if occurrences != 1:
            raise ValueError(
                f"fixture {spec.get('name')} 的替换锚点必须精确命中一次，实际 {occurrences}: {old!r}"
            )
        text = text.replace(old, new, 1)
    destination.write_text(text, encoding="utf-8", newline="\n")
    return destination


def result_output(result: subprocess.CompletedProcess[str]) -> str:
    return result.stdout.replace("\r\n", "\n").rstrip()


def run_legacy_acceptance(checker: Path, fixtures: list[dict[str, object]]) -> int:
    """以预加固 checker 的目录结构逐一执行 fixture，证明它会错误放行。"""
    failures = 0
    print("MODE legacy-acceptance (必须证明旧 checker 错误接受每个 mutation fixture)")
    print(f"CHECKER_SHA256 {sha256(checker)} {checker}")
    with tempfile.TemporaryDirectory(prefix="sql-guard-legacy-") as temp:
        temp_root = Path(temp) / "repo"
        legacy_checker = temp_root / "docs/superpowers/scripts/2026-08-12-verify-sql-target-identity-guard.sh"
        legacy_checker.parent.mkdir(parents=True, exist_ok=True)
        for sql_name in SQL_PATHS:
            target = temp_root / sql_name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(REPO_ROOT / sql_name, target)
        shutil.copy2(checker, legacy_checker)

        for spec in fixtures:
            name = spec.get("name")
            source_name = spec.get("source")
            if not isinstance(name, str) or not isinstance(source_name, str):
                raise ValueError("fixture name/source 格式错误")
            target = temp_root / source_name
            materialized = materialize_fixture(spec, target)
            print(f"FIXTURE_SHA256 {name} {sha256(materialized)} {source_name}")
            completed = subprocess.run(
                ["bash", str(legacy_checker)],
                cwd=temp_root,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            print(f"LEGACY_RESULT {name} exit={completed.returncode}")
            print(result_output(completed))
            if completed.returncode != 0:
                failures += 1
                print(f"UNEXPECTED_REJECT {name}: 旧 checker 未错误接受该 fixture")
            shutil.copy2(REPO_ROOT / source_name, target)
    if failures:
        print(f"RESULT FAIL ({failures} 个 fixture 未被旧 checker 放行)")
        return 1
    print("RESULT PASS (旧 checker 对全部 mutation fixture 均错误返回 0；这正是加固前的 Red 基线)")
    return 0


def run_hardened_rejection(checker: Path, fixtures: list[dict[str, object]]) -> int:
    """验证负向 mutation 一律 fail-close，字符串/普通注释控制样本保持可接受。"""
    failures = 0
    rejected = 0
    accepted_controls = 0
    print("MODE hardened-rejection (负向 mutation 必须拒绝；惰性文本控制样本必须通过)")
    print(f"CHECKER_SHA256 {sha256(checker)} {checker}")
    with tempfile.TemporaryDirectory(prefix="sql-guard-hardened-") as temp:
        temp_dir = Path(temp)
        for spec in fixtures:
            name = spec.get("name")
            outcome = spec.get("expected_outcome", "reject")
            expected = spec.get("expected_failure")
            if not isinstance(name, str) or outcome not in {"reject", "accept"}:
                raise ValueError("fixture name/expected_outcome 格式错误")
            if outcome == "reject" and not isinstance(expected, str):
                raise ValueError(f"拒绝 fixture {name} 缺少 expected_failure")
            fixture_path = materialize_fixture(spec, temp_dir / f"{name}.sql")
            print(f"FIXTURE_SHA256 {name} {sha256(fixture_path)} {fixture_path}")
            completed = subprocess.run(
                ["bash", str(checker), "--fixture", str(fixture_path)],
                cwd=REPO_ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            output = result_output(completed)
            print(f"HARDENED_RESULT {name} exit={completed.returncode}")
            print(output)
            if outcome == "accept":
                if completed.returncode != 0:
                    failures += 1
                    print(f"UNEXPECTED_REJECT {name}: 惰性字符串/普通注释文本被误杀")
                else:
                    accepted_controls += 1
                continue
            if completed.returncode == 0:
                failures += 1
                print(f"UNEXPECTED_ACCEPT {name}: checker 返回 0")
            elif expected not in output:
                failures += 1
                print(f"WRONG_REJECTION {name}: 未找到预期诊断 {expected!r}")
            else:
                rejected += 1
    if failures:
        print(f"RESULT FAIL ({failures} 个 mutation fixture 未得到预期 fail-close 结果)")
        return 1
    print(
        "RESULT PASS ("
        f"{rejected} 个负向 mutation 均被 fail-close 拒绝；"
        f"{accepted_controls} 个字符串/普通注释控制样本未被误杀)"
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("legacy-acceptance", "hardened-rejection"), required=True)
    parser.add_argument("--checker", type=Path, default=DEFAULT_CHECKER)
    args = parser.parse_args()
    checker = args.checker.resolve()
    if not checker.is_file():
        raise SystemExit(f"checker 不存在: {checker}")
    fixtures = load_fixtures()
    if args.mode == "legacy-acceptance":
        return run_legacy_acceptance(checker, fixtures)
    return run_hardened_rejection(checker, fixtures)


if __name__ == "__main__":
    raise SystemExit(main())
