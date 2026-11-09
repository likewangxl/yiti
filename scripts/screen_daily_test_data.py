"""Deterministic daily TEST snapshots for screen-only validation.

This module is pure: it never connects to a database and never mutates its input.
"""

from __future__ import annotations

import hashlib
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP
from typing import Mapping

from scripts.branch_ranking_metrics import calculate_metrics, seed_test_inputs

DAILY_TEST_PROVENANCE = (
    "TEST synthetic daily snapshot; deterministic from org/date; same-org formulas; "
    "not real operating data"
)

_Q = Decimal("0.0001")
_ANCHOR = date(2026, 9, 21)
def _dec(value: object, name: str) -> Decimal:
    try:
        result = Decimal(str(value))
    except Exception as exc:  # pragma: no cover - Decimal supplies the detail
        raise ValueError(f"{name} must be Decimal-compatible") from exc
    if not result.is_finite() or result < 0:
        raise ValueError(f"{name} must be a non-negative finite number")
    return result


def _q(value: Decimal) -> Decimal:
    return value.quantize(_Q, rounding=ROUND_HALF_UP)


def _unit(org: str, day: date, label: str) -> Decimal:
    raw = hashlib.sha256(f"{org}|{day.isoformat()}|{label}".encode()).digest()
    return Decimal(int.from_bytes(raw[:2], "big")) / Decimal(65535)


def _factor(org: str, day: date, label: str, index: int) -> Decimal:
    # A bounded smooth drift plus a deterministic small daily component.
    drift = Decimal(index) / Decimal(1000)
    wave = (_unit(org, day, label) - Decimal("0.5")) / Decimal(100)
    return Decimal("1") + drift + wave


def _absolute_factor(org: str, day: date, label: str) -> Decimal:
    return _factor(org, day, label, (day - _ANCHOR).days)


def _parse_date(value: object, name: str) -> date:
    if not isinstance(value, str):
        raise ValueError(f"{name} must be YYYY-MM-DD")
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise ValueError(f"{name} must be YYYY-MM-DD") from exc


def _validate_snapshot(snapshot: Mapping) -> list[Mapping]:
    if snapshot.get("dataClassification") != "TEST":
        raise ValueError("snapshot dataClassification must be TEST")
    source = snapshot.get("rows")
    if not isinstance(source, list) or not source:
        raise ValueError("snapshot rows must be a non-empty list")
    seen: set[str] = set()
    for row in source:
        raw_org = row.get("orgCode")
        if not isinstance(raw_org, str):
            raise ValueError("snapshot orgCode must be a non-empty string")
        org = raw_org.strip()
        if not org or org in seen:
            raise ValueError("snapshot contains missing or duplicate orgCode")
        if not isinstance(row.get("metricValues"), Mapping):
            raise ValueError("snapshot row metricValues must be a mapping")
        seen.add(org)
    return source


def _base_records(source: list[Mapping], version: str) -> list[dict]:
    records = []
    for row in source:
        m = row["metricValues"]
        records.append({
            "orgCode": str(row["orgCode"]).strip(),
            "dataDate": str(row.get("dataDate", "2026-09-21")),
            "version": version,
            "dataClassification": "TEST",
            "corpDeposit": m.get("TEST_PROV_CORP_DEP"),
            "corpLoan": m.get("TEST_PROV_CORP_LOAN"),
            "retailLoan": m.get("TEST_PROV_RETAIL_LOAN"),
            "corpDepositTarget": m.get("TEST_PROV_CORP_DEP_TARGET"),
            "corpLoanTarget": m.get("TEST_PROV_CORP_LOAN_TARGET"),
            "retailNplBal": m.get("TEST_PROV_RETAIL_NPL_BAL"),
            "corpNplBal": m.get("TEST_PROV_CORP_NPL_BAL"),
            "retailNplExplicitZero": m.get("TEST_PROV_RETAIL_NPL_BAL") in (0, "0", "0.0", "0.0000"),
            "corpNplExplicitZero": m.get("TEST_PROV_CORP_NPL_BAL") in (0, "0", "0.0", "0.0000"),
            "retailDeposit": m.get("TEST_PROV_RETAIL_DEP"),
            "retailDepositTarget": m.get("TEST_PROV_DEP_TARGET"),
            "retailLoanTarget": m.get("TEST_PROV_LOAN_TARGET"),
        })
        if records[-1]["retailDeposit"] in (None, ""):
            raise ValueError("snapshot must contain TEST_PROV_RETAIL_DEP for daily simulation")
    seeded = seed_test_inputs(records)
    # Reuse the canonical invariant checks so an invalid supplied NPL cannot be clipped.
    calculate_metrics(seeded)
    return seeded


def generate_daily_test_snapshot_rows(snapshot: Mapping, start_date: str, end_date: str, version: str) -> list[dict]:
    """Generate one immutable-shaped row per organisation and day.

    The output intentionally contains only ``orgCode``, ``dataDate`` and
    ``metricValues`` so it can be inserted into an existing snapshot adapter.
    """
    source = _validate_snapshot(snapshot)
    if not isinstance(version, str) or not version.startswith("TEST_"):
        raise ValueError("version must start with TEST_")
    start, end = _parse_date(start_date, "start_date"), _parse_date(end_date, "end_date")
    if end < start:
        raise ValueError("end_date must not precede start_date")
    if (end - start).days + 1 > 366:
        raise ValueError("date range cannot exceed 366 days")

    bases = _base_records(source, version)
    output: list[dict] = []
    cursor = start
    while cursor <= end:
        for base in bases:
            org = base["orgCode"]
            day_index = (cursor - _ANCHOR).days
            corp_dep = _q(_dec(base["corpDeposit"], "corpDeposit") * _absolute_factor(org, cursor, "corp_dep"))
            retail_dep = _q(_dec(base["retailDeposit"], "retailDeposit") * _absolute_factor(org, cursor, "retail_dep"))
            corp_loan = _q(_dec(base["corpLoan"], "corpLoan") * _absolute_factor(org, cursor, "corp_loan"))
            retail_loan = _q(_dec(base["retailLoan"], "retailLoan") * _absolute_factor(org, cursor, "retail_loan"))
            month_index = (cursor.year - _ANCHOR.year) * 12 + cursor.month - _ANCHOR.month
            month_factor = Decimal("1") + Decimal(month_index) / Decimal(120)
            corp_target = _q(_dec(base["corpDepositTarget"], "corpDepositTarget") * month_factor)
            corp_loan_target = _q(_dec(base["corpLoanTarget"], "corpLoanTarget") * (Decimal("1") + Decimal(month_index) / Decimal(150)))
            retail_target = _q(_dec(base["retailDepositTarget"], "retailDepositTarget") * month_factor)
            retail_loan_target = _q(_dec(base["retailLoanTarget"], "retailLoanTarget") * (Decimal("1") + Decimal(month_index) / Decimal(150)))
            base_retail_ratio = _dec(base["retailNplBal"], "retailNplBal") / _dec(base["retailLoan"], "retailLoan") if _dec(base["retailLoan"], "retailLoan") else Decimal("0")
            base_corp_ratio = _dec(base["corpNplBal"], "corpNplBal") / _dec(base["corpLoan"], "corpLoan") if _dec(base["corpLoan"], "corpLoan") else Decimal("0")
            retail_ratio = Decimal("0") if base.get("retailNplExplicitZero") else max(Decimal("0"), min(Decimal("0.05"), base_retail_ratio + Decimal(day_index) / Decimal(100000) + (_unit(org, _ANCHOR, "retail_npl") - Decimal("0.5")) / Decimal(10000)))
            corp_ratio = Decimal("0") if base.get("corpNplExplicitZero") else max(Decimal("0"), min(Decimal("0.05"), base_corp_ratio + Decimal(day_index) / Decimal(120000) + (_unit(org, _ANCHOR, "corp_npl") - Decimal("0.5")) / Decimal(12000)))
            retail_npl = _q(retail_loan * retail_ratio)
            corp_npl = _q(corp_loan * corp_ratio)
            total_dep = _q(corp_dep + retail_dep)
            total_loan = _q(corp_loan + retail_loan)
            src = next(r for r in source if str(r["orgCode"]).strip() == org)["metricValues"]
            count_factor = _absolute_factor(org, cursor, "customers") + Decimal(day_index) / Decimal(300)
            attention_factor = _absolute_factor(org, cursor, "attention") + Decimal(day_index) / Decimal(300)
            customers = max(0, int((_dec(src.get("TEST_PROV_CUSTOMERS", "0"), "customers") * count_factor).to_integral_value(rounding=ROUND_HALF_UP)))
            attention = min(customers, max(0, int((_dec(src.get("TEST_PROV_ATTENTION", "0"), "attention") * attention_factor).to_integral_value(rounding=ROUND_HALF_UP))))
            month_start = date(cursor.year, cursor.month, 1)
            month_values = []
            probe = month_start
            while probe <= cursor:
                month_values.append(_q(_dec(base["corpDeposit"], "corpDeposit") * _absolute_factor(org, probe, "corp_dep")) + _q(_dec(base["retailDeposit"], "retailDeposit") * _absolute_factor(org, probe, "retail_dep")))
                probe += timedelta(days=1)
            previous_month_end = month_start - timedelta(days=1)
            previous_total = _q(_dec(base["corpDeposit"], "corpDeposit") * _absolute_factor(org, previous_month_end, "corp_dep")) + _q(_dec(base["retailDeposit"], "retailDeposit") * _absolute_factor(org, previous_month_end, "retail_dep"))
            metric = {
                "TEST_PROV_DEP": total_dep,
                "TEST_PROV_CORP_DEP": corp_dep,
                "TEST_PROV_RETAIL_DEP": retail_dep,
                "TEST_PROV_AVG_DEP": _q(sum(month_values, Decimal("0")) / Decimal(len(month_values))),
                "TEST_PROV_DEP_INCREASE": _q(total_dep - previous_total),
                "TEST_PROV_LOAN": total_loan,
                "TEST_PROV_RETAIL_LOAN": retail_loan,
                "TEST_PROV_CORP_LOAN": corp_loan,
                "TEST_PROV_CUSTOMERS": Decimal(customers),
                "TEST_PROV_REVENUE": _q(total_loan * Decimal("0.012")),
                "TEST_PROV_CORP_DEP_TARGET": corp_target,
                "TEST_PROV_CORP_LOAN_TARGET": corp_loan_target,
                "TEST_PROV_RETAIL_NPL_BAL": retail_npl,
                "TEST_PROV_CORP_NPL_BAL": corp_npl,
                "TEST_PROV_DEP_TARGET": retail_target,
                "TEST_PROV_DEP_RATE": _q(retail_dep / retail_target * 100) if retail_target else None,
                "TEST_PROV_LOAN_TARGET": retail_loan_target,
                "TEST_PROV_LOAN_RATE": _q(retail_loan / retail_loan_target * 100) if retail_loan_target else None,
                "TEST_PROV_ATTENTION": Decimal(attention),
                "TEST_PROV_CORP_DEP_RATE": _q(corp_dep / corp_target * 100) if corp_target else None,
                "TEST_PROV_CORP_LOAN_RATE": _q(corp_loan / corp_loan_target * 100) if corp_loan_target else None,
                "TEST_PROV_RETAIL_NPL_RATE": _q(retail_npl / retail_loan * 100) if retail_loan else None,
                "TEST_PROV_CORP_NPL_RATE": _q(corp_npl / corp_loan * 100) if corp_loan else None,
            }
            output.append({"orgCode": org, "dataDate": cursor.isoformat(), "metricValues": {k: (None if v is None else str(v)) for k, v in metric.items()}})
        cursor += timedelta(days=1)
    return output
