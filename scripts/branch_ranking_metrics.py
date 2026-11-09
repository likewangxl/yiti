"""纯函数 TEST_PROV 分行排名指标计划与计算。

本模块只处理内存中的记录，不连接数据库、不生成或执行 SQL。
"""

from __future__ import annotations

import hashlib
import re
from datetime import date
from decimal import Decimal, InvalidOperation
from typing import Iterable, Mapping


INPUT_FIELDS = (
    "TEST_PROV_CORP_DEP_TARGET",
    "TEST_PROV_CORP_LOAN_TARGET",
    "TEST_PROV_RETAIL_NPL_BAL",
    "TEST_PROV_CORP_NPL_BAL",
)

METRIC_DEFINITIONS = {
    "TEST_PROV_CORP_DEP_TARGET": {"unit": "YUAN", "kind": "input", "description": "对公存款目标值"},
    "TEST_PROV_CORP_LOAN_TARGET": {"unit": "YUAN", "kind": "input", "description": "对公贷款目标值"},
    "TEST_PROV_RETAIL_NPL_BAL": {"unit": "YUAN", "kind": "input", "description": "零售贷款不良余额"},
    "TEST_PROV_CORP_NPL_BAL": {"unit": "YUAN", "kind": "input", "description": "对公贷款不良余额"},
    "TEST_PROV_CORP_DEP_RATE": {"unit": "PERCENT", "kind": "output", "expression": "corpDeposit / corpDepositTarget * 100", "description": "对公存款目标完成率"},
    "TEST_PROV_CORP_LOAN_RATE": {"unit": "PERCENT", "kind": "output", "expression": "corpLoan / corpLoanTarget * 100", "description": "对公贷款目标完成率"},
    "TEST_PROV_RETAIL_NPL_RATE": {"unit": "PERCENT", "kind": "output", "expression": "retailNplBal / retailLoan * 100", "description": "零售贷款不良率"},
    "TEST_PROV_CORP_NPL_RATE": {"unit": "PERCENT", "kind": "output", "expression": "corpNplBal / corpLoan * 100", "description": "对公贷款不良率"},
}


def _decimal(value, field: str) -> Decimal | None:
    if value is None or value == "":
        return None
    try:
        result = Decimal(str(value))
    except (InvalidOperation, ValueError) as exc:
        raise ValueError(f"{field} 不是有效 Decimal") from exc
    if not result.is_finite():
        raise ValueError(f"{field} 不是有限数")
    return result


def _put(record: dict, key: str, value: Decimal) -> None:
    record[key] = str(value)


def seed_test_inputs(records: Iterable[Mapping]) -> list[dict]:
    """确定性补齐 TEST 输入；只填缺失值，不覆盖调用方已有值。"""
    output = []
    for source in records:
        record = dict(source)
        raw_org_code = record.get("orgCode")
        org_code = "" if raw_org_code is None else str(raw_org_code).strip()
        if not org_code:
            raise ValueError("orgCode 不能为空")
        if record.get("dataClassification") != "TEST":
            raise ValueError("只能为明确标记 TEST 的数据补充测试指标")
        digest = hashlib.sha256(org_code.encode("utf-8")).digest()
        values = [Decimal(100 + int.from_bytes(digest[i:i + 2], "big") % 900) for i in range(0, 16, 2)]
        for key, value in (("corpDeposit", values[0]), ("corpLoan", values[1]), ("retailLoan", values[2])):
            if record.get(key) in (None, ""):
                _put(record, key, value)
        for key, amount, selector in (("corpDepositTarget", "corpDeposit", values[3]), ("corpLoanTarget", "corpLoan", values[4])):
            if record.get(key) in (None, ""):
                actual = _decimal(record[amount], amount) or Decimal(0)
                rate_percent = Decimal(75 + int(selector) % 51)
                _put(record, key, Decimal(1) if actual == 0 else actual * 100 / rate_percent)
        for key, loan, percent in (("retailNplBal", "retailLoan", values[5] % 6), ("corpNplBal", "corpLoan", values[6] % 6)):
            if record.get(key) in (None, ""):
                loan_value = _decimal(record[loan], loan) or Decimal(0)
                _put(record, key, loan_value * percent / 100)
        record["dataClassification"] = "TEST"
        if record.get("batchId") not in (None, "") and not str(record["batchId"]).startswith("TEST-"):
            raise ValueError("TEST 补充数据的 batchId 必须以 TEST- 开头")
        record.setdefault("batchId", f"TEST-RANKING-{digest.hex()[:12]}")
        output.append(record)
    return output


def _validate_identity(records: list[Mapping]) -> None:
    seen = set()
    identities = set()
    unit_signatures = set()
    unit_fields = ("corpDeposit", "corpLoan", "retailLoan", "corpDepositTarget", "corpLoanTarget", "retailNplBal", "corpNplBal")
    for record in records:
        raw_org = record.get("orgCode")
        org = "" if raw_org is None else str(raw_org).strip()
        if not org or org in seen:
            raise ValueError("机构号缺失或重复")
        seen.add(org)
        classification = record.get("dataClassification")
        if classification not in ("TEST", "LIVE", "PROD"):
            raise ValueError("dataClassification 只能是 TEST、LIVE 或 PROD")
        date_value = record.get("dataDate")
        date_text = "" if date_value is None else str(date_value)
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date_text):
            raise ValueError("dataDate 必须是 YYYY-MM-DD")
        try:
            date.fromisoformat(date_text)
        except (TypeError, ValueError):
            raise ValueError("dataDate 必须是 YYYY-MM-DD")
        raw_version = record.get("version")
        version = "" if raw_version is None else str(raw_version).strip()
        if not version:
            raise ValueError("version 不能为空")
        units = record.get("units")
        if units is not None:
            if not isinstance(units, Mapping) or any(units.get(field) != "YUAN" for field in ("corpDeposit", "corpLoan", "retailLoan", "corpDepositTarget", "corpLoanTarget", "retailNplBal", "corpNplBal")):
                raise ValueError("排名金额输入必须统一使用 YUAN")
            unit_signatures.add(tuple(units.get(field) for field in unit_fields))
        else:
            unit_signatures.add(None)
        identities.add((classification, date_text, version))
    if len(identities) > 1:
        raise ValueError("记录日期或版本不一致")
    if len(unit_signatures) > 1:
        raise ValueError("记录单位契约不一致")


def _measure_values(record: Mapping) -> dict[str, Decimal | None]:
    values = {key: _decimal(record.get(key), key) for key in (
        "corpDeposit", "corpLoan", "retailLoan", "corpDepositTarget", "corpLoanTarget", "retailNplBal", "corpNplBal")}
    for key, value in values.items():
        if value is not None and value < 0:
            raise ValueError(f"{key} 不能为负数")
    if values["retailNplBal"] is not None and values["retailLoan"] is not None and values["retailNplBal"] > values["retailLoan"]:
        raise ValueError("零售不良余额不能大于零售贷款")
    if values["corpNplBal"] is not None and values["corpLoan"] is not None and values["corpNplBal"] > values["corpLoan"]:
        raise ValueError("对公不良余额不能大于对公贷款")
    return values


def calculate_metrics(records: Iterable[Mapping]) -> list[dict]:
    """按机构计算四项输出；分母缺失/为零返回 None，不把它伪造成 0%。"""
    source = [dict(record) for record in records]
    _validate_identity(source)
    result = []
    for record in source:
        values = _measure_values(record)

        def ratio(numerator: str, denominator: str) -> Decimal | None:
            top, bottom = values[numerator], values[denominator]
            return None if top is None or bottom in (None, Decimal(0)) else top / bottom * 100

        item = dict(record)
        item.update({
            "TEST_PROV_CORP_DEP_RATE": ratio("corpDeposit", "corpDepositTarget"),
            "TEST_PROV_CORP_LOAN_RATE": ratio("corpLoan", "corpLoanTarget"),
            "TEST_PROV_RETAIL_NPL_RATE": ratio("retailNplBal", "retailLoan"),
            "TEST_PROV_CORP_NPL_RATE": ratio("corpNplBal", "corpLoan"),
        })
        result.append(item)
    return result


def aggregate_metrics(rows: Iterable[Mapping]) -> dict[str, Decimal | None]:
    """按分子/分母汇总后计算比例，不平均机构比例。"""
    source = [dict(row) for row in rows]
    _validate_identity(source)
    validated = [_measure_values(row) for row in source]

    def total(field: str) -> Decimal | None:
        values = [values[field] for values in validated]
        return None if any(value is None for value in values) else sum(values, Decimal(0))

    totals = {field: total(field) for field in ("corpDeposit", "corpLoan", "retailLoan", "corpDepositTarget", "corpLoanTarget", "retailNplBal", "corpNplBal")}
    for field, value in totals.items():
        if value is not None and value < 0:
            raise ValueError(f"{field} 不能为负数")
    if totals["retailNplBal"] is not None and totals["retailLoan"] is not None and totals["retailNplBal"] > totals["retailLoan"]:
        raise ValueError("汇总零售不良余额不能大于零售贷款")
    if totals["corpNplBal"] is not None and totals["corpLoan"] is not None and totals["corpNplBal"] > totals["corpLoan"]:
        raise ValueError("汇总对公不良余额不能大于对公贷款")

    def ratio(top: str, bottom: str) -> Decimal | None:
        return None if totals[top] is None or totals[bottom] in (None, Decimal(0)) else totals[top] / totals[bottom] * 100

    return {
        "TEST_PROV_CORP_DEP_RATE": ratio("corpDeposit", "corpDepositTarget"),
        "TEST_PROV_CORP_LOAN_RATE": ratio("corpLoan", "corpLoanTarget"),
        "TEST_PROV_RETAIL_NPL_RATE": ratio("retailNplBal", "retailLoan"),
        "TEST_PROV_CORP_NPL_RATE": ratio("corpNplBal", "corpLoan"),
    }
