"""Pure deterministic generator for formal-screen M98-compatible TEST source rows."""
from __future__ import annotations
import hashlib, json
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP

SOURCE_COLUMNS = ["BRANCH_NO", "STAT_LEV3_ORG_ID", "STAT_LEV3_ORG_NAME", "STAT_LEV4_ORG_ID", "STAT_LEV4_ORG_NAME", "STAT_LEV5_ORG_ID", "STAT_LEV5_ORG_NAME", "EMP_ID", "EMP_NAME", "EMP_STAT", "IND_TYPE", "IND_ID_1_LEV", "IND_NAME_1_LEV", "IND_ID_2_LEV", "IND_NAME_2_LEV", "IND_ID_3_LEV", "IND_NAME_3_LEV", "IND_ID_4_LEV", "IND_NAME_4_LEV", "IND_ID_5_LEV", "IND_NAME_5_LEV", "IND_ID_6_LEV", "IND_NAME_6_LEV", "SUB_PROJ_TYPE", "SUB_PROJ_CODE", "SUB_PROJ_NAME", "CURRENCY_TYPE", "CURRENCY_DESC", "CURR_BAL", "M_AVG_BAL", "Q_AVG_BAL", "Y_AVG_BAL", "INT_BAL", "FTP_BAL", "FEE_BAL", "INVEST_VAL", "SALES_AND_ADDTAX", "FTP_REVENUE", "EXPECT_LOSS", "BUSINESS_EXPENSE", "SALES_OUT", "PROFIT_BEFTAX", "INCOME_TAX", "RWA_BAL", "RWA_M_AVG_BAL", "RWA_Q_AVG_BAL", "RWA_Y_AVG_BAL", "RISK_COST", "EVA_BAL", "FLAG", "REMARK", "DATA_SRC", "HIVE_SYS_TIME", "STATIS_DT"]
_Q = Decimal("0.0001"); _ANCHOR = date(2026, 9, 21)

def _q(v): return Decimal(str(v)).quantize(_Q, rounding=ROUND_HALF_UP)
def _hash(*p): return Decimal(int.from_bytes(hashlib.sha256("|".join(map(str,p)).encode()).digest()[:4], "big"))/Decimal(2**32)
def _factor(org, d, label): return Decimal(1) + Decimal((d-_ANCHOR).days)/Decimal(1000) + (_hash(org,d,label)-Decimal("0.5"))/Decimal(100)
def _fmt(v): return None if v is None else format(_q(v), "f")
def _dates(start, end):
    out=[]; d=start
    while d<=end: out.append(d); d+=timedelta(days=1)
    return out
def _alloc(v, employees, org):
    weights=[Decimal(1)+_hash(org,e,"weight") for e in employees]; total=sum(weights,Decimal(0)); units=int((_q(v)*Decimal(10000)).to_integral_value()); result=[]; left=units
    for i,w in enumerate(weights):
        x=left if i==len(weights)-1 else int(Decimal(units)*w/total); result.append(Decimal(x)/Decimal(10000)); left-=x
    return result
def _alloc_int(v, employees, org):
    weights=[Decimal(1)+_hash(org,e,"count") for e in employees]; total=sum(weights,Decimal(0)); result=[]; left=int(v)
    for i,w in enumerate(weights):
        x=left if i==len(weights)-1 else int(Decimal(int(v))*w/total); result.append(x); left-=x
    return result
def _period_avg(cache, org, d, metric, start):
    vals=[cache[(org,x)][metric] for x in _dates(start,d)]
    return _q(sum(vals,Decimal(0))/Decimal(len(vals)))

def generate_m98_source_rows(base_snapshot, org_employee_map, dates, marker="GENERATED_SCREEN_20261109"):
    if base_snapshot.get("dataClassification") != "TEST": raise ValueError("base snapshot must be TEST")
    if not isinstance(marker, str) or not marker.startswith("GENERATED_") or len(marker) > 100: raise ValueError("invalid marker")
    if not isinstance(org_employee_map, dict) or not org_employee_map: raise ValueError("org_employee_map required")
    parsed=[]
    for value in dates:
        try: parsed.append(date.fromisoformat(value))
        except (TypeError,ValueError) as exc: raise ValueError("invalid date") from exc
    if not parsed: raise ValueError("dates required")
    if len(set(parsed)) != len(parsed): raise ValueError("duplicate dates")
    if (max(parsed)-min(parsed)).days > 1100: raise ValueError("date span too large")
    owners={}
    for org, employees in org_employee_map.items():
        if not isinstance(org, str) or not org.strip() or any(not isinstance(e, str) or not e.strip() for e in employees): raise ValueError("organisation and employee identifiers must be non-empty strings")
        if not employees: raise ValueError("organisation has no employees")
        for emp in employees:
            if emp in owners: raise ValueError("employee belongs to multiple organisations")
            owners[emp]=org
    source={}
    for r in base_snapshot.get("rows",[]):
        if not isinstance(r.get("orgCode"), str) or not r["orgCode"].strip() or r["orgCode"] in source: raise ValueError("duplicate or empty snapshot organisation")
        source[r["orgCode"]]=r["metricValues"]
    if set(source) != set(org_employee_map): raise ValueError("organisation map must match snapshot")
    required={"TEST_PROV_CORP_DEP","TEST_PROV_RETAIL_DEP","TEST_PROV_CORP_LOAN","TEST_PROV_RETAIL_LOAN","TEST_PROV_REVENUE","TEST_PROV_CUSTOMERS","TEST_PROV_ATTENTION","TEST_PROV_RETAIL_NPL_BAL","TEST_PROV_CORP_NPL_BAL"}
    cache={}
    cache_start = min(date(d.year, 1, 1) for d in parsed)
    cache_end = max(parsed)
    for org,m in source.items():
        vals={k:Decimal(str(m[k])) for k in required}
        if any(not v.is_finite() for v in vals.values()): raise ValueError("non-finite base value")
        if any(v<0 for v in vals.values()) or vals["TEST_PROV_RETAIL_NPL_BAL"]>vals["TEST_PROV_RETAIL_LOAN"] or vals["TEST_PROV_CORP_NPL_BAL"]>vals["TEST_PROV_CORP_LOAN"]: raise ValueError("invalid negative or NPL balance")
        for d in _dates(cache_start, cache_end):
            cache[(org,d)]={k:_q(vals[k]*_factor(org,d,k)) for k in ["TEST_PROV_CORP_DEP","TEST_PROV_RETAIL_DEP","TEST_PROV_CORP_LOAN","TEST_PROV_RETAIL_LOAN","TEST_PROV_REVENUE"]}
            cache[(org,d)]["TEST_PROV_CUSTOMERS"]=Decimal(int(vals["TEST_PROV_CUSTOMERS"]*_factor(org,d,"customers")))
            cache[(org,d)]["TEST_PROV_ATTENTION"]=Decimal(int(vals["TEST_PROV_ATTENTION"]*_factor(org,d,"attention")))
            cache[(org,d)]["TEST_PROV_RETAIL_NPL_BAL"]=_q(cache[(org,d)]["TEST_PROV_RETAIL_LOAN"]*(vals["TEST_PROV_RETAIL_NPL_BAL"]/vals["TEST_PROV_RETAIL_LOAN"] if vals["TEST_PROV_RETAIL_LOAN"] else 0))
            cache[(org,d)]["TEST_PROV_CORP_NPL_BAL"]=_q(cache[(org,d)]["TEST_PROV_CORP_LOAN"]*(vals["TEST_PROV_CORP_NPL_BAL"]/vals["TEST_PROV_CORP_LOAN"] if vals["TEST_PROV_CORP_LOAN"] else 0))
    for org in source:
        for d in parsed:
            if (org,d) not in cache: raise ValueError("date cache incomplete")
    rows=[]; period_cache={}
    def cached_period(org, d, metric, start):
        key=(org,d.isoformat(),metric,start.isoformat())
        if key not in period_cache:
            period_cache[key]=_period_avg(cache,org,d,metric,start)
        return period_cache[key]
    def add(org, emp, d, category, metric, share, meta, periods, integer=False):
        remark={"origin":"GENERATED","orgCode":org,"algorithm":"formal-screen-v1"}
        remark["valueUnit" if integer else "amountUnit"]="COUNT" if integer else "YUAN"
        row={k:None for k in SOURCE_COLUMNS}; row.update(BRANCH_NO="02900000",EMP_ID=emp,EMP_NAME="生成明细（非真实员工履历）",EMP_STAT="GENERATED",DATA_SRC=marker,STATIS_DT=d.isoformat(),REMARK=json.dumps(remark,ensure_ascii=False,separators=(",",":")))
        row.update(meta,CURR_BAL=str(int(share)) if integer else _fmt(share),M_AVG_BAL=None if integer else _fmt(periods[0]),Q_AVG_BAL=None if integer else _fmt(periods[1]),Y_AVG_BAL=None if integer else _fmt(periods[2]))
        rows.append(row)
    for org, employees in org_employee_map.items():
        for d in parsed:
            cur=cache[(org,d)]; month=date(d.year,d.month,1); quarter=date(d.year,((d.month-1)//3)*3+1,1); year=date(d.year,1,1)
            for emp,share_i in zip(employees, range(len(employees))):
                def series(metric): return ( cached_period(org,d,metric,month), cached_period(org,d,metric,quarter), cached_period(org,d,metric,year) )
                metric_source={"CDEP":"TEST_PROV_CORP_DEP","RDEP":"TEST_PROV_RETAIL_DEP","CLOAN":"TEST_PROV_CORP_LOAN","RLOAN":"TEST_PROV_RETAIL_LOAN","RETAIL_NPL":"TEST_PROV_RETAIL_LOAN","FTP":"TEST_PROV_REVENUE"}
                def emit(metric, amount, meta):
                    source_value = cache[(org,d)][metric_source[metric]]
                    ratio = Decimal(str(amount)) / source_value if source_value else Decimal(0)
                    period_values = tuple(_alloc(_q(series(metric_source[metric])[i] * ratio), employees, org)[share_i] for i in range(3))
                    add(org,emp,d,metric,metric,_alloc(amount,employees,org)[share_i],meta,period_values)
                cdep=cur["TEST_PROV_CORP_DEP"]; rdep=cur["TEST_PROV_RETAIL_DEP"]; cloan=cur["TEST_PROV_CORP_LOAN"]; rloan=cur["TEST_PROV_RETAIL_LOAN"]; rnpl=cur["TEST_PROV_RETAIL_NPL_BAL"]; cnpl=cur["TEST_PROV_CORP_NPL_BAL"]; rev=cur["TEST_PROV_REVENUE"]
                emit("CDEP",cdep,{"IND_TYPE":"报表指标","IND_NAME_1_LEV":"对公业务（不含汇兑）","IND_NAME_2_LEV":"对公负债业务","IND_NAME_3_LEV":"对公板块存款","SUB_PROJ_TYPE":"存款分类","SUB_PROJ_CODE":"1","SUB_PROJ_NAME":"低成本结算","CURRENCY_TYPE":"199","CURRENCY_DESC":"全币折人民币"})
                emit("RDEP",rdep,{"IND_TYPE":"规模指标","IND_NAME_1_LEV":"存款","IND_NAME_2_LEV":"基础性存款","IND_NAME_3_LEV":"个人基础性存款","SUB_PROJ_TYPE":"板块分类","SUB_PROJ_CODE":"2","SUB_PROJ_NAME":"零售","CURRENCY_TYPE":"199","CURRENCY_DESC":"全币折人民币"})
                corp_attention=min((cloan-cnpl)*Decimal("0.05"), cloan-cnpl)
                corp_parts=[cloan-cnpl-corp_attention,corp_attention,cnpl*Decimal("0.4"),cnpl*Decimal("0.3"),cnpl*Decimal("0.3")]
                for name,code,val in zip(["正常","关注","次级","可疑","损失"],["0",None,"02",None,None],corp_parts): emit("CLOAN",val,{"IND_TYPE":"报表指标","IND_NAME_1_LEV":"对公业务（不含汇兑，含逾期）","IND_NAME_2_LEV":"对公资产业务","IND_NAME_3_LEV":"对公板块贷款","SUB_PROJ_TYPE":"五级分类","SUB_PROJ_CODE":code,"SUB_PROJ_NAME":name,"CURRENCY_TYPE":"01","CURRENCY_DESC":"人民币"})
                for name,val in [("个人消费贷款",rloan*Decimal("0.85")),("信用卡贷款（生成分类）",rloan*Decimal("0.15"))]: emit("RLOAN",val,{"IND_TYPE":"规模指标","IND_NAME_1_LEV":"贷款","IND_NAME_2_LEV":"个人一般贷款" if name.startswith("个人") else "信用卡贷款","IND_NAME_3_LEV":name,"SUB_PROJ_TYPE":"零售信贷产品分类","CURRENCY_TYPE":"01","CURRENCY_DESC":"人民币"})
                retail_attention=min((rloan-rnpl)*Decimal("0.05"), rloan-rnpl)
                retail_parts=[rloan-rnpl-retail_attention,retail_attention,rnpl*Decimal("0.4"),rnpl*Decimal("0.3"),rnpl*Decimal("0.3")]
                for name,val in zip(["正常","关注","次级","可疑","损失"],retail_parts): emit("RETAIL_NPL",val,{"IND_TYPE":"报表指标","IND_NAME_1_LEV":"零售信贷产品（含逾期）","IND_NAME_2_LEV":"个人一般贷款","IND_NAME_3_LEV":"个人贷款","SUB_PROJ_TYPE":"五级分类","SUB_PROJ_NAME":name,"CURRENCY_TYPE":"01","CURRENCY_DESC":"人民币"})
                corp_rev=rev*cloan/(cloan+rloan) if cloan+rloan else 0
                for name,val,code,currency in [("公司",corp_rev,"1","199"),("零售",rev-corp_rev,"2","01")]: emit("FTP",val,{"IND_TYPE":"盈利指标","IND_NAME_1_LEV":"税前利润","IND_NAME_2_LEV":"营业收入","IND_NAME_3_LEV":"利息净收入","SUB_PROJ_TYPE":"板块分类","SUB_PROJ_CODE":code,"SUB_PROJ_NAME":name,"CURRENCY_TYPE":currency,"CURRENCY_DESC":"全币折人民币" if currency=="199" else "人民币","FTP_REVENUE":_fmt(_alloc(val,employees,org)[share_i])})
                for name,val in [("有效客户数",cur["TEST_PROV_CUSTOMERS"]),("待跟进数",cur["TEST_PROV_ATTENTION"])]: add(org,emp,d,"COUNT",name,_alloc_int(val,employees,org)[share_i],{"IND_TYPE":"应用统计","IND_NAME_1_LEV":"客户营销","IND_NAME_2_LEV":"机构直营","IND_NAME_3_LEV":name,"CURRENCY_TYPE":"199","CURRENCY_DESC":"全币折人民币"},(None,None,None),True)
    return rows
