import unittest

from scripts.branch_ranking_sql import generate_ranking_sql


VALID = {
    "TEST_PROV_CORP_DEP": 321,
    "TEST_PROV_CORP_DEP_TARGET": 1,
    "TEST_PROV_CORP_LOAN": 327,
    "TEST_PROV_CORP_LOAN_TARGET": 2,
    "TEST_PROV_RETAIL_NPL_BAL": 3,
    "TEST_PROV_RETAIL_LOAN": 326,
    "TEST_PROV_CORP_NPL_BAL": 4,
}


class BranchRankingSqlTest(unittest.TestCase):
  def test_generates_four_derived_metrics_with_same_safe_sql_contract(self):
    result = generate_ranking_sql(VALID)
    self.assertEqual(set(result), {"TEST_PROV_CORP_DEP_RATE", "TEST_PROV_CORP_LOAN_RATE", "TEST_PROV_RETAIL_NPL_RATE", "TEST_PROV_CORP_NPL_RATE"})
    self.assertEqual(result["TEST_PROV_CORP_DEP_RATE"]["refMetricCodes"], ["TEST_PROV_CORP_DEP", "TEST_PROV_CORP_DEP_TARGET"])
    sql = result["TEST_PROV_RETAIL_NPL_RATE"]["sql"]
    self.assertIn("base_key", sql); self.assertIn("metric_value", sql)
    self.assertIn("FROM ORG_INDEX_RESULT", sql)
    self.assertIn("data_date = :dataDate", sql); self.assertIn("version = :version", sql)
    self.assertNotIn("COALESCE", sql.upper()); self.assertNotIn("IFNULL", sql.upper())
    self.assertIn("CASE", sql); self.assertIn("NULL", sql)


  def test_optional_legacy_rates_are_supported(self):
    mapping = {**VALID, "TEST_PROV_RETAIL_DEP": 322, "TEST_PROV_DEP_TARGET": 330, "TEST_PROV_LOAN_TARGET": 332}
    result = generate_ranking_sql(mapping, include_legacy=True)
    self.assertTrue({"TEST_PROV_DEP_RATE", "TEST_PROV_LOAN_RATE"}.issubset(result))


  def test_rejects_invalid_slot_mapping(self):
    for bad in [None, {}, {"A": True}, {"A": 1.0}, {"A": "1"}, {"A": 0}, {"A": 401}]:
      with self.subTest(bad=bad), self.assertRaises((TypeError, ValueError)):
        generate_ranking_sql(bad)


  def test_rejects_duplicate_slot_ownership_and_missing_dependency(self):
    with self.assertRaises(ValueError):
        generate_ranking_sql({**VALID, "TEST_PROV_CORP_NPL_DUP": 3})
    missing = dict(VALID)
    del missing["TEST_PROV_CORP_LOAN_TARGET"]
    with self.assertRaises(ValueError):
        generate_ranking_sql(missing)


  def test_npl_rejects_amount_above_loan_and_negative_values_in_sql(self):
    sql = generate_ranking_sql(VALID)["TEST_PROV_CORP_NPL_RATE"]["sql"]
    self.assertIn("val_4 < 0", sql)
    self.assertIn("val_4 > val_327", sql)
    self.assertIn("val_4 * 100", sql)


if __name__ == "__main__":
    unittest.main()
