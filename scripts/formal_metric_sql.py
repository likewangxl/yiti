"""Fixed, parameterised SQL definitions for formal screen metrics."""
from __future__ import annotations
import re

TABLE = "XAN_M98_EMP_STAT_SHOW3"
FIELDS = frozenset({"CURR_BAL", "M_AVG_BAL", "Q_AVG_BAL", "Y_AVG_BAL", "FTP_REVENUE"})
COMPARISONS = {None: None, "datePrevMonthEnd": "datePrevMonthEnd", "datePrevQuarterEnd": "datePrevQuarterEnd", "datePrevYearEnd": "datePrevYearEnd"}

SELECTORS = {
    "CDEP": "IND_TYPE='报表指标' AND IND_NAME_1_LEV='对公业务（不含汇兑）' AND IND_NAME_2_LEV='对公负债业务' AND IND_NAME_3_LEV='对公板块存款' AND CURRENCY_TYPE='199'",
    "RDEP": "IND_TYPE='规模指标' AND IND_NAME_1_LEV='存款' AND IND_NAME_2_LEV='基础性存款' AND IND_NAME_3_LEV='个人基础性存款' AND CURRENCY_TYPE='199'",
    "CLOAN": "IND_TYPE='报表指标' AND IND_NAME_1_LEV='对公业务（不含汇兑，含逾期）' AND IND_NAME_2_LEV='对公资产业务' AND IND_NAME_3_LEV='对公板块贷款' AND SUB_PROJ_TYPE='五级分类' AND CURRENCY_TYPE='01'",
    "RLOAN": "IND_TYPE='规模指标' AND IND_NAME_1_LEV='贷款' AND IND_NAME_2_LEV IN ('个人一般贷款','信用卡贷款') AND CURRENCY_TYPE='01'",
    "RETAIL_LOAN_CLASS": "IND_TYPE='报表指标' AND IND_NAME_1_LEV='零售信贷产品（含逾期）' AND SUB_PROJ_TYPE='五级分类' AND CURRENCY_TYPE='01'",
    "CORP_FTP": "IND_TYPE='盈利指标' AND IND_NAME_1_LEV='税前利润' AND IND_NAME_2_LEV='营业收入' AND IND_NAME_3_LEV='利息净收入' AND SUB_PROJ_TYPE='板块分类' AND SUB_PROJ_CODE='1' AND CURRENCY_TYPE='199'",
    "RETAIL_FTP": "IND_TYPE='盈利指标' AND IND_NAME_1_LEV='税前利润' AND IND_NAME_2_LEV='营业收入' AND IND_NAME_3_LEV='利息净收入' AND SUB_PROJ_TYPE='板块分类' AND SUB_PROJ_CODE='2' AND CURRENCY_TYPE='01'",
    "CUSTOMERS": "IND_TYPE='应用统计' AND IND_NAME_1_LEV='客户营销' AND IND_NAME_2_LEV='机构直营' AND IND_NAME_3_LEV='有效客户数' AND CURRENCY_TYPE='199'",
    "ATTENTION": "IND_TYPE='应用统计' AND IND_NAME_1_LEV='客户营销' AND IND_NAME_2_LEV='机构直营' AND IND_NAME_3_LEV='待跟进数' AND CURRENCY_TYPE='199'",
}

def _guard(field):
    return f"CASE WHEN {field} IS NULL OR TRIM({field})='' OR {field} NOT REGEXP '^[0-9]+([.][0-9]+)?$' THEN NULL ELSE CAST({field} AS DECIMAL(30,8)) END"

def _selector(selector):
    if selector == "DEP": return f"({SELECTORS['CDEP']}) OR ({SELECTORS['RDEP']})"
    if selector == "CORP_NPL": return f"({SELECTORS['CLOAN']}) AND SUB_PROJ_NAME IN ('次级','可疑','损失')"
    if selector == "RETAIL_NPL": return f"({SELECTORS['RETAIL_LOAN_CLASS']}) AND SUB_PROJ_NAME IN ('次级','可疑','损失')"
    if selector in SELECTORS: return SELECTORS[selector]
    raise ValueError("unknown selector")

def _expected(selector):
    return {"CDEP":1,"RDEP":1,"DEP":2,"CLOAN":5,"RLOAN":2,"CORP_NPL":3,"RETAIL_LOAN_CLASS":5,"RETAIL_NPL":3}.get(selector,1)

def _quality(selector, expected):
    base = f"COUNT(*)={expected} AND COUNT(DISTINCT DATA_SRC)=1 AND SUM(CASE WHEN {{value}} IS NULL THEN 1 ELSE 0 END)=0"
    if selector == "DEP": return base + " AND COUNT(DISTINCT IND_NAME_3_LEV)=2"
    if selector == "RLOAN": return base + " AND COUNT(DISTINCT IND_NAME_2_LEV)=2"
    if selector in {"CLOAN", "RETAIL_LOAN_CLASS"}: return base + " AND COUNT(DISTINCT SUB_PROJ_NAME)=5 AND COUNT(DISTINCT CASE WHEN SUB_PROJ_NAME IN ('正常','关注','次级','可疑','损失') THEN SUB_PROJ_NAME END)=5"
    if selector in {"CORP_NPL", "RETAIL_NPL"}: return base + " AND COUNT(DISTINCT SUB_PROJ_NAME)=3 AND COUNT(DISTINCT CASE WHEN SUB_PROJ_NAME IN ('次级','可疑','损失') THEN SUB_PROJ_NAME END)=3"
    return base

def employee_metric_sql(selector, field="CURR_BAL", comparison=None, object_id=None, preferred_data_source=None):
    if selector not in {"CDEP","RDEP","DEP","CLOAN","RLOAN","CORP_NPL","RETAIL_LOAN_CLASS","RETAIL_NPL","CORP_FTP","RETAIL_FTP","CUSTOMERS","ATTENTION"}: raise ValueError("unknown selector")
    if field not in FIELDS: raise ValueError("field is not allowed")
    if comparison not in COMPARISONS: raise ValueError("comparison is not allowed")
    if object_id is not None and (not isinstance(object_id, str) or not object_id.strip()): raise ValueError("object_id is not allowed")
    if preferred_data_source is not None and (not isinstance(preferred_data_source, str) or re.fullmatch(r"[A-Z][A-Z0-9_]{0,99}", preferred_data_source) is None): raise ValueError("preferred_data_source is not allowed")
    condition=_selector(selector); expected=_expected(selector); value=_guard(field)
    quality=_quality(selector,expected).format(value=value)
    current=f"SELECT EMP_ID, CASE WHEN {quality} THEN SUM({value}) ELSE NULL END AS metric_value FROM filtered GROUP BY EMP_ID"
    object_clause=" AND source_rows.EMP_ID=:objectId" if object_id is not None else ""
    preferred_join=""
    preferred_filter=""
    if preferred_data_source is not None:
        preferred_join=f" LEFT JOIN (SELECT DISTINCT EMP_ID AS preferred_emp_id,STATIS_DT AS preferred_date FROM {TABLE} WHERE DATA_SRC='{preferred_data_source}') preferred ON preferred.preferred_emp_id=source_rows.EMP_ID AND preferred.preferred_date=source_rows.STATIS_DT"
        preferred_filter=f" AND (source_rows.DATA_SRC='{preferred_data_source}' OR preferred.preferred_emp_id IS NULL)"
    base=f"SELECT source_rows.EMP_ID, source_rows.{field}, source_rows.DATA_SRC, source_rows.IND_NAME_1_LEV, source_rows.IND_NAME_2_LEV, source_rows.IND_NAME_3_LEV, source_rows.SUB_PROJ_NAME FROM {TABLE} source_rows{preferred_join} WHERE source_rows.STATIS_DT=:{{dateParam}} AND ({condition}){preferred_filter}{object_clause}"
    if comparison is None:
        return f"WITH filtered AS ({base.format(dateParam='dataDate')}), aggregated AS ({current}) SELECT EMP_ID AS base_key, metric_value FROM aggregated"
    prior=base.format(dateParam=comparison)
    return f"WITH filtered AS ({base.format(dateParam='dataDate')}), aggregated AS ({current}), prior_filtered AS ({prior}), prior_aggregated AS (SELECT EMP_ID, CASE WHEN {quality} THEN SUM({_guard(field)}) ELSE NULL END AS metric_value FROM prior_filtered GROUP BY EMP_ID) SELECT c.EMP_ID AS base_key, CASE WHEN c.metric_value IS NULL OR p.metric_value IS NULL THEN NULL ELSE c.metric_value-p.metric_value END AS metric_value FROM aggregated c LEFT JOIN prior_aggregated p ON p.EMP_ID=c.EMP_ID"

def ratio_metric_sql(numerator_slot, denominator_slot, npl=False):
    for slot in (numerator_slot, denominator_slot):
        if type(slot) is not int or not 1 <= slot <= 400: raise ValueError("slot must be int 1..400")
    if type(npl) is not bool: raise ValueError("npl must be bool")
    extra = " OR numerator > denominator" if npl else ""
    return f"SELECT base_key, CASE WHEN numerator IS NULL OR denominator IS NULL OR denominator <= 0 OR numerator < 0{extra} THEN NULL ELSE numerator * 100 / denominator END AS metric_value FROM (SELECT org_code AS base_key, val_{numerator_slot} AS numerator, val_{denominator_slot} AS denominator FROM ORG_INDEX_RESULT WHERE data_date=:dataDate AND version=:version) source"
