// 大屏数据源 config_json 组装/解析纯函数测试（TDD Red 先行）
// 覆盖：KPI_DETAIL 引导式 / fieldMeta 字段元数据 / WIDE_TABLE aggregation / scopeMode / 试跑预览列头
// 后端契约对齐 report-analytics-center ScreenDatasourceServiceImpl（spec 2026-07-17 §3）：
//   - KPI_DETAIL：SNAPSHOT→SINGLE、TREND→TIMESERIES；TREND metrics 每项 code+name 非空；valueCol ∈ score|completeRate
//   - fieldMeta：col 非空且不重复、role ∈ DIM|METRIC 必填
//   - aggregation：groupBy DATE→TIMESERIES、NONE/SUBJECT→SINGLE；filters op 白名单，IN 值为逗号分隔字符串（后端拆分）
import { describe, it, expect } from 'vitest';
import {
  defaultDsModel, deriveDsType, buildConfigJson, buildTimeParamJson,
  parseConfigJson, validateDsModel, buildPreviewColumns, formatPreviewCell,
  datasourceTryRunScopeMode, buildDatasourceTryRunRequest, buildDatasourceProbeRequest,
  AMOUNT_SCALE_OPTIONS
} from '../dsConfig';

/** 快速构造一个在 defaultDsModel 基础上打补丁的模型 */
function model(patch = {}) {
  const m = defaultDsModel();
  // 除专门验证“新建不得静默默认”的测试外，其余纯配置测试显式声明共用条线，
  // 避免无关的必填字段遮蔽各自要验证的配置规则。
  return { ...m, bizLine: 'COMMON', ...patch };
}

describe('deriveDsType —— ds_type 随配置联动（与后端强制规则一致）', () => {
  it('WIDE_TABLE 未开聚合 → TIMESERIES（现状行为）', () => {
    expect(deriveDsType(model({ sourceKind: 'WIDE_TABLE' }))).toBe('TIMESERIES');
  });
  it('WIDE_TABLE 聚合 groupBy=DATE → TIMESERIES', () => {
    const m = model({ sourceKind: 'WIDE_TABLE', aggEnabled: true });
    m.aggregation.groupBy = 'DATE';
    expect(deriveDsType(m)).toBe('TIMESERIES');
  });
  it('WIDE_TABLE 聚合 groupBy=NONE/SUBJECT → SINGLE', () => {
    const m = model({ sourceKind: 'WIDE_TABLE', aggEnabled: true });
    m.aggregation.groupBy = 'NONE';
    expect(deriveDsType(m)).toBe('SINGLE');
    m.aggregation.groupBy = 'SUBJECT';
    expect(deriveDsType(m)).toBe('SINGLE');
  });
  it('KPI_DETAIL SNAPSHOT → SINGLE；TREND → TIMESERIES', () => {
    const m = model({ sourceKind: 'KPI_DETAIL' });
    m.kpiDetail.mode = 'SNAPSHOT';
    expect(deriveDsType(m)).toBe('SINGLE');
    m.kpiDetail.mode = 'TREND';
    expect(deriveDsType(m)).toBe('TIMESERIES');
  });
  it('KPI_RESULT → TIMESERIES（后端强制）；CUSTOM_SQL → 跟随用户选择', () => {
    expect(deriveDsType(model({ sourceKind: 'KPI_RESULT' }))).toBe('TIMESERIES');
    expect(deriveDsType(model({ sourceKind: 'CUSTOM_SQL', dsType: 'SINGLE' }))).toBe('SINGLE');
    expect(deriveDsType(model({ sourceKind: 'CUSTOM_SQL', dsType: 'TIMESERIES' }))).toBe('TIMESERIES');
  });
});

describe('buildConfigJson —— 表单模型 → config_json 对象', () => {
  it('金额量级预设：METRIC 只落 amountScale，不携带自定义单位和小数位；未选预设时保留自定义值', () => {
    expect(AMOUNT_SCALE_OPTIONS).toEqual([
      { value: 'YUAN', label: '元', unit: '元', decimals: 2 },
      { value: 'TEN_THOUSAND_YUAN', label: '万元', unit: '万元', decimals: 2 },
      { value: 'HUNDRED_MILLION_YUAN', label: '亿元', unit: '亿元', decimals: 2 }
    ]);

    const m = model({ sourceKind: 'CUSTOM_SQL', dsType: 'SINGLE' });
    m.sql = { text: 'SELECT amount FROM RPT_X', dateCol: '' };
    m.fieldMeta = [
      {
        col: 'amount', alias: '存款余额', role: 'METRIC', amountScale: 'TEN_THOUSAND_YUAN',
        unit: '自定义单位', decimals: '4'
      },
      { col: 'ratio', role: 'METRIC', amountScale: '', unit: '%', decimals: '1' },
      { col: 'org_name', role: 'DIM', amountScale: 'YUAN', unit: '', decimals: null }
    ];

    expect(buildConfigJson(m).fieldMeta).toEqual([
      { col: 'amount', alias: '存款余额', role: 'METRIC', amountScale: 'TEN_THOUSAND_YUAN' },
      { col: 'ratio', role: 'METRIC', unit: '%', decimals: 1 },
      { col: 'org_name', role: 'DIM' }
    ]);
  });

  it('WIDE_TABLE 基础形态：table + metrics(仅 code) + schemaVersion:2 + scopeMode，未开聚合/无 fieldMeta 时不落对应键', () => {
    const m = model({ sourceKind: 'WIDE_TABLE' });
    m.wide.table = 'EMP_INDEX_RESULT';
    m.wide.metricCodes = ['M0001', 'M0002'];
    const cfg = buildConfigJson(m);
    expect(cfg).toEqual({
      schemaVersion: 2,
      table: 'EMP_INDEX_RESULT',
      metrics: [{ metricCode: 'M0001' }, { metricCode: 'M0002' }],
      scopeMode: 'SUBJECT'
    });
    expect(cfg).not.toHaveProperty('aggregation');
    expect(cfg).not.toHaveProperty('fieldMeta');
  });

  it('WIDE_TABLE 开启聚合：aggregation 落盘；filters 过滤整行空、IN 值保持逗号分隔字符串', () => {
    const m = model({ sourceKind: 'WIDE_TABLE', aggEnabled: true });
    m.wide.table = 'ORG_INDEX_RESULT';
    m.wide.metricCodes = ['M0003'];
    m.aggregation = {
      groupBy: 'SUBJECT', agg: 'SUM',
      filters: [
        { col: 'org_code', op: 'IN', value: 'ORG001, ORG002' },
        { col: '', op: 'EQ', value: '' }  // 用户加了但没填的空行 → 丢弃
      ]
    };
    const cfg = buildConfigJson(m);
    expect(cfg.aggregation).toEqual({
      groupBy: 'SUBJECT', agg: 'SUM',
      filters: [{ col: 'org_code', op: 'IN', value: 'ORG001, ORG002' }]
    });
  });

  it('WIDE_TABLE 聚合无有效 filters 时省略 filters 键', () => {
    const m = model({ sourceKind: 'WIDE_TABLE', aggEnabled: true });
    m.wide.metricCodes = ['M0001'];
    m.aggregation = { groupBy: 'DATE', agg: 'AVG', filters: [{ col: '', op: 'EQ', value: '' }] };
    const cfg = buildConfigJson(m);
    expect(cfg.aggregation).toEqual({ groupBy: 'DATE', agg: 'AVG' });
  });

  it('aggEnabled=false 时即使 aggregation 有残留值也不落盘（开关语义）', () => {
    const m = model({ sourceKind: 'WIDE_TABLE', aggEnabled: false });
    m.wide.metricCodes = ['M0001'];
    m.aggregation.groupBy = 'DATE';
    expect(buildConfigJson(m)).not.toHaveProperty('aggregation');
  });

  it('fieldMeta（全类型通用）：过滤整行空、trim、decimals 转数字，空数组不落键', () => {
    const m = model({ sourceKind: 'CUSTOM_SQL', dsType: 'SINGLE' });
    m.sql = { text: 'SELECT 1', dateCol: '' };
    m.fieldMeta = [
      { col: ' 存款余额 ', alias: '一般性存款', role: 'METRIC', unit: '万元', decimals: '2' },
      { col: 'data_date', alias: '', role: 'DIM', unit: '', decimals: null },
      { col: '', alias: '', role: 'METRIC', unit: '', decimals: null }  // 空行丢弃
    ];
    const cfg = buildConfigJson(m);
    expect(cfg.fieldMeta).toEqual([
      { col: '存款余额', alias: '一般性存款', role: 'METRIC', unit: '万元', decimals: 2 },
      { col: 'data_date', role: 'DIM' }
    ]);
    // fieldMeta 全空时不落键
    m.fieldMeta = [];
    expect(buildConfigJson(m)).not.toHaveProperty('fieldMeta');
  });

  it('KPI_DETAIL SNAPSHOT：schemaVersion:2 + 方案/主体/模式，不携带 metrics/valueCol', () => {
    const m = model({ sourceKind: 'KPI_DETAIL' });
    m.kpiDetail = { ...m.kpiDetail, schemeCode: 'KPI_2026_STD', subjectType: 'EMP', mode: 'SNAPSHOT' };
    const cfg = buildConfigJson(m);
    expect(cfg).toEqual({
      schemaVersion: 2, schemeCode: 'KPI_2026_STD', subjectType: 'EMP', mode: 'SNAPSHOT', scopeMode: 'SUBJECT'
    });
  });

  it('KPI_DETAIL TREND：携带 metrics 快照（code+name）与 valueCol（默认 score）', () => {
    const m = model({ sourceKind: 'KPI_DETAIL', scopeMode: 'GLOBAL' });
    m.kpiDetail = {
      schemeCode: 'KPI_2026_STD', subjectType: 'ORG', mode: 'TREND',
      metrics: [{ metricCode: 'M0001', metricName: '存款日均' }],
      valueCol: 'score', timeParams: ['LAST_1M']
    };
    const cfg = buildConfigJson(m);
    expect(cfg).toEqual({
      schemaVersion: 2, schemeCode: 'KPI_2026_STD', subjectType: 'ORG', mode: 'TREND',
      metrics: [{ metricCode: 'M0001', metricName: '存款日均' }],
      valueCol: 'score', scopeMode: 'GLOBAL'
    });
  });

  it('KPI_DETAIL TREND 取值列可选 completeRate', () => {
    const m = model({ sourceKind: 'KPI_DETAIL' });
    m.kpiDetail = {
      schemeCode: 'S1', subjectType: 'EMP', mode: 'TREND',
      metrics: [{ metricCode: 'M1', metricName: 'N1' }], valueCol: 'completeRate', timeParams: []
    };
    expect(buildConfigJson(m).valueCol).toBe('completeRate');
  });

  it('KPI_RESULT：cycleType + scopeMode；CUSTOM_SQL：sql/dateCol(空→null)', () => {
    const m1 = model({ sourceKind: 'KPI_RESULT' });
    m1.kpi.cycleType = 'QUARTERLY';
    expect(buildConfigJson(m1)).toEqual({ schemaVersion: 2, cycleType: 'QUARTERLY', scopeMode: 'SUBJECT' });

    const m2 = model({ sourceKind: 'CUSTOM_SQL', dsType: 'TIMESERIES', scopeMode: 'GLOBAL' });
    m2.sql = { text: 'SELECT stat_date, cnt FROM RPT_X', dateCol: 'stat_date' };
    expect(buildConfigJson(m2)).toEqual({
      schemaVersion: 2, sql: 'SELECT stat_date, cnt FROM RPT_X', dateCol: 'stat_date', scopeMode: 'GLOBAL'
    });
    m2.sql.dateCol = '';
    expect(buildConfigJson(m2).dateCol).toBeNull();
  });
});

describe('buildTimeParamJson —— 预设周期落 time_param_json', () => {
  it('WIDE_TABLE：序列化 wide.timeParams', () => {
    const m = model({ sourceKind: 'WIDE_TABLE' });
    m.wide.timeParams = ['LATEST', 'LAST_1M'];
    expect(buildTimeParamJson(m)).toBe('["LATEST","LAST_1M"]');
  });
  it('KPI_DETAIL TREND：序列化 kpiDetail.timeParams；SNAPSHOT 不携带（null）', () => {
    const m = model({ sourceKind: 'KPI_DETAIL' });
    m.kpiDetail.mode = 'TREND';
    m.kpiDetail.timeParams = ['LAST_10D'];
    expect(buildTimeParamJson(m)).toBe('["LAST_10D"]');
    m.kpiDetail.mode = 'SNAPSHOT';
    expect(buildTimeParamJson(m)).toBeNull();
  });
  it('其他类型 → null', () => {
    expect(buildTimeParamJson(model({ sourceKind: 'KPI_RESULT' }))).toBeNull();
    expect(buildTimeParamJson(model({ sourceKind: 'CUSTOM_SQL' }))).toBeNull();
  });
});

describe('parseConfigJson —— config_json → 表单模型补丁（读时兼容 v1 旧数据）', () => {
  it('金额量级预设回填；旧 fieldMeta 无 amountScale 时继续回填 unit/decimals', () => {
    const patch = parseConfigJson('CUSTOM_SQL', {
      sql: 'SELECT amount, ratio FROM RPT_X',
      fieldMeta: [
        { col: 'amount', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN' },
        { col: 'ratio', role: 'METRIC', unit: '%', decimals: 1 }
      ]
    });

    expect(patch.fieldMeta).toEqual([
      {
        col: 'amount', alias: '', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN', unit: '', decimals: null
      },
      { col: 'ratio', alias: '', role: 'METRIC', unit: '%', decimals: 1 }
    ]);
  });

  it('WIDE_TABLE v1 旧数据：无 fieldMeta/aggregation/scopeMode → 补默认（关聚合/空元数据/SUBJECT）', () => {
    const patch = parseConfigJson('WIDE_TABLE', {
      table: 'EMP_INDEX_RESULT',
      metrics: [{ metricCode: 'M0001', metricName: '存款', slot: 1 }]
    });
    expect(patch.wide.table).toBe('EMP_INDEX_RESULT');
    expect(patch.wide.metricCodes).toEqual(['M0001']);
    // 后端重写后的 config 带 slot → 供 filters col 下拉提示
    expect(patch.wide.slotCols).toEqual(['val_1']);
    expect(patch.aggEnabled).toBe(false);
    expect(patch.fieldMeta).toEqual([]);
    expect(patch.scopeMode).toBe('SUBJECT');
  });

  it('WIDE_TABLE v2：回填 aggregation（含 filters）与 fieldMeta（行字段补默认）', () => {
    const patch = parseConfigJson('WIDE_TABLE', {
      schemaVersion: 2,
      table: 'ORG_INDEX_RESULT',
      metrics: [{ metricCode: 'M0003', slot: 2 }],
      aggregation: { groupBy: 'DATE', agg: 'SUM', filters: [{ col: 'org_code', op: 'IN', value: 'A,B' }] },
      fieldMeta: [{ col: 'val_2', alias: '余额', role: 'METRIC' }],
      scopeMode: 'GLOBAL'
    });
    expect(patch.aggEnabled).toBe(true);
    expect(patch.aggregation).toEqual({
      groupBy: 'DATE', agg: 'SUM', filters: [{ col: 'org_code', op: 'IN', value: 'A,B' }]
    });
    expect(patch.fieldMeta).toEqual([{ col: 'val_2', alias: '余额', role: 'METRIC', unit: '', decimals: null }]);
    expect(patch.scopeMode).toBe('GLOBAL');
  });

  it('KPI_DETAIL TREND：回填方案/主体/模式/metrics 快照/valueCol', () => {
    const patch = parseConfigJson('KPI_DETAIL', {
      schemaVersion: 2, schemeCode: 'KPI_2026_STD', subjectType: 'ORG', mode: 'TREND',
      metrics: [{ metricCode: 'M1', metricName: 'N1' }], valueCol: 'completeRate'
    }, '["LAST_1M"]');
    expect(patch.kpiDetail).toEqual({
      schemeCode: 'KPI_2026_STD', subjectType: 'ORG', mode: 'TREND',
      metrics: [{ metricCode: 'M1', metricName: 'N1' }], valueCol: 'completeRate', timeParams: ['LAST_1M']
    });
  });

  it('KPI_DETAIL SNAPSHOT 缺省字段补默认（subjectType=EMP、valueCol=score、timeParams 默认集）', () => {
    const patch = parseConfigJson('KPI_DETAIL', { schemeCode: 'S1', mode: 'SNAPSHOT' });
    expect(patch.kpiDetail.subjectType).toBe('EMP');
    expect(patch.kpiDetail.valueCol).toBe('score');
    expect(Array.isArray(patch.kpiDetail.timeParams)).toBe(true);
  });

  it('CUSTOM_SQL / KPI_RESULT：回填原有字段 + 通用段', () => {
    const p1 = parseConfigJson('CUSTOM_SQL', { sql: 'SELECT 1', dateCol: null, scopeMode: 'GLOBAL' });
    expect(p1.sql).toEqual({ text: 'SELECT 1', dateCol: '' });
    expect(p1.scopeMode).toBe('GLOBAL');
    const p2 = parseConfigJson('KPI_RESULT', { cycleType: 'MONTHLY' });
    expect(p2.kpi.cycleType).toBe('MONTHLY');
    expect(p2.scopeMode).toBe('SUBJECT');
  });
});

describe('validateDsModel —— 与后端 43009 校验规则对齐的前置校验', () => {
  it('金额量级只允许 METRIC 使用；DIM 行携带 amountScale 时保存不得落盘', () => {
    const m = model({ sourceKind: 'KPI_RESULT' });
    m.fieldMeta = [{ col: 'org_name', role: 'DIM', amountScale: 'YUAN', unit: '', decimals: null }];
    expect(validateDsModel(m)).toEqual([]);
    expect(buildConfigJson(m).fieldMeta).toEqual([{ col: 'org_name', role: 'DIM' }]);
  });

  it('新建模型不为 bizLine 静默补值；NAMED_GROUP 仅在 ORG_INDEX_RESULT + org_code 宽表下合法', () => {
    const fresh = defaultDsModel();
    expect(fresh.bizLine).toBe('');
    expect(validateDsModel(fresh)).toEqual(expect.arrayContaining([expect.stringContaining('业务条线')])) ;
    fresh.bizLine = 'COMMON';
    fresh.scopeMode = 'NAMED_GROUP';
    fresh.wide.table = 'ORG_INDEX_RESULT';
    fresh.wide.metricCodes = ['M0001'];
    expect(validateDsModel(fresh)).toEqual([]);
  });

  it('KPI_DETAIL：方案必填；TREND 必须至少选一个指标', () => {
    const m = model({ sourceKind: 'KPI_DETAIL' });
    m.kpiDetail.schemeCode = '';
    expect(validateDsModel(m).some(e => e.includes('方案'))).toBe(true);
    m.kpiDetail.schemeCode = 'S1';
    m.kpiDetail.mode = 'TREND';
    m.kpiDetail.metrics = [];
    expect(validateDsModel(m).some(e => e.includes('指标'))).toBe(true);
    m.kpiDetail.metrics = [{ metricCode: 'M1', metricName: 'N1' }];
    expect(validateDsModel(m)).toEqual([]);
  });

  it('KPI_DETAIL TREND：metrics 缺 metricName 快照报错（后端 code+name 均非空）', () => {
    const m = model({ sourceKind: 'KPI_DETAIL' });
    m.kpiDetail = {
      ...m.kpiDetail, schemeCode: 'S1', mode: 'TREND',
      metrics: [{ metricCode: 'M1', metricName: '' }]
    };
    expect(validateDsModel(m).length).toBeGreaterThan(0);
  });

  it('WIDE_TABLE：指标必选；开聚合后 filters 行 col/op/value 必须填全', () => {
    const m = model({ sourceKind: 'WIDE_TABLE' });
    m.wide.metricCodes = [];
    expect(validateDsModel(m).some(e => e.includes('指标'))).toBe(true);
    m.wide.metricCodes = ['M0001'];
    m.aggEnabled = true;
    m.aggregation = { groupBy: 'SUBJECT', agg: 'SUM', filters: [{ col: 'org_code', op: 'EQ', value: '' }] };
    expect(validateDsModel(m).some(e => e.includes('过滤'))).toBe(true);
    m.aggregation.filters[0].value = 'X';
    expect(validateDsModel(m)).toEqual([]);
  });

  it('fieldMeta：col 重复 / role 非法 / decimals 非法均报错；整行空跳过', () => {
    const m = model({ sourceKind: 'KPI_RESULT' });
    m.fieldMeta = [
      { col: 'a', alias: '', role: 'METRIC', unit: '', decimals: null },
      { col: 'a', alias: '', role: 'DIM', unit: '', decimals: null }
    ];
    expect(validateDsModel(m).some(e => e.includes('重复'))).toBe(true);
    m.fieldMeta = [{ col: 'a', alias: '', role: 'BAD', unit: '', decimals: null }];
    expect(validateDsModel(m).length).toBeGreaterThan(0);
    m.fieldMeta = [{ col: 'a', alias: '', role: 'METRIC', unit: '', decimals: '2.5' }];
    expect(validateDsModel(m).some(e => e.includes('小数'))).toBe(true);
    m.fieldMeta = [{ col: '', alias: '', role: 'METRIC', unit: '', decimals: null }];
    expect(validateDsModel(m)).toEqual([]);
  });

  it('CUSTOM_SQL：SQL 必填；时序型必须声明日期列', () => {
    const m = model({ sourceKind: 'CUSTOM_SQL', dsType: 'TIMESERIES' });
    m.sql = { text: '', dateCol: '' };
    expect(validateDsModel(m).some(e => e.includes('SQL'))).toBe(true);
    m.sql.text = 'SELECT 1';
    expect(validateDsModel(m).some(e => e.includes('日期列'))).toBe(true);
    m.sql.dateCol = 'stat_date';
    expect(validateDsModel(m)).toEqual([]);
  });
});

describe('试跑预览增强 —— columnsMeta 列头与单元格格式化', () => {
  it('buildPreviewColumns：按 col 匹配 columnsMeta，别名替换列头、单位附注；无 meta 时原样', () => {
    const cols = buildPreviewColumns(
      ['metric_code', '存款余额', '完成率'],
      [
        { col: '存款余额', alias: '一般性存款', role: 'METRIC', unit: '万元', decimals: 2 },
        { col: '完成率', role: 'METRIC', unit: '%' }
      ]
    );
    expect(cols).toEqual([
      { col: 'metric_code', label: 'metric_code', meta: null },
      { col: '存款余额', label: '一般性存款（万元）', meta: { col: '存款余额', alias: '一般性存款', role: 'METRIC', unit: '万元', decimals: 2 } },
      { col: '完成率', label: '完成率（%）', meta: { col: '完成率', role: 'METRIC', unit: '%' } }
    ]);
  });

  it('buildPreviewColumns：columnsMeta 缺失（旧接口）时退化为原始列名', () => {
    expect(buildPreviewColumns(['a', 'b'], null)).toEqual([
      { col: 'a', label: 'a', meta: null },
      { col: 'b', label: 'b', meta: null }
    ]);
  });

  it('试跑预览：amountScale 预设按元转换到目标单位并固定 2 位', () => {
    const columns = buildPreviewColumns(
      ['存款余额'],
      [{ col: '存款余额', alias: '一般性存款', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN' }]
    );
    expect(columns[0]).toMatchObject({ col: '存款余额', label: '一般性存款（亿元）' });
    expect(formatPreviewCell(123456789, columns[0].meta)).toBe('1.23');
    expect(formatPreviewCell(100000000, columns[0].meta)).toBe('1.00');
  });

  it('formatPreviewCell：null/undefined → "—"（完成率 target=0 场景）；decimals 生效；非数值原样', () => {
    expect(formatPreviewCell(null, { decimals: 2 })).toBe('—');
    expect(formatPreviewCell(undefined, null)).toBe('—');
    expect(formatPreviewCell(98.456, { decimals: 2 })).toBe('98.46');
    expect(formatPreviewCell(98.456, null)).toBe(98.456);
    expect(formatPreviewCell('ORG001', { decimals: 2 })).toBe('ORG001');
  });
});

describe('命名机构组试跑请求 —— ScreenTryRunReqDTO', () => {
  const namedGroupRow = {
    sourceKind: 'WIDE_TABLE', dsType: 'TIMESERIES',
    configJson: JSON.stringify({ schemaVersion: 2, scopeMode: 'NAMED_GROUP', table: 'ORG_INDEX_RESULT' })
  };

  it('NAMED_GROUP 必须显式选择测试组，并只把选中组作为 testOrgGroupCode 发给后端', () => {
    expect(() => buildDatasourceTryRunRequest(namedGroupRow, { reason: '验证范围' }))
      .toThrow(/测试机构组/);
    expect(buildDatasourceTryRunRequest(namedGroupRow, {
      period: 'LAST_1M', orgCode: 'O1', empId: 'E1', testOrgGroupCode: 'G_REPORT', reason: '验证范围'
    })).toEqual({
      sourceKind: 'WIDE_TABLE', dsType: 'TIMESERIES', configJson: namedGroupRow.configJson,
      period: 'LAST_1M', dateFrom: null, dateTo: null,
      contextParams: { orgCode: 'O1', empId: 'E1' }, testOrgGroupCode: 'G_REPORT', reason: '验证范围'
    });
  });

  it('损坏或未知范围模式的 configJson Fail Close，不伪装成 SUBJECT 继续试跑', () => {
    expect(() => datasourceTryRunScopeMode('{bad json')).toThrow(/无法解析/);
    expect(() => datasourceTryRunScopeMode('{"scopeMode":"ALL_ORGS"}')).toThrow(/不受支持/);
    expect(datasourceTryRunScopeMode('{}')).toBe('SUBJECT'); // 仅存量配置的显式兼容口径
  });
});

describe('已保存数据源列探测请求 —— ScreenDatasourceProbeReqDTO', () => {
  const namedGroupRow = {
    id: 72, sourceKind: 'WIDE_TABLE', dsType: 'TIMESERIES',
    configJson: JSON.stringify({ schemaVersion: 2, scopeMode: 'NAMED_GROUP', table: 'ORG_INDEX_RESULT' })
  };

  it('NAMED_GROUP probe 必须有审计原因与显式测试组，且不泄露 sourceKind/configJson 到 body', () => {
    expect(() => buildDatasourceProbeRequest(namedGroupRow, { reason: '探测数值列' }))
      .toThrow(/测试机构组/);
    const body = buildDatasourceProbeRequest(namedGroupRow, {
      period: 'LAST_1M', contextParams: { orgCode: 'O1', empId: null },
      testOrgGroupCode: 'G_REPORT', reason: '探测数值列'
    });
    expect(body).toEqual({
      period: 'LAST_1M', dateFrom: null, dateTo: null,
      contextParams: { orgCode: 'O1', empId: null },
      testOrgGroupCode: 'G_REPORT', reason: '探测数值列'
    });
    expect(body).not.toHaveProperty('configJson');
    expect(body).not.toHaveProperty('sourceKind');
  });
});

describe('NAMED_GROUP 数据源来源约束', () => {
  it('只允许 ORG_INDEX_RESULT 的 WIDE_TABLE + org_code，CUSTOM_SQL 及其他来源 Fail Close', () => {
    const customSql = model({ sourceKind: 'CUSTOM_SQL', scopeMode: 'NAMED_GROUP' });
    customSql.sql = { text: 'SELECT 1', dateCol: '' };
    expect(validateDsModel(customSql)).toEqual(expect.arrayContaining([expect.stringMatching(/NAMED_GROUP.*WIDE_TABLE/)]));

    const empWide = model({ sourceKind: 'WIDE_TABLE', scopeMode: 'NAMED_GROUP' });
    empWide.wide = { ...empWide.wide, table: 'EMP_INDEX_RESULT', metricCodes: ['M1'] };
    expect(validateDsModel(empWide)).toEqual(expect.arrayContaining([expect.stringMatching(/ORG_INDEX_RESULT.*org_code/)]));

    const orgWide = model({ sourceKind: 'WIDE_TABLE', scopeMode: 'NAMED_GROUP' });
    orgWide.wide = { ...orgWide.wide, table: 'ORG_INDEX_RESULT', metricCodes: ['M1'] };
    expect(validateDsModel(orgWide)).toEqual([]);
  });
});
