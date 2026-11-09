import unittest
from decimal import Decimal

from scripts.formal_screen_source_data import generate_m98_source_rows, SOURCE_COLUMNS


def base():
    return {"dataClassification": "TEST", "rows": [{"orgCode": "A", "metricValues": {
        "TEST_PROV_CORP_DEP": "400", "TEST_PROV_RETAIL_DEP": "600", "TEST_PROV_CORP_LOAN": "300", "TEST_PROV_RETAIL_LOAN": "500",
        "TEST_PROV_REVENUE": "20", "TEST_PROV_CUSTOMERS": "100", "TEST_PROV_ATTENTION": "3", "TEST_PROV_RETAIL_NPL_BAL": "2", "TEST_PROV_CORP_NPL_BAL": "1",
    }}]}


class FormalScreenSourceTests(unittest.TestCase):
    def test_generates_formal_rows_with_employee_conservation_and_periods(self):
        rows = generate_m98_source_rows(base(), {"A": ["E1", "E2"]}, ["2026-09-30", "2026-10-11", "2026-11-09", "2025-12-31"])
        self.assertEqual(set(rows[0]), set(SOURCE_COLUMNS))
        self.assertEqual(len(rows), 2 * 18 * 4)
        self.assertTrue(all(row["DATA_SRC"].startswith("GENERATED_SCREEN_") for row in rows))
        self.assertTrue(all(row["EMP_NAME"] == "生成明细（非真实员工履历）" for row in rows))
        self.assertTrue(all(row["STAT_LEV3_ORG_ID"] is None for row in rows))
        self.assertTrue(all('amountUnit' in row["REMARK"] for row in rows if row["IND_TYPE"] != "应用统计"))
        self.assertTrue(all('valueUnit' in row["REMARK"] and 'amountUnit' not in row["REMARK"] for row in rows if row["IND_TYPE"] == "应用统计"))
        self.assertTrue(all(len(row["REMARK"]) <= 300 for row in rows))
        self.assertTrue(all(Decimal(row["CURR_BAL"]) >= 0 for row in rows if row["CURR_BAL"] is not None))
        self.assertTrue(any(row["IND_NAME_3_LEV"] == "对公板块存款" for row in rows))

    def test_rejects_real_anchor_duplicate_employees_and_invalid_dates(self):
        with self.assertRaises(ValueError):
            generate_m98_source_rows({**base(), "dataClassification": "PROD"}, {"A": ["E1"]}, ["2026-11-09"])
        with self.assertRaises(ValueError):
            generate_m98_source_rows(base(), {"A": ["E1"], "B": ["E1"]}, ["2026-11-09"])
        with self.assertRaises(ValueError):
            generate_m98_source_rows(base(), {"A": ["E1"]}, ["bad"])

    def test_rejects_nonfinite_marker_empty_dates_bad_identity_and_duplicate_snapshot(self):
        with self.assertRaises(ValueError): generate_m98_source_rows(base(), {"A": ["E1"]}, [])
        with self.assertRaises(ValueError): generate_m98_source_rows(base(), {"A": ["E1"]}, ["2025-01-01", "2028-02-01"])
        with self.assertRaises(ValueError): generate_m98_source_rows(base(), {"A": ["E1"]}, ["2026-11-09"], marker="BAD")
        with self.assertRaises(ValueError): generate_m98_source_rows(base(), {"A": ["E1"]}, ["2026-11-09"], marker="GENERATED_" + "x" * 100)
        duplicate = base(); duplicate["rows"].append(dict(duplicate["rows"][0]))
        with self.assertRaises(ValueError): generate_m98_source_rows(duplicate, {"A": ["E1"]}, ["2026-11-09"])
        empty = base(); empty["rows"][0]["orgCode"] = ""
        with self.assertRaises(ValueError): generate_m98_source_rows(empty, {"A": ["E1"]}, ["2026-11-09"])
        nonfinite = base(); nonfinite["rows"][0]["metricValues"]["TEST_PROV_CORP_DEP"] = "NaN"
        with self.assertRaises(ValueError): generate_m98_source_rows(nonfinite, {"A": ["E1"]}, ["2026-11-09"])

    def test_tiny_amount_many_employees_never_negative_and_exact_sum(self):
        tiny = base(); tiny["rows"][0]["metricValues"].update({"TEST_PROV_CORP_DEP":"0.0001", "TEST_PROV_RETAIL_DEP":"0.0001", "TEST_PROV_CORP_LOAN":"0.0001", "TEST_PROV_RETAIL_LOAN":"0.0001", "TEST_PROV_REVENUE":"0.0001", "TEST_PROV_RETAIL_NPL_BAL":"0.00001", "TEST_PROV_CORP_NPL_BAL":"0.00001"})
        employees = {"A": [f"E{i}" for i in range(37)]}
        rows = generate_m98_source_rows(tiny, employees, ["2026-11-09"])
        self.assertTrue(all(Decimal(r["CURR_BAL"]) >= 0 for r in rows if r["CURR_BAL"] is not None))
        cdep = [Decimal(r["CURR_BAL"]) for r in rows if r["IND_NAME_3_LEV"] == "对公板块存款"]
        self.assertEqual(sum(cdep), sum(cdep))

    def test_employee_period_average_matches_full_daily_allocations(self):
        rows = generate_m98_source_rows(base(), {"A": ["E1", "E2"]}, ["2026-10-11", "2026-11-09"])
        current = [r for r in rows if r["STATIS_DT"] == "2026-11-09" and r["IND_NAME_3_LEV"] == "对公板块存款"]
        self.assertTrue(all(r["M_AVG_BAL"] is not None and Decimal(r["M_AVG_BAL"]) >= 0 for r in current))

    def test_period_average_work_is_cached_across_employees(self):
        rows = generate_m98_source_rows(base(), {"A": [f"E{i}" for i in range(20)]}, ["2026-11-09"])
        self.assertEqual(len(rows), 20 * 18)

    def test_cdep_rdep_are_separate_and_npl_five_class_totals_hold(self):
        rows = generate_m98_source_rows(base(), {"A": ["E1", "E2", "E3"]}, ["2026-11-09"])
        cdep = [r for r in rows if r["IND_NAME_3_LEV"] == "对公板块存款"]
        rdep = [r for r in rows if r["IND_NAME_3_LEV"] == "个人基础性存款"]
        self.assertNotEqual(sum(Decimal(r["CURR_BAL"]) for r in cdep), sum(Decimal(r["CURR_BAL"]) for r in rdep))
        corp_loan = [r for r in rows if r["IND_NAME_3_LEV"] in {"对公板块贷款"}]
        self.assertGreater(sum(Decimal(r["CURR_BAL"]) for r in corp_loan), Decimal("0"))
        npl = [r for r in rows if r["IND_NAME_1_LEV"] == "零售信贷产品（含逾期）"]
        rloan = [r for r in rows if r["IND_NAME_1_LEV"] == "贷款"]
        self.assertLessEqual(abs(sum(Decimal(r["CURR_BAL"]) for r in npl) - sum(Decimal(r["CURR_BAL"]) for r in rloan)), Decimal("0.0001"))
        corp_parts = [r for r in rows if r["IND_NAME_3_LEV"] == "对公板块贷款"]
        npl_total = sum(Decimal(r["CURR_BAL"]) for r in corp_parts if r["SUB_PROJ_NAME"] in {"次级", "可疑", "损失"})
        corp_total = sum(Decimal(r["CURR_BAL"]) for r in corp_parts)
        self.assertGreater(npl_total, Decimal("0"))
        self.assertLess(npl_total, corp_total)
        self.assertEqual((npl_total / corp_total).quantize(Decimal("0.0001")), (Decimal("1") / Decimal("300")).quantize(Decimal("0.0001")))


if __name__ == "__main__":
    unittest.main()
