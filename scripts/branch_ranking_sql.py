"""纯函数：生成机构排名派生指标的 SQL 定义文本。

返回值用于指标定义 API 的 ``sqlText``/``refMetricCodes``，不会连接数据库或写 SQL 文件。
"""

from __future__ import annotations

from collections.abc import Mapping


_DERIVED = {
    "TEST_PROV_CORP_DEP_RATE": ("TEST_PROV_CORP_DEP", "TEST_PROV_CORP_DEP_TARGET", False),
    "TEST_PROV_CORP_LOAN_RATE": ("TEST_PROV_CORP_LOAN", "TEST_PROV_CORP_LOAN_TARGET", False),
    "TEST_PROV_RETAIL_NPL_RATE": ("TEST_PROV_RETAIL_NPL_BAL", "TEST_PROV_RETAIL_LOAN", True),
    "TEST_PROV_CORP_NPL_RATE": ("TEST_PROV_CORP_NPL_BAL", "TEST_PROV_CORP_LOAN", True),
}
_LEGACY = {
    "TEST_PROV_DEP_RATE": ("TEST_PROV_RETAIL_DEP", "TEST_PROV_DEP_TARGET", False),
    "TEST_PROV_LOAN_RATE": ("TEST_PROV_RETAIL_LOAN", "TEST_PROV_LOAN_TARGET", False),
}


def _validate_slots(slots: Mapping[str, int]) -> dict[str, int]:
    if not isinstance(slots, Mapping) or not slots:
        raise TypeError("slots must be a non-empty mapping")
    normalized: dict[str, int] = {}
    owners: dict[int, str] = {}
    for code, slot in slots.items():
        if not isinstance(code, str) or not code or not code.replace("_", "").isalnum():
            raise TypeError("metric codes must be safe strings")
        if type(slot) is not int or not 1 <= slot <= 400:
            raise ValueError(f"slot for {code} must be an integer in [1,400]")
        if slot in owners and owners[slot] != code:
            raise ValueError(f"slot {slot} is shared by {owners[slot]} and {code}")
        owners[slot] = code
        normalized[code] = slot
    return normalized


def _sql(numerator_slot: int, denominator_slot: int, npl: bool) -> str:
    numerator = f"val_{numerator_slot}"
    denominator = f"val_{denominator_slot}"
    invalid = f"{numerator} IS NULL OR {denominator} IS NULL OR {denominator} <= 0 OR {numerator} < 0"
    if npl:
        invalid += f" OR {numerator} > {denominator}"
    return (
        "SELECT org_code AS base_key, "
        f"CASE WHEN {invalid} THEN NULL "
        f"ELSE {numerator} * 100 / {denominator} END AS metric_value "
        "FROM ORG_INDEX_RESULT "
        "WHERE data_date = :dataDate AND version = :version"
    )


def generate_ranking_sql(slots: Mapping[str, int], include_legacy: bool = False) -> dict[str, dict[str, object]]:
    """Generate four ranking-rate SQL definitions from a metric-code to slot map."""
    normalized = _validate_slots(slots)
    definitions = dict(_DERIVED)
    if include_legacy:
        definitions.update(_LEGACY)
    result: dict[str, dict[str, object]] = {}
    for output, (numerator, denominator, npl) in definitions.items():
        if numerator not in normalized or denominator not in normalized:
            raise ValueError(f"missing dependency for {output}: {numerator}/{denominator}")
        result[output] = {
            "sql": _sql(normalized[numerator], normalized[denominator], npl),
            "refMetricCodes": [numerator, denominator],
        }
    return result
