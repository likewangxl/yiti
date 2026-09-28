import { describe, expect, it } from 'vitest';
import {
  BRANCH_TEST_COLUMNS,
  buildBranchTestModel,
  buildBranchTestRequest,
  branchTestInstitutions,
  parseBranchTestView
} from '../branchTestDataset';

const directory = [
  { orgCode: 'B001', orgName: '宝鸡支行', cityCode: 'BJ', cityName: '宝鸡' },
  { orgCode: 'B002', orgName: '榆林支行', cityCode: 'YL', cityName: '榆林' },
  { orgCode: 'B003', orgName: '咸阳支行', cityCode: 'XY', cityName: '咸阳' }
];

const attentionBlockId = 9001;
const view = {
  state: 'published',
  screenCode: 'SCR_BRANCH_OPERATING_TEST',
  runtimeSchemaVersion: 2,
  orgScopeMode: 'NAMED_GROUP',
  panoramaInstitutions: directory,
  renderPackageJson: JSON.stringify({
    schemaVersion: 2,
    canvasStyle: {
      dataClassification: 'TEST',
      presentation: { type: 'CODE', template: 'branch-overview-v1' }
    },
    components: [
      { component: 'ChartWidget', blockId: attentionBlockId, innerType: 'TABLE_LIST', children: null, propValue: { bindingKey: 'attention' } }
    ],
    bindSnapshots: {
      [attentionBlockId]: { componentType: 'TABLE_LIST', bind: { dsId: 901, period: 'LATEST' } }
    }
  })
};

const orderedColumns = [
  'kind', 'org_code', 'key', 'name', 'data_date', 'unit', 'status', 'owner',
  'value', 'yoy', 'mom', 'actual', 'target', 'rate', 'gap', 'deposit', 'loan',
  'amount', 'increase', 'count', 'pending', 'days'
];

describe('新增经营细分来源', () => {
  it('固定二维TEST契约允许显式经营细分值，不从合计推拆', () => {
    const table = response();
    const row = Object.fromEntries(BRANCH_TEST_COLUMNS.map(column => [column, null]));
    Object.assign(row, { org_code: 'B001', kind: 'kpi', key: 'intermediaryIncome', name: '中间业务收入', data_date: '2026-09-21', unit: 'YUAN', value: 50000 });
    table.rows.push(orderedColumns.map(column => row[column]));
    const model = buildBranchTestModel(table, view, 'B001');
    expect(model.kpis).toEqual(expect.arrayContaining([expect.objectContaining({ key: 'intermediaryIncome', value: 0.0005, unit: '亿元' })]));
    expect(model.kpis.some(item => item.key === 'corpDeposit')).toBe(false);
  });
});

function makeResponseRows() {
  const rows = [];
  const push = (orgCode, kind, key, values = {}) => {
    const row = Object.fromEntries(BRANCH_TEST_COLUMNS.map(column => [column, null]));
    Object.assign(row, { org_code: orgCode, kind, key, data_date: '2026-09-21' }, values);
    rows.push(orderedColumns.map(column => row[column]));
  };
  for (const [orgCode, factor] of [['B001', 1], ['B002', 2], ['B003', 3]]) {
    push(orgCode, 'kpi', 'deposit', { name: '存款余额测试', unit: 'YUAN', value: factor * 100000000, yoy: factor, mom: -factor });
    push(orgCode, 'kpi', 'depositAverage', { name: '存款月均测试', unit: 'YUAN', value: factor * 90000000 });
    push(orgCode, 'kpi', 'loan', { name: '贷款余额测试', unit: 'YUAN', value: factor * 80000000 });
    push(orgCode, 'kpi', 'revenue', { name: '测试收入', unit: 'YUAN', value: factor * 125000 });
    push(orgCode, 'kpi', 'customers', { name: '客户用户数', unit: 'COUNT', value: factor * 2 });
    push(orgCode, 'kpi', 'rate', { name: '目标完成率', unit: 'PERCENT', value: factor * 10 });
    push(orgCode, 'target', 'deposit', { name: '存款目标', unit: 'YUAN', actual: factor * 100000000, target: factor * 200000000, gap: factor * 100000000 });
    push(orgCode, 'composition', 'corporate', { name: '对公', unit: 'YUAN', value: factor * 40000000 });
    push(orgCode, 'composition', 'retail', { name: '零售', unit: 'YUAN', value: factor * 60000000 });
    push(orgCode, 'marketing', 'PENDING', { name: '待触达', unit: 'COUNT', count: factor });
    push(orgCode, 'marketing', 'SLA_WARNING', { name: 'SLA预警', unit: 'COUNT', count: factor - 1 });
    push(orgCode, 'project', `project-${orgCode}`, { name: `项目-${orgCode}`, status: '推进中', owner: `负责人-${orgCode}`, unit: 'YUAN', amount: factor * 100000, days: factor });
    push(orgCode, 'team', `team-${orgCode}`, { name: `团队-${orgCode}`, unit: 'PERCENT', rate: factor * 20, increase: factor * 10000, pending: factor });
    push(orgCode, 'attention', `attention-${orgCode}`, { name: `关注-${orgCode}`, owner: `owner-${orgCode}`, unit: 'COUNT', count: factor });
    for (const [date, deposit, loan] of [
      ['2026-04-30', 40000000, 30000000], ['2026-05-31', 50000000, 40000000],
      ['2026-06-30', 60000000, 50000000], ['2026-07-31', 70000000, 60000000],
      ['2026-08-31', 80000000, 70000000], ['2026-09-21', 90000000, 80000000]
    ]) {
      push(orgCode, 'history', 'balance', { name: '历史余额', unit: 'YUAN', data_date: date, deposit: factor * deposit, loan: factor * loan });
    }
    push(orgCode, 'history', 'balance', { name: '同期历史', unit: 'YUAN', data_date: '2025-09-21', deposit: factor * 30000000, loan: factor * 20000000 });
    push(orgCode, 'history', 'balance', { name: '上月同期', unit: 'YUAN', data_date: '2026-08-21', deposit: factor * 35000000, loan: factor * 25000000 });
  }
  return rows;
}

function response(overrides = {}) {
  return {
    columns: orderedColumns,
    rows: makeResponseRows(),
    quality: {
      dataClassification: 'TEST',
      version: 'TEST_BRANCH_OPERATING_20260921',
      batchId: 'TEST_BRANCH_OPERATING_20260921',
      dataDate: '2026-09-21',
      status: 'COMPLETE'
    },
    ...overrides
  };
}

describe('分行经营 TEST 自由报表适配器', () => {
  it('严格读取发布身份，并从快照 blockId 构造 schema2 请求而不发送 dsId', () => {
    const parsed = parseBranchTestView(view);
    expect(parsed.attention.blockId).toBe(attentionBlockId);
    expect(parsed.renderPackage.schemaVersion).toBe(2);
    expect(buildBranchTestRequest(view)).toEqual({
      schemaVersion: 2,
      screenCode: 'SCR_BRANCH_OPERATING_TEST',
      blockId: attentionBlockId,
      period: 'LATEST',
      contextParams: {}
    });
    expect(buildBranchTestRequest(view, 'B002')).toEqual(expect.objectContaining({ contextParams: { orgCode: 'B002' } }));
    expect(buildBranchTestRequest(view)).not.toHaveProperty('dsId');
    expect(() => buildBranchTestRequest(view, 'OUTSIDE')).toThrow(/目录|机构/);
  });

  it('拒绝未发布、非 TEST、错误模板、重复 attention 或缺可信快照', () => {
    expect(() => parseBranchTestView({ ...view, state: 'draft' })).toThrow();
    expect(() => parseBranchTestView({ ...view, renderPackageJson: view.renderPackageJson.replace('TEST', 'PROD') })).toThrow(/TEST/);
    expect(() => parseBranchTestView({ ...view, renderPackageJson: view.renderPackageJson.replace('branch-overview-v1', 'corporate-overview-v1') })).toThrow(/模板/);
    const duplicate = JSON.parse(view.renderPackageJson);
    duplicate.components.push({ component: 'ChartWidget', blockId: 9002, innerType: 'TABLE_LIST', propValue: { bindingKey: 'attention' } });
    duplicate.bindSnapshots['9002'] = { componentType: 'TABLE_LIST', bind: { dsId: 902 } };
    expect(() => parseBranchTestView({ ...view, renderPackageJson: JSON.stringify(duplicate) })).toThrow(/唯一|多个/);
    const missingSnapshot = JSON.parse(view.renderPackageJson);
    delete missingSnapshot.bindSnapshots[attentionBlockId];
    expect(() => parseBranchTestView({ ...view, renderPackageJson: JSON.stringify(missingSnapshot) })).toThrow(/快照|身份/);
    const wrongInnerType = JSON.parse(view.renderPackageJson);
    wrongInnerType.components[0].innerType = 'BAR_COMPARE';
    expect(() => parseBranchTestView({ ...view, renderPackageJson: JSON.stringify(wrongInnerType) })).toThrow(/TABLE_LIST/);
    const wrongSnapshotType = JSON.parse(view.renderPackageJson);
    wrongSnapshotType.bindSnapshots[attentionBlockId].componentType = 'ChartWidget';
    expect(() => parseBranchTestView({ ...view, renderPackageJson: JSON.stringify(wrongSnapshotType) })).toThrow(/TABLE_LIST/);
  });

  it('返回机构目录与数据行机构号的交集，并拒绝目录外机构与重复目录 id', () => {
    expect(branchTestInstitutions(response(), view).map(item => item.orgCode)).toEqual(['B001', 'B002', 'B003']);
    const outside = [...makeResponseRows()[0]];
    outside[orderedColumns.indexOf('org_code')] = 'OUTSIDE';
    expect(() => branchTestInstitutions(response({ rows: [...makeResponseRows(), outside] }), view)).toThrow(/目录|机构/);
    const duplicateDirectory = [{ ...directory[0] }, { ...directory[0] }, directory[1]];
    expect(() => branchTestInstitutions(response(), { ...view, panoramaInstitutions: duplicateDirectory })).toThrow(/重复/);
  });

  it('按三家机构独立切换模型，使用响应列名并保留响应文字与金额单位', () => {
    const source = response();
    for (const institution of directory) {
      const model = buildBranchTestModel(source, view, institution.orgCode);
      expect(model).toMatchObject({
        orgCode: institution.orgCode,
        orgName: institution.orgName,
        sourceLabel: '测试数据 · 非实际经营数据',
        dataDate: '2026-09-21',
        targetDate: '2026-09-21',
        trendUnit: '亿元',
        gaps: {}
      });
      expect(model.institutions).toHaveLength(3);
      expect(model.kpis).toHaveLength(6);
      expect(model.kpis.find(item => item.key === 'deposit')).toMatchObject({ value: institution.orgCode === 'B001' ? 1 : institution.orgCode === 'B002' ? 2 : 3, unit: '亿元', yoy: institution.orgCode === 'B001' ? 1 : institution.orgCode === 'B002' ? 2 : 3 });
      expect(model.kpis.find(item => item.key === 'customers').unit).toBe('户');
      expect(model.targets[0]).toMatchObject({ actual: expect.any(Number), target: expect.any(Number), gap: expect.any(Number), unit: '亿元', rate: 50 });
      expect(model.trend).toHaveLength(6);
      expect(model.trend.every(item => item.deposit === null || item.loan === null || Number.isFinite(item.deposit))).toBe(true);
      expect(model.composition[0]).toMatchObject({ name: '对公', unit: '亿元' });
      expect(model.marketing.find(item => item.key === 'SLA_WARNING')).toMatchObject({ label: 'SLA预警' });
      expect(model.projects[0]).toMatchObject({ name: `项目-${institution.orgCode}`, owner: `负责人-${institution.orgCode}`, unit: '万元' });
      expect(model.teams[0]).toMatchObject({ name: `团队-${institution.orgCode}`, increaseUnit: '万元' });
      expect(model.attention[0]).toMatchObject({ label: `关注-${institution.orgCode}`, owner: `owner-${institution.orgCode}` });
    }
  });

  it('六项 KPI 缺失或重复时明确失败，不从其他机构补齐', () => {
    const rows = makeResponseRows();
    const columns = orderedColumns;
    const kindIndex = columns.indexOf('kind');
    const keyIndex = columns.indexOf('key');
    const orgIndex = columns.indexOf('org_code');
    const deposit = rows.find(row => row[orgIndex] === 'B002' && row[kindIndex] === 'kpi' && row[keyIndex] === 'deposit');
    rows.splice(rows.indexOf(deposit), 1);
    expect(() => buildBranchTestModel(response({ rows }), view, 'B002')).toThrow(/KPI|deposit/);
    rows.push(deposit);
    rows.push([...deposit]);
    expect(() => buildBranchTestModel(response({ rows }), view, 'B002')).toThrow(/重复/);
  });

  it('非有限数字拒绝，null/空白保持缺失，不把零值变成空值或零值互换', () => {
    const rows = makeResponseRows();
    const columns = orderedColumns;
    const valueIndex = columns.indexOf('value');
    const orgIndex = columns.indexOf('org_code');
    const keyIndex = columns.indexOf('key');
    const deposit = rows.find(row => row[orgIndex] === 'B002' && row[keyIndex] === 'deposit');
    deposit[valueIndex] = Infinity;
    expect(() => buildBranchTestModel(response({ rows }), view, 'B002')).toThrow(/数字|finite|有限/);

    const nullRows = makeResponseRows();
    const loan = nullRows.find(row => row[orgIndex] === 'B003' && row[keyIndex] === 'loan');
    loan[valueIndex] = null;
    expect(() => buildBranchTestModel(response({ rows: nullRows }), view, 'B003')).toThrow(/loan|缺少|KPI/);

    const zeroRows = makeResponseRows();
    const customer = zeroRows.find(row => row[orgIndex] === 'B001' && row[keyIndex] === 'customers');
    customer[valueIndex] = 0;
    const model = buildBranchTestModel(response({ rows: zeroRows }), view, 'B001');
    expect(model.kpis.find(item => item.key === 'customers').value).toBe(0);
  });

  it('质量必须持续为 TEST、批次前缀正确，日期取全体行最大日期', () => {
    expect(() => buildBranchTestModel(response({ quality: { ...response().quality, dataClassification: 'PROD' } }), view, 'B001')).toThrow(/TEST/);
    expect(() => buildBranchTestModel(response({ quality: { ...response().quality, version: 'LIVE_1' } }), view, 'B001')).toThrow(/批次|version/);
    expect(() => buildBranchTestModel(response({ quality: { ...response().quality, dataDate: '2026-09-20' } }), view, 'B001')).toThrow(/日期/);
    const rows = makeResponseRows();
    const dateIndex = orderedColumns.indexOf('data_date');
    rows[0][dateIndex] = '2026-09-22';
    expect(() => buildBranchTestModel(response({ rows }), view, 'B001')).toThrow(/日期/);
  });
});
