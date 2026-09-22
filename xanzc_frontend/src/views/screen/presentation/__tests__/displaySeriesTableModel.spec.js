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
});
