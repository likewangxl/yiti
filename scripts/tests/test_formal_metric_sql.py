import unittest

from scripts.formal_metric_sql import employee_metric_sql, ratio_metric_sql


class FormalMetricSqlTests(unittest.TestCase):
    def test_fixed_selectors_and_numeric_guard(self):
        for selector in ("CDEP", "RDEP", "DEP", "CLOAN", "RLOAN", "CORP_NPL", "RETAIL_LOAN_CLASS", "RETAIL_NPL", "CORP_FTP", "RETAIL_FTP", "CUSTOMERS", "ATTENTION"):
            sql = employee_metric_sql(selector)
            self.assertIn("XAN_M98_EMP_STAT_SHOW3", sql)
            self.assertIn("EMP_ID AS base_key", sql)
            self.assertIn(":dataDate", sql)
            self.assertRegex(sql, r"REGEXP")
            self.assertNotIn("COALESCE", sql.upper())
            self.assertNotIn("RAND", sql.upper())
        self.assertIn("LEFT JOIN", employee_metric_sql("CDEP", comparison="datePrevMonthEnd"))

    def test_rejects_unknown_field_selector_macro_and_injection(self):
        with self.assertRaises(ValueError): employee_metric_sql("CDEP", field="BAD")
        with self.assertRaises(ValueError): employee_metric_sql("BAD")
        with self.assertRaises(ValueError): employee_metric_sql("CDEP", comparison="dateBad")
        with self.assertRaises(ValueError): employee_metric_sql("CDEP;DROP TABLE XAN_M98_EMP_STAT_SHOW3")

    def test_ratio_sql_validates_slots_and_nulls_bad_npl(self):
        sql = ratio_metric_sql(203, 204, npl=True)
        self.assertIn("ORG_INDEX_RESULT", sql)
        self.assertIn(":dataDate", sql)
        self.assertIn(":version", sql)
        self.assertIn("CASE", sql)
        self.assertIn("IS NULL", sql)
        self.assertIn("> denominator", sql)
        self.assertIn("val_203", sql)
        self.assertIn("val_204", sql)
        self.assertIn("numerator IS NULL", sql)
        self.assertNotIn("\\.", sql)
        for bad in (0, 401, True, 1.2, "1"):
            with self.assertRaises(ValueError): ratio_metric_sql(bad, 2)
        with self.assertRaises(ValueError): ratio_metric_sql(1, 2, npl="yes")

    def test_category_completeness_uses_business_names_and_object_id_parameter(self):
        cloan = employee_metric_sql("CLOAN", comparison="datePrevYearEnd", object_id="ORG-1")
        self.assertIn("SUB_PROJ_NAME", cloan)
        self.assertIn("正常", cloan)
        self.assertIn("损失", cloan)
        self.assertIn(":objectId", cloan)
        npl = employee_metric_sql("CORP_NPL")
        self.assertIn("次级", npl)
        self.assertIn("可疑", npl)
        self.assertIn("损失", npl)

    def test_preferred_source_fallback_is_explicit_and_default_is_unchanged(self):
        default_sql = employee_metric_sql("CDEP")
        self.assertNotIn("preferred_emp_id", default_sql)
        preferred = employee_metric_sql("CDEP", preferred_data_source="GENERATED_SCREEN_20261109")
        self.assertIn("preferred_emp_id", preferred)
        self.assertIn("SELECT DISTINCT EMP_ID AS preferred_emp_id", preferred)
        self.assertIn("LEFT JOIN", preferred)
        self.assertIn("preferred.preferred_emp_id IS NULL", preferred)
        compared = employee_metric_sql("CDEP", comparison="datePrevYearEnd", preferred_data_source="GENERATED_SCREEN_20261109")
        self.assertGreaterEqual(compared.count("preferred_emp_id"), 2)
        with self.assertRaises(ValueError): employee_metric_sql("CDEP", preferred_data_source="bad-marker")
        with self.assertRaises(ValueError): employee_metric_sql("CDEP", preferred_data_source="GENERATED_SCREEN_" + "x" * 100)


if __name__ == "__main__": unittest.main()
