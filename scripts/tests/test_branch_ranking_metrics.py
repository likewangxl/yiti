import unittest
from decimal import Decimal

from scripts.branch_ranking_metrics import (
    METRIC_DEFINITIONS,
    aggregate_metrics,
    calculate_metrics,
    seed_test_inputs,
)


class BranchRankingMetricsTests(unittest.TestCase):
    def test_definitions_expose_eight_named_test_metrics(self):
        self.assertEqual(len(METRIC_DEFINITIONS), 8)
        self.assertEqual(METRIC_DEFINITIONS["TEST_PROV_CORP_DEP_RATE"]["unit"], "PERCENT")
        self.assertEqual(METRIC_DEFINITIONS["TEST_PROV_CORP_DEP_TARGET"]["unit"], "YUAN")

    def test_calculate_metrics_uses_decimal_and_allows_zero_npl(self):
        rows = calculate_metrics([{
            "orgCode": "A", "dataDate": "2026-11-09", "version": "TEST-V1",
            "dataClassification": "TEST", "corpDeposit": "300", "corpLoan": "400",
            "retailLoan": "500", "corpDepositTarget": "200", "corpLoanTarget": "800",
            "retailNplBal": "0", "corpNplBal": "40",
        }])
        result = rows[0]
        self.assertEqual(result["TEST_PROV_CORP_DEP_RATE"], Decimal("150"))
        self.assertEqual(result["TEST_PROV_CORP_LOAN_RATE"], Decimal("50"))
        self.assertEqual(result["TEST_PROV_RETAIL_NPL_RATE"], Decimal("0"))
        self.assertEqual(result["TEST_PROV_CORP_NPL_RATE"], Decimal("10"))

    def test_seed_is_deterministic_test_only_and_does_not_overwrite_values(self):
        source = [{"orgCode": "A", "dataDate": "2026-11-09", "version": "V1", "dataClassification": "TEST", "corpDeposit": "100", "corpLoan": "200", "retailLoan": "0", "corpDepositTarget": None, "retailNplBal": "0"}]
        first = seed_test_inputs(source)
        second = seed_test_inputs(source)
        self.assertEqual(first, second)
        self.assertEqual(first[0]["corpDeposit"], "100")
        self.assertEqual(first[0]["dataClassification"], "TEST")
        self.assertTrue(first[0]["batchId"].startswith("TEST-"))
        self.assertGreaterEqual(Decimal(first[0]["corpDepositTarget"]), Decimal("80"))
        self.assertLessEqual(Decimal(first[0]["corpDepositTarget"]), Decimal("140"))
        self.assertEqual(first[0]["retailLoan"], "0")
        self.assertEqual(first[0]["retailNplBal"], "0")
        self.assertLessEqual(Decimal(first[0]["corpNplBal"]), Decimal("10"))

    def test_seed_keeps_all_generated_npl_ratios_at_or_below_five_percent(self):
        rows = seed_test_inputs([{"orgCode": f"ORG-{i}", "dataDate": "2026-11-09", "version": "V1", "dataClassification": "TEST"} for i in range(39)])
        self.assertEqual(len(rows), 39)
        for row in rows:
            for npl, loan in (("retailNplBal", "retailLoan"), ("corpNplBal", "corpLoan")):
                self.assertLessEqual(Decimal(row[npl]), Decimal(row[loan]) * Decimal("0.05"))
        with self.assertRaises(ValueError):
            seed_test_inputs([{ "orgCode": "A", "batchId": "LIVE-1" }])
        with self.assertRaises(ValueError):
            seed_test_inputs([{ "orgCode": "A", "dataDate": "2026-11-09", "version": "V1" }])
        with self.assertRaises(ValueError):
            seed_test_inputs([{ "orgCode": None, "dataClassification": "TEST" }])

    def test_aggregate_recomputes_ratio_from_sums(self):
        rows = calculate_metrics([
            {"orgCode": "A", "dataDate": "2026-11-09", "version": "V1", "dataClassification": "TEST", "corpDeposit": "100", "corpLoan": "100", "retailLoan": "100", "corpDepositTarget": "100", "corpLoanTarget": "100", "retailNplBal": "10", "corpNplBal": "10"},
            {"orgCode": "B", "dataDate": "2026-11-09", "version": "V1", "dataClassification": "TEST", "corpDeposit": "900", "corpLoan": "900", "retailLoan": "900", "corpDepositTarget": "1800", "corpLoanTarget": "900", "retailNplBal": "0", "corpNplBal": "90"},
        ])
        aggregate = aggregate_metrics(rows)
        self.assertEqual(aggregate["TEST_PROV_CORP_DEP_RATE"], Decimal(1000) / Decimal(1900) * 100)
        self.assertEqual(aggregate["TEST_PROV_RETAIL_NPL_RATE"], Decimal("10") / Decimal("1000") * 100)
        self.assertIsNone(aggregate_metrics([{**rows[0], "corpDepositTarget": None}, rows[1]])["TEST_PROV_CORP_DEP_RATE"])

    def test_rejects_negative_values_bad_npl_and_mixed_batch_identity(self):
        base = {"orgCode": "A", "dataDate": "2026-11-09", "version": "V1", "dataClassification": "TEST", "corpDeposit": "100", "corpLoan": "100", "retailLoan": "100", "corpDepositTarget": "100", "corpLoanTarget": "100", "retailNplBal": "1", "corpNplBal": "1"}
        with self.assertRaises(ValueError):
            calculate_metrics([{**base, "corpLoanTarget": "-1"}])
        with self.assertRaises(ValueError):
            calculate_metrics([{**base, "retailNplBal": "101"}])
        with self.assertRaises(ValueError):
            calculate_metrics([base, {**base, "orgCode": "B", "dataDate": "2026-11-10"}])

    def test_accepts_one_classification_per_batch_and_validates_date_and_units(self):
        base = {"orgCode": "A", "dataDate": "2026-11-09", "version": "V1", "dataClassification": "LIVE", "corpDeposit": "100", "corpLoan": "100", "retailLoan": "100", "corpDepositTarget": "100", "corpLoanTarget": "100", "retailNplBal": "1", "corpNplBal": "1", "units": {"corpDeposit": "YUAN", "corpLoan": "YUAN", "retailLoan": "YUAN", "corpDepositTarget": "YUAN", "corpLoanTarget": "YUAN", "retailNplBal": "YUAN", "corpNplBal": "YUAN"}}
        self.assertEqual(calculate_metrics([base])[0]["TEST_PROV_CORP_DEP_RATE"], Decimal("100"))
        with self.assertRaises(ValueError):
            calculate_metrics([base, {**base, "orgCode": "B", "dataClassification": "TEST"}])
        with self.assertRaises(ValueError):
            calculate_metrics([{**base, "dataDate": "2026/11/09"}])
        with self.assertRaises(ValueError):
            calculate_metrics([{**base, "units": {**base["units"], "corpLoan": "HUNDRED_MILLION"}}])
        with self.assertRaises(ValueError):
            calculate_metrics([base, {**base, "orgCode": "B", "units": None}])
        with self.assertRaises(ValueError):
            calculate_metrics([{**base, "corpLoan": "-Infinity"}])
        with self.assertRaises(ValueError):
            calculate_metrics([{**base, "orgCode": None}])
        with self.assertRaises(ValueError):
            calculate_metrics([{**base, "version": None}])
        with self.assertRaises(ValueError):
            calculate_metrics([{**base, "dataDate": "2026-13-99"}])
        with self.assertRaises(ValueError):
            calculate_metrics([{**base, "dataDate": "20260921"}])

    def test_prod_is_calculable_but_seed_rejects_prod_and_live(self):
        prod = {"orgCode": "P", "dataDate": "2026-11-09", "version": "V1", "dataClassification": "PROD", "corpDeposit": "100", "corpLoan": "100", "retailLoan": "100", "corpDepositTarget": "100", "corpLoanTarget": "100", "retailNplBal": "0", "corpNplBal": "0"}
        with self.assertRaises(ValueError):
            seed_test_inputs([prod])
        with self.assertRaises(ValueError):
            seed_test_inputs([{**prod, "dataClassification": "LIVE"}])
        self.assertEqual(calculate_metrics([prod])[0]["TEST_PROV_CORP_DEP_RATE"], Decimal("100"))

    def test_aggregate_validates_each_source_row_before_summing(self):
        base = {"orgCode": "A", "dataDate": "2026-11-09", "version": "V1", "dataClassification": "TEST", "corpDeposit": "100", "corpLoan": "100", "retailLoan": "100", "corpDepositTarget": "100", "corpLoanTarget": "100", "retailNplBal": "1", "corpNplBal": "1"}
        with self.assertRaises(ValueError):
            aggregate_metrics([base, {**base, "orgCode": "B", "corpDeposit": "-90"}])
        with self.assertRaises(ValueError):
            aggregate_metrics([base, {**base, "orgCode": "B", "retailNplBal": "101"}])


if __name__ == "__main__":
    unittest.main()
