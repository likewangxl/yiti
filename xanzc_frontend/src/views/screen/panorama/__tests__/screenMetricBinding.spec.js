import { describe, expect, it } from 'vitest';
import {
  bindingShape,
  buildMetricBindingPlan,
  filterCatalog,
  mergePeriodMetrics,
  suggestAssignments
} from '../screenMetricBinding';

const orgDeposit = {
  metricCode: 'M_0277',
  metricName: '对公一般性存款余额',
  baseDim: 'ORG',
  valSlot: 20,
  unit: 'YUAN',
  unitSource: 'COLUMN',
  metricCategory: '规模类',
  summaryRule: 'SUM',
  tableName: 'ORG_INDEX_RESULT',
  subjectColumn: 'org_code',
  outputColumn: '对公一般性存款余额',
  bindable: true
};
const orgRetail = {
  ...orgDeposit,
  metricCode: 'M_0309',
  metricName: '零售一般性存款余额',
  valSlot: 21,
  outputColumn: '零售一般性存款余额'
};
const monthAverage = {
  ...orgDeposit,
  metricCode: 'M_0265',
  metricName: '一般性存款月均余额-机构',
  valSlot: 12,
  unit: 'YUAN',
  unitSource: 'CONFIRMED_CODE',
  outputColumn: '一般性存款月均余额-机构'
};
const monthDelta = {
  ...orgDeposit,
  metricCode: 'M_0266',
  metricName: '一般性存款月均余额较上月-机构',
  valSlot: 51,
  unit: null,
  unitSource: 'NONE',
  outputColumn: '一般性存款月均余额较上月-机构'
};
const genericDeposit = {
  ...orgDeposit,
  metricCode: 'M_0900',
  metricName: '一般性存款余额',
  outputColumn: '一般性存款余额'
};

const valueField = { semantic: 'value', label: '数值', kind: 'metric', required: true, unitKinds: ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'] };
const screen = { bizLine: 'COMMON', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'GRP_XIAN' };

describe('screen metric binding plan', () => {
  it('不会把月均较上月建议成存款净增，也不会把通用存款名建议成对公指标', () => {
    const branch = suggestAssignments({
      template: 'branch-overview-v1',
      slot: 'depositIncrease',
      fieldSpecs: [valueField],
      metrics: [monthDelta, monthAverage, orgDeposit]
    });
    expect(branch.assignments.value).toBeNull();
    expect(branch.notes.join('')).toContain('M_0266');

    const average = suggestAssignments({
      template: 'branch-overview-v1',
      slot: 'depositAverage',
      fieldSpecs: [valueField],
      metrics: [monthDelta, monthAverage]
    });
    expect(average.assignments.value.metricCode).toBe('M_0265');

    const corporate = suggestAssignments({
      template: 'corp-overview-v1',
      slot: 'corpDeposit',
      fieldSpecs: [valueField],
      metrics: [genericDeposit, orgDeposit, orgRetail]
    });
    expect(corporate.assignments.value.metricCode).toBe('M_0277');
    expect(corporate.candidates.value.map(item => item.metricCode)).not.toContain('M_0900');
  });

  it('按机构宽表生成可试跑的聚合数据源，并复用相同指标的已有来源', () => {
    const plan = buildMetricBindingPlan({
      slot: 'deposit',
      slotLabel: '存款余额',
      template: 'branch-overview-v1',
      fieldSpecs: [valueField, { semantic: 'date', label: '数据日期', kind: 'dimension' }],
      screen,
      metrics: [orgDeposit],
      assignments: { value: { metricCode: 'M_0277', unit: 'YUAN' } },
      aggregation: { groupBy: 'NONE', agg: 'SUM' },
      filters: [{ target: 'metric', metricCode: 'M_0277', op: 'GT', value: '0' }],
      reason: '绑定存款余额',
      datasources: []
    });

    expect(plan.ok).toBe(true);
    expect(plan.binding).toMatchObject({
      period: 'LATEST',
      fields: { value: '对公一般性存款余额' },
      units: { value: 'YUAN' }
    });
    const config = JSON.parse(plan.datasourceRequest.configJson);
    expect(config).toMatchObject({
      schemaVersion: 2,
      scopeMode: 'NAMED_GROUP',
      table: 'ORG_INDEX_RESULT',
      metrics: [{ metricCode: 'M_0277' }],
      aggregation: { groupBy: 'NONE', agg: 'SUM', filters: [{ col: 'val_20', op: 'GT', value: '0' }] }
    });
    expect(config.fieldMeta[0]).toMatchObject({ col: '对公一般性存款余额', role: 'METRIC', unit: 'YUAN', semantic: 'value' });
    expect(plan.datasourceRequest.dsType).toBe('SINGLE');
    expect(plan.previewRow.configJson).toBe(plan.datasourceRequest.configJson);

    const saved = {
      id: 42,
      status: 'ACTIVE',
      sourceKind: 'WIDE_TABLE',
      bizLine: 'COMMON',
      configJson: plan.datasourceRequest.configJson
    };
    const reused = buildMetricBindingPlan({
      slot: 'deposit',
      slotLabel: '存款余额',
      template: 'branch-overview-v1',
      fieldSpecs: [valueField],
      screen,
      metrics: [orgDeposit],
      assignments: { value: { metricCode: 'M_0277', unit: 'YUAN' } },
      aggregation: { groupBy: 'NONE', agg: 'SUM' },
      filters: [{ target: 'metric', metricCode: 'M_0277', op: 'GT', value: '0' }],
      reason: '绑定存款余额',
      datasources: [saved]
    });
    expect(reused.existingDatasourceId).toBe(42);
    expect(reused.datasourceRequest).toBeNull();
  });

  it('命名机构组不能绑定员工指标，城市汇总不能由机构指标自动加总', () => {
    expect(bindingShape('citySummary').blocked).toBe(true);
    const plan = buildMetricBindingPlan({
      slot: 'deposit',
      slotLabel: '存款余额',
      template: 'branch-overview-v1',
      fieldSpecs: [valueField],
      screen,
      metrics: [{ ...orgDeposit, baseDim: 'EMP', tableName: 'EMP_INDEX_RESULT', subjectColumn: 'emp_id' }],
      assignments: { value: { metricCode: 'M_0277', unit: 'YUAN' } },
      aggregation: { groupBy: 'NONE', agg: 'SUM' },
      reason: '绑定',
      datasources: []
    });
    expect(plan.ok).toBe(false);
    expect(plan.errors.join('')).toContain('机构');
  });

  it('期间比较的增量指标进入同一结果表，按基准日回推改为按日期聚合', () => {
    const plan = buildMetricBindingPlan({
      slot: 'deposit',
      slotLabel: '存款总额',
      template: 'branch-overview-v1',
      fieldSpecs: [valueField],
      screen,
      metrics: [orgDeposit, monthDelta],
      assignments: { value: { metricCode: 'M_0277', unit: 'YUAN' } },
      periodComparisons: {
        day: { mode: 'UNSET' },
        month: { mode: 'METRIC', metricCode: 'M_0266', unit: 'YUAN' },
        year: { mode: 'OFFSET', metricCode: 'M_0277', unit: 'YUAN' }
      },
      aggregation: { groupBy: 'NONE', agg: 'SUM' },
      reason: '配置期间比较',
      datasources: []
    });
    expect(plan.ok).toBe(true);
    expect(plan.binding.fields).toEqual({ value: '对公一般性存款余额' });
    expect(plan.periodComparisons.month).toMatchObject({
      mode: 'METRIC', metricCode: 'M_0266', outputColumn: '一般性存款月均余额较上月-机构', unit: 'YUAN'
    });
    expect(plan.periodComparisons.year.mode).toBe('OFFSET');
    expect(plan.periodComparisons.day.mode).toBe('UNSET');
    const config = JSON.parse(plan.datasourceRequest.configJson);
    expect(config.metrics.map(item => item.metricCode).sort()).toEqual(['M_0266', 'M_0277']);
    expect(config.aggregation.groupBy).toBe('DATE');
    expect(config.fieldMeta.map(item => item.semantic)).toEqual(['value', 'periodMonth']);
    expect(plan.warnings.join('')).toContain('按日期');
    expect(plan.datasourceRequest.dsType).toBe('TIMESERIES');

    const merged = mergePeriodMetrics(config, [{
      metricCode: 'M_0298', metricName: '结算性存款', outputColumn: '结算性存款', unit: 'YUAN', tableName: 'ORG_INDEX_RESULT', semantic: 'periodDay'
    }]);
    expect(merged.ok).toBe(true);
    expect(merged.config.metrics.map(item => item.metricCode)).toContain('M_0298');
  });

  it('完成率期间比较按主指标比例量纲接受 PERCENT/RATIO，拒绝金额单位', () => {
    const rate = {
      ...orgDeposit,
      metricCode: 'M_RATE',
      metricName: '测试完成率',
      unit: 'RATIO',
      outputColumn: '测试完成率',
      summaryRule: 'AVG'
    };
    const accepted = buildMetricBindingPlan({
      slot: 'rate', slotLabel: '完成率', template: 'branch-overview-v1', fieldSpecs: [
        { semantic: 'value', label: '数值', kind: 'metric', required: true, unitKinds: ['PERCENT', 'RATIO'] }
      ], screen, metrics: [rate], assignments: { value: { metricCode: 'M_RATE', unit: 'PERCENT' } },
      periodComparisons: { month: { mode: 'METRIC', metricCode: 'M_RATE', unit: 'PERCENT' } },
      aggregation: { groupBy: 'NONE', agg: 'AVG' }, reason: '绑定完成率', datasources: []
    });
    expect(accepted.ok).toBe(true);
    expect(accepted.periodComparisons.month).toMatchObject({ metricCode: 'M_RATE', unit: 'PERCENT', outputColumn: '测试完成率' });

    const rejected = buildMetricBindingPlan({
      slot: 'rate', slotLabel: '完成率', template: 'branch-overview-v1', fieldSpecs: [
        { semantic: 'value', label: '数值', kind: 'metric', required: true, unitKinds: ['PERCENT', 'RATIO'] }
      ], screen, metrics: [rate], assignments: { value: { metricCode: 'M_RATE', unit: 'PERCENT' } },
      periodComparisons: { month: { mode: 'METRIC', metricCode: 'M_RATE', unit: 'YUAN' } },
      aggregation: { groupBy: 'NONE', agg: 'AVG' }, reason: '错误金额单位', datasources: []
    });
    expect(rejected.ok).toBe(false);
    expect(rejected.errors.join('')).toContain('量纲');
  });

  it('期间比较不能跨越主指标量纲，金额与比例互相拒绝', () => {
    const rate = {
      ...orgDeposit,
      metricCode: 'M_RATE_CROSS',
      metricName: '测试完成率',
      unit: 'PERCENT',
      outputColumn: '测试完成率',
      summaryRule: 'AVG'
    };
    const amount = {
      ...orgDeposit,
      metricCode: 'M_AMOUNT_CROSS',
      metricName: '测试金额',
      unit: 'YUAN',
      outputColumn: '测试金额'
    };
    const ratioMainAmountComparison = buildMetricBindingPlan({
      slot: 'rate', slotLabel: '完成率', template: 'branch-overview-v1', fieldSpecs: [
        { semantic: 'value', label: '数值', kind: 'metric', required: true, unitKinds: ['PERCENT', 'RATIO'] }
      ], screen, metrics: [rate, amount], assignments: { value: { metricCode: 'M_RATE_CROSS', unit: 'PERCENT' } },
      periodComparisons: { month: { mode: 'METRIC', metricCode: 'M_AMOUNT_CROSS', unit: 'YUAN' } },
      aggregation: { groupBy: 'NONE', agg: 'AVG' }, reason: '完成率跨量纲', datasources: []
    });
    expect(ratioMainAmountComparison.ok).toBe(false);
    expect(ratioMainAmountComparison.errors.join('')).toContain('主指标');

    const amountMainRatioComparison = buildMetricBindingPlan({
      slot: 'deposit', slotLabel: '存款', template: 'branch-overview-v1', fieldSpecs: [valueField],
      screen, metrics: [rate, amount], assignments: { value: { metricCode: 'M_AMOUNT_CROSS', unit: 'YUAN' } },
      periodComparisons: { month: { mode: 'METRIC', metricCode: 'M_RATE_CROSS', unit: 'PERCENT' } },
      aggregation: { groupBy: 'NONE', agg: 'SUM' }, reason: '金额跨量纲', datasources: []
    });
    expect(amountMainRatioComparison.ok).toBe(false);
    expect(amountMainRatioComparison.errors.join('')).toContain('主指标');
  });

  it('结算性存款只有期间比较时仍按金额量纲拒绝比例指标', () => {
    const rate = {
      ...orgDeposit,
      metricCode: 'M_RATE_SETTLEMENT',
      metricName: '结算完成率',
      unit: 'PERCENT',
      outputColumn: '结算完成率',
      summaryRule: 'AVG'
    };
    const plan = buildMetricBindingPlan({
      slot: 'settlementDeposit', slotLabel: '结算性存款', template: 'branch-overview-v1', fieldSpecs: [],
      screen, metrics: [rate], assignments: {}, comparisonsOnly: true,
      periodComparisons: { month: { mode: 'METRIC', metricCode: 'M_RATE_SETTLEMENT', unit: 'PERCENT' } },
      aggregation: { groupBy: 'NONE', agg: 'SUM' }, reason: '', datasources: []
    });
    expect(plan.ok).toBe(false);
    expect(plan.errors.join('')).toContain('主指标');
  });

  it('目录过滤只保留可绑定且维度匹配的指标', () => {
    const rows = filterCatalog([
      orgDeposit,
      { ...monthDelta, bindable: false },
      { ...orgRetail, baseDim: 'CUST', tableName: 'CUST_INDEX_RESULT' }
    ], { keyword: '存款', baseDim: 'ORG', bindableOnly: true });
    expect(rows.map(item => item.metricCode)).toEqual(['M_0277']);
  });
});
