import { describe, expect, it } from 'vitest';
import { buildDisplaySeriesTableModel } from '../model/displaySeriesTableModel';

const ref = blockId => [{ blockId, role: 'PRIMARY', metricCode: `M${blockId}`, metricName: '指标', unit: 'YUAN', dimension: 'ORG' }];
const presentation = components => ({ displaySchemaVersion: 1, display: { components } });

describe('displaySeriesTableModel', () => {
  it('旧协议不启用，趋势保留0/null并按配置系列顺序', () => {
    expect(buildDisplaySeriesTableModel({}, {})).toMatchObject({ enabled: false });
    const result = buildDisplaySeriesTableModel(presentation([{ componentId: 'trend', componentType: 'TREND', visible: true, order: 0,
      text: { title: '经营趋势' }, content: { series: [{ seriesKey: 'loan', field: 'loan', label: '贷款', unit: 'YUAN' }, { seriesKey: 'deposit', field: 'deposit', label: '存款', unit: 'YUAN' }] }, dataRefs: ref(1) }]),
    { blockResults: { 1: [{ date: '2026-01', loan: 0, deposit: null }, { date: '2026-02', loan: 2, deposit: 3 }] } });
    expect(result.components[0].series.map(item => item.key)).toEqual(['loan', 'deposit']);
    expect(result.components[0].series[0].values).toEqual([0, 2]);
    expect(result.components[0].series[1].values).toEqual([null, 3]);
  });

  it('缺日期和重复日期明确报错，不复制快照生成曲线', () => {
    const result = buildDisplaySeriesTableModel(presentation([{ componentId: 'trend', componentType: 'TREND', visible: true,
      content: { series: [{ seriesKey: 'v', field: 'value', label: '值', unit: 'YUAN' }] }, dataRefs: ref(1) }]),
    { blockResults: { 1: [{ value: 1 }, { date: '2026-01', value: 2 }, { date: '2026-01', value: 3 }] } });
    expect(result.components[0].state).toBe('INVALID');
    expect(result.components[0].issues.join(' ')).toMatch(/缺少日期|重复/);
    expect(result.components[0].rows).toHaveLength(1);
  });

  it('金额和户数混用同轴时拒绝', () => {
    const result = buildDisplaySeriesTableModel(presentation([{ componentId: 'trend', componentType: 'TREND', visible: true,
      content: { series: [{ seriesKey: 'a', field: 'amount', label: '金额', unit: 'YUAN' }, { seriesKey: 'c', field: 'count', label: '户数', unit: 'COUNT' }] }, dataRefs: ref(1) }]),
    { trend: [{ date: '2026-01', amount: 1, count: 2 }] });
    expect(result.components[0]).toMatchObject({ state: 'INVALID' });
    expect(result.components[0].issues[0]).toContain('单位类型不兼容');
  });

  it('明细列按配置顺序和visible过滤，行顺序稳定且0不为空', () => {
    const result = buildDisplaySeriesTableModel(presentation([{ componentId: 'table', componentType: 'DETAIL_TABLE', visible: true,
      format: { decimals: 0 }, text: { title: '机构明细' }, content: { columns: [
        { columnKey: 'loan', field: 'loan', label: '贷款', unit: 'YUAN', visible: true },
        { columnKey: 'hidden', field: 'x', label: '隐藏', unit: 'COUNT', visible: false },
        { columnKey: 'deposit', field: 'deposit', label: '存款', unit: 'YUAN', visible: true }
      ] }, dataRefs: ref(2) }]), { blockResults: { 2: [{ orgCode: 'B', loan: 0, deposit: 2 }, { orgCode: 'A', loan: 1, deposit: null }] } });
    expect(result.components[0].columns.map(item => item.columnKey)).toEqual(['loan', 'deposit']);
    expect(result.components[0].rows.map(item => item.key)).toEqual(['B', 'A']);
    expect(result.components[0].rows[0].cells[0].text).toBe('0元');
    expect(result.components[0].rows[1].cells[1].text).toBe('—');
  });

  it('机构维度明细只展示 institutionViewModel 已授权目录中的机构', () => {
    const result = buildDisplaySeriesTableModel(presentation([{ componentId: 'table', componentType: 'DETAIL_TABLE', visible: true,
      format: { decimals: 0 }, content: { columns: [
        { columnKey: 'orgCode', field: 'orgCode', label: '机构', unit: 'AUTO', visible: true },
        { columnKey: 'deposit', field: 'deposit', label: '存款', unit: 'YUAN', visible: true }
      ] }, dataRefs: ref(5) }]), {
      institutions: [{ orgCode: 'A', orgName: '甲机构' }],
      blockResults: { 5: [
        { orgCode: 'A', deposit: 1 },
        { orgCode: 'B', deposit: 2 }
      ] }
    });

    expect(result.components[0].rows).toHaveLength(1);
    expect(result.components[0].rows[0].key).toBe('A');
  });

  it('AUTO维度保留原始文本，空值使用emptyText且对象不会被字符串化', () => {
    const result = buildDisplaySeriesTableModel(presentation([{ componentId: 'table', componentType: 'DETAIL_TABLE', visible: true,
      format: { decimals: 2, emptyText: '暂无' }, content: { columns: [
        { columnKey: 'orgName', field: 'orgName', label: '机构名称', unit: 'AUTO', visible: true },
        { columnKey: 'orgCode', field: 'orgCode', label: '机构编码', unit: 'AUTO', visible: true },
        { columnKey: 'zero', field: 'zero', label: '零值', unit: 'AUTO', visible: true },
        { columnKey: 'nullValue', field: 'nullValue', label: '空值', unit: 'AUTO', visible: true },
        { columnKey: 'undefinedValue', field: 'undefinedValue', label: '缺值', unit: 'AUTO', visible: true },
        { columnKey: 'emptyValue', field: 'emptyValue', label: '空文本', unit: 'AUTO', visible: true },
        { columnKey: 'unsafe', field: 'unsafe', label: '备注', unit: 'AUTO', visible: true },
        { columnKey: 'objectValue', field: 'objectValue', label: '对象', unit: 'AUTO', visible: true }
      ] }, dataRefs: ref(3) }]), { blockResults: { 3: [{
      orgName: '西安分行', orgCode: '001', zero: 0, nullValue: null,
      unsafe: '<script>alert(1)</script>', objectValue: { html: '<script>alert(2)</script>' }
    }] } });
    const cells = result.components[0].rows[0].cells;

    expect(result.components[0].columns.map(item => item.columnKey)).toEqual([
      'orgName', 'orgCode', 'zero', 'nullValue', 'undefinedValue', 'emptyValue', 'unsafe', 'objectValue'
    ]);
    expect(cells.map(cell => cell.key)).toEqual([
      'orgName', 'orgCode', 'zero', 'nullValue', 'undefinedValue', 'emptyValue', 'unsafe', 'objectValue'
    ]);
    expect(cells.map(cell => cell.text)).toEqual([
      '西安分行', '001', '0', '暂无', '暂无', '暂无', '<script>alert(1)</script>', '暂无'
    ]);
  });

  it('数值单位仍按小数位和单位格式化', () => {
    expect(buildDisplaySeriesTableModel(presentation([{ componentId: 'table', componentType: 'DETAIL_TABLE', visible: true,
      format: { decimals: 2 }, content: { columns: [
        { columnKey: 'amount', field: 'amount', label: '金额', unit: 'YUAN', visible: true }
      ] }, dataRefs: ref(4) }]), { blockResults: { 4: [{ amount: '1234.5' }] } })
      .components[0].rows[0].cells[0].text).toBe('1,234.50元');
  });
});
