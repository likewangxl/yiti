import copy
import unittest
from decimal import Decimal

from scripts.screen_daily_test_data import generate_daily_test_snapshot_rows


def snapshot():
    return {
        "dataClassification": "TEST",
        "rows": [
            {"orgCode": "A", "dataDate": "2026-09-21", "metricValues": {
                "TEST_PROV_DEP": "1000", "TEST_PROV_CORP_DEP": "400", "TEST_PROV_RETAIL_DEP": "600",
                "TEST_PROV_AVG_DEP": "950", "TEST_PROV_DEP_INCREASE": "20", "TEST_PROV_LOAN": "800",
                "TEST_PROV_RETAIL_LOAN": "500", "TEST_PROV_CORP_LOAN": "300", "TEST_PROV_CUSTOMERS": "100",
                "TEST_PROV_REVENUE": "50", "TEST_PROV_DEP_TARGET": "600", "TEST_PROV_DEP_RATE": "100",
                "TEST_PROV_LOAN_TARGET": "900", "TEST_PROV_LOAN_RATE": "55.5556", "TEST_PROV_ATTENTION": "3",
            }},
            {"orgCode": "B", "dataDate": "2026-09-21", "metricValues": {
                "TEST_PROV_DEP": "2000", "TEST_PROV_CORP_DEP": "900", "TEST_PROV_RETAIL_DEP": "1100",
                "TEST_PROV_AVG_DEP": "1900", "TEST_PROV_DEP_INCREASE": "40", "TEST_PROV_LOAN": "1500",
                "TEST_PROV_RETAIL_LOAN": "900", "TEST_PROV_CORP_LOAN": "600", "TEST_PROV_CUSTOMERS": "200",
                "TEST_PROV_REVENUE": "80", "TEST_PROV_DEP_TARGET": "1000", "TEST_PROV_DEP_RATE": "110",
                "TEST_PROV_LOAN_TARGET": "1600", "TEST_PROV_LOAN_RATE": "56.25", "TEST_PROV_ATTENTION": "4",
            }},
        ],
    }


class ScreenDailyTestDataTests(unittest.TestCase):
    def test_generates_daily_rows_and_preserves_source(self):
        source = snapshot()
        before = copy.deepcopy(source)
        rows = generate_daily_test_snapshot_rows(source, "2026-10-31", "2026-11-02", "TEST_DAILY_20261109")
        self.assertEqual(len(rows), 6)
        self.assertEqual(source, before)
        self.assertEqual({row["dataDate"] for row in rows}, {"2026-10-31", "2026-11-01", "2026-11-02"})
        self.assertTrue(all(set(row) == {"orgCode", "dataDate", "metricValues"} for row in rows))
        self.assertEqual(len(rows[0]["metricValues"]), 23)

    def test_formulas_and_bounds_hold(self):
        rows = generate_daily_test_snapshot_rows(snapshot(), "2026-10-30", "2026-11-02", "TEST_DAILY_20261109")
        by_key = {(r["orgCode"], r["dataDate"]): r["metricValues"] for r in rows}
        for row in rows:
            m = row["metricValues"]
            self.assertEqual(Decimal(m["TEST_PROV_DEP"]), Decimal(m["TEST_PROV_CORP_DEP"]) + Decimal(m["TEST_PROV_RETAIL_DEP"]))
            self.assertEqual(Decimal(m["TEST_PROV_LOAN"]), Decimal(m["TEST_PROV_CORP_LOAN"]) + Decimal(m["TEST_PROV_RETAIL_LOAN"]))
            expected_rate = (Decimal(m["TEST_PROV_CORP_DEP"]) / Decimal(m["TEST_PROV_CORP_DEP_TARGET"]) * 100).quantize(Decimal("0.0001"))
            self.assertEqual(Decimal(m["TEST_PROV_CORP_DEP_RATE"]), expected_rate)
            self.assertEqual(Decimal(m["TEST_PROV_DEP_RATE"]), (Decimal(m["TEST_PROV_RETAIL_DEP"]) / Decimal(m["TEST_PROV_DEP_TARGET"]) * 100).quantize(Decimal("0.0001")))
            self.assertEqual(Decimal(m["TEST_PROV_LOAN_RATE"]), (Decimal(m["TEST_PROV_RETAIL_LOAN"]) / Decimal(m["TEST_PROV_LOAN_TARGET"]) * 100).quantize(Decimal("0.0001")))
            self.assertEqual(Decimal(m["TEST_PROV_CORP_LOAN_RATE"]), (Decimal(m["TEST_PROV_CORP_LOAN"]) / Decimal(m["TEST_PROV_CORP_LOAN_TARGET"]) * 100).quantize(Decimal("0.0001")))
            self.assertEqual(Decimal(m["TEST_PROV_RETAIL_NPL_RATE"]), (Decimal(m["TEST_PROV_RETAIL_NPL_BAL"]) / Decimal(m["TEST_PROV_RETAIL_LOAN"]) * 100).quantize(Decimal("0.0001")))
            self.assertEqual(Decimal(m["TEST_PROV_CORP_NPL_RATE"]), (Decimal(m["TEST_PROV_CORP_NPL_BAL"]) / Decimal(m["TEST_PROV_CORP_LOAN"]) * 100).quantize(Decimal("0.0001")))
            self.assertLessEqual(Decimal(m["TEST_PROV_RETAIL_NPL_BAL"]), Decimal(m["TEST_PROV_RETAIL_LOAN"]))
            self.assertLessEqual(int(m["TEST_PROV_ATTENTION"]), int(m["TEST_PROV_CUSTOMERS"]))
        self.assertEqual(by_key[("A", "2026-10-30")]["TEST_PROV_DEP_TARGET"], by_key[("A", "2026-10-31")]["TEST_PROV_DEP_TARGET"])
        self.assertNotEqual(by_key[("A", "2026-10-31")]["TEST_PROV_DEP_TARGET"], by_key[("A", "2026-11-01")]["TEST_PROV_DEP_TARGET"])
        self.assertNotEqual(by_key[("A", "2026-10-30")]["TEST_PROV_CUSTOMERS"], by_key[("A", "2026-11-02")]["TEST_PROV_CUSTOMERS"])

    def test_daily_values_are_absolute_and_month_average_and_increase_are_reconciled(self):
        all_rows = generate_daily_test_snapshot_rows(snapshot(), "2026-10-01", "2026-11-02", "TEST_DAILY_20261109")
        split_rows = generate_daily_test_snapshot_rows(snapshot(), "2026-10-31", "2026-11-01", "TEST_DAILY_20261109")
        all_map = {(r["orgCode"], r["dataDate"]): r["metricValues"] for r in all_rows}
        by_key = all_map
        split_map = {(r["orgCode"], r["dataDate"]): r["metricValues"] for r in split_rows}
        self.assertEqual(all_map[("A", "2026-10-31")], split_map[("A", "2026-10-31")])
        previous = generate_daily_test_snapshot_rows(snapshot(), "2026-09-30", "2026-10-31", "TEST_DAILY_20261109")
        previous_map = {(r["orgCode"], r["dataDate"]): r["metricValues"] for r in previous}
        for row in all_rows:
            day = row["dataDate"]
            m = row["metricValues"]
            previous_day = "2026-09-30" if day.startswith("2026-10") else "2026-10-31"
            previous_metric = previous_map.get((row["orgCode"], previous_day), {"TEST_PROV_DEP": "0"})["TEST_PROV_DEP"]
            self.assertEqual(Decimal(m["TEST_PROV_DEP_INCREASE"]), (Decimal(m["TEST_PROV_DEP"]) - Decimal(previous_metric)).quantize(Decimal("0.0001")))
            month_start = day[:7] + "-01"
            history = [Decimal(metrics["TEST_PROV_DEP"]) for (org, value_day), metrics in all_map.items() if org == "A" and value_day.startswith(day[:7]) and value_day <= day]
            if row["orgCode"] == "A" and history:
                self.assertEqual(Decimal(m["TEST_PROV_AVG_DEP"]), (sum(history) / len(history)).quantize(Decimal("0.0001")))

    def test_rejects_non_test_duplicate_and_invalid_ranges(self):
        with self.assertRaises(ValueError):
            generate_daily_test_snapshot_rows({**snapshot(), "dataClassification": "PROD"}, "2026-10-31", "2026-11-01", "TEST_DAILY")
        duplicate = snapshot()
        duplicate["rows"].append(copy.deepcopy(duplicate["rows"][0]))
        with self.assertRaises(ValueError):
            generate_daily_test_snapshot_rows(duplicate, "2026-10-31", "2026-11-01", "TEST_DAILY")
        with self.assertRaises(ValueError):
            generate_daily_test_snapshot_rows(snapshot(), "2026-10-31", "2027-11-01", "TEST_DAILY")
        with self.assertRaises(ValueError):
            generate_daily_test_snapshot_rows(snapshot(), "2026-10-31", "2026-11-01", "LIVE_DAILY")
        missing = snapshot()
        del missing["rows"][0]["metricValues"]["TEST_PROV_RETAIL_DEP"]
        with self.assertRaises(ValueError):
            generate_daily_test_snapshot_rows(missing, "2026-10-31", "2026-11-01", "TEST_DAILY")
        for bad_org in (None, ""):
            invalid = snapshot()
            invalid["rows"][0]["orgCode"] = bad_org
            with self.assertRaises(ValueError):
                generate_daily_test_snapshot_rows(invalid, "2026-10-31", "2026-11-01", "TEST_DAILY")

    def test_zero_denominators_and_zero_numerators_obey_null_and_zero_rules(self):
        for denominator in ("TEST_PROV_DEP_TARGET", "TEST_PROV_LOAN_TARGET", "TEST_PROV_CORP_DEP_TARGET", "TEST_PROV_CORP_LOAN_TARGET", "TEST_PROV_RETAIL_LOAN", "TEST_PROV_CORP_LOAN"):
            fixture = snapshot()
            fixture["rows"][0]["metricValues"][denominator] = "0"
            rows = generate_daily_test_snapshot_rows(fixture, "2026-10-31", "2026-10-31", "TEST_DAILY")
            metrics = rows[0]["metricValues"]
            related = {
                "TEST_PROV_DEP_TARGET": "TEST_PROV_DEP_RATE",
                "TEST_PROV_LOAN_TARGET": "TEST_PROV_LOAN_RATE",
                "TEST_PROV_CORP_DEP_TARGET": "TEST_PROV_CORP_DEP_RATE",
                "TEST_PROV_CORP_LOAN_TARGET": "TEST_PROV_CORP_LOAN_RATE",
                "TEST_PROV_RETAIL_LOAN": "TEST_PROV_RETAIL_NPL_RATE",
                "TEST_PROV_CORP_LOAN": "TEST_PROV_CORP_NPL_RATE",
            }[denominator]
            self.assertIsNone(metrics[related])
        fixture = snapshot()
        for key in ("TEST_PROV_RETAIL_DEP", "TEST_PROV_RETAIL_LOAN", "TEST_PROV_CORP_DEP", "TEST_PROV_CORP_LOAN", "TEST_PROV_RETAIL_NPL_BAL", "TEST_PROV_CORP_NPL_BAL"):
            fixture["rows"][0]["metricValues"][key] = "0"
        metrics = generate_daily_test_snapshot_rows(fixture, "2026-10-31", "2026-10-31", "TEST_DAILY")[0]["metricValues"]
        for key in ("TEST_PROV_DEP_RATE", "TEST_PROV_LOAN_RATE", "TEST_PROV_CORP_DEP_RATE", "TEST_PROV_CORP_LOAN_RATE"):
            self.assertEqual(Decimal(metrics[key]), Decimal("0.0000"))
        fixture = snapshot()
        fixture["rows"][0]["metricValues"]["TEST_PROV_RETAIL_NPL_BAL"] = "0"
        fixture["rows"][0]["metricValues"]["TEST_PROV_CORP_NPL_BAL"] = "0"
        metrics = generate_daily_test_snapshot_rows(fixture, "2026-10-31", "2026-10-31", "TEST_DAILY")[0]["metricValues"]
        self.assertEqual(Decimal(metrics["TEST_PROV_RETAIL_NPL_RATE"]), Decimal("0.0000"))
        self.assertEqual(Decimal(metrics["TEST_PROV_CORP_NPL_RATE"]), Decimal("0.0000"))

    def test_targets_npl_bounds_and_daily_smoothness(self):
        rows = generate_daily_test_snapshot_rows(snapshot(), "2026-10-30", "2026-11-01", "TEST_DAILY")
        grouped = {}
        for row in rows:
            grouped.setdefault(row["orgCode"], []).append(row)
            m = row["metricValues"]
            self.assertLessEqual(Decimal(m["TEST_PROV_RETAIL_NPL_BAL"]), Decimal(m["TEST_PROV_RETAIL_LOAN"]))
            self.assertLessEqual(Decimal(m["TEST_PROV_CORP_NPL_BAL"]), Decimal(m["TEST_PROV_CORP_LOAN"]))
            self.assertRegex(m["TEST_PROV_CUSTOMERS"], r"^\d+$")
            self.assertRegex(m["TEST_PROV_ATTENTION"], r"^\d+$")
        for items in grouped.values():
            for left, right in zip(items, items[1:]):
                for code in ("TEST_PROV_DEP", "TEST_PROV_LOAN", "TEST_PROV_RETAIL_NPL_RATE", "TEST_PROV_CORP_NPL_RATE"):
                    before, after = Decimal(left["metricValues"][code]), Decimal(right["metricValues"][code])
                    if before:
                        self.assertLess(abs(after - before) / abs(before), Decimal("0.05"))
        for code in ("TEST_PROV_DEP_TARGET", "TEST_PROV_LOAN_TARGET", "TEST_PROV_CORP_DEP_TARGET", "TEST_PROV_CORP_LOAN_TARGET"):
            self.assertEqual(rows[0]["metricValues"][code], rows[2]["metricValues"][code])

    def test_rejects_npl_above_principal(self):
        fixture = snapshot()
        fixture["rows"][0]["metricValues"]["TEST_PROV_RETAIL_NPL_BAL"] = "501"
        with self.assertRaises(ValueError):
            generate_daily_test_snapshot_rows(fixture, "2026-10-31", "2026-10-31", "TEST_DAILY")
        fixture = snapshot()
        fixture["rows"][0]["metricValues"]["TEST_PROV_CORP_NPL_BAL"] = "301"
        with self.assertRaises(ValueError):
            generate_daily_test_snapshot_rows(fixture, "2026-10-31", "2026-10-31", "TEST_DAILY")


if __name__ == "__main__":
    unittest.main()
