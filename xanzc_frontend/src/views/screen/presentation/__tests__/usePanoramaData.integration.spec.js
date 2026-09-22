// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';

const queryScreenData = vi.fn();
vi.mock('@/api/screen', () => ({ queryScreenData: (...args) => queryScreenData(...args) }));

import { normalizeBlockResult, usePanoramaData } from '../../panorama/usePanoramaData';
import { buildDisplayMetricsModel } from '../model/displayMetricsModel';
import { buildDisplaySeriesTableModel } from '../model/displaySeriesTableModel';
import { buildCompositionTabsModel } from '../model/compositionTabsModel';
import { buildInstitutionRankingModel } from '../model/institutionRankingModel';

const presentation = {
  type: 'CODE',
  template: 'branch-overview-v1',
  displaySchemaVersion: 1,
  display: {
    components: [
      {
        componentId: 'metric', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 0, visible: true,
        content: { mainField: 'value' },
        dataRefs: [{ blockId: 11, metricCode: 'M_DEPOSIT', metricName: '存款余额', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
      },
      {
        componentId: 'trend', componentType: 'TREND', layoutRegion: 'CENTER', order: 1, visible: true,
        content: { series: [{ seriesKey: 'deposit', field: 'deposit', label: '存款', unit: 'HUNDRED_MILLION' }] },
        dataRefs: [{ blockId: 12, metricCode: 'M_TREND', metricName: '趋势', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
      },
      {
        componentId: 'composition', componentType: 'COMPOSITION_TABS', layoutRegion: 'CENTER', order: 2, visible: true,
        content: { tabs: [{ tabKey: 'deposit', label: '存款', corporateField: 'corporate', retailField: 'retail', totalField: 'total', unit: 'HUNDRED_MILLION' }] },
        dataRefs: [{ blockId: 13, metricCode: 'M_COMPOSITION', metricName: '业务构成', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
      },
      {
        componentId: 'ranking', componentType: 'RANKING', layoutRegion: 'RIGHT', order: 3, visible: true,
        content: { rankingMetrics: [{ metricKey: 'value', field: 'value', label: '存款余额', unit: 'HUNDRED_MILLION', direction: 'DESC' }] },
        dataRefs: [{ blockId: 14, metricCode: 'M_RANKING', metricName: '机构排名', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
      }
    ]
  }
};

function binding(slot, blockId, fields, units) {
  return { id: `node-${slot}`, component: 'ChartWidget', blockId, innerType: 'METRIC_CARD', propValue: { bindingKey: slot },
    snapshot: { bind: { dsId: 9, period: 'LATEST', fields, units }, componentType: 'METRIC_CARD' } };
}

function view() {
  const components = [
    binding('deposit', 11, { value: 'deposit_raw' }, { value: 'HUNDRED_MILLION' }),
    binding('trend', 12, { date: 'data_date', deposit: 'deposit_raw' }, { deposit: 'HUNDRED_MILLION' }),
    binding('composition', 13, { corporate: 'corp_raw', retail: 'retail_raw', total: 'total_raw' }, { corporate: 'HUNDRED_MILLION', retail: 'HUNDRED_MILLION', total: 'HUNDRED_MILLION' }),
    binding('ranking', 14, { orgCode: 'org_code_raw', name: 'org_name_raw', value: 'value_raw' }, { value: 'HUNDRED_MILLION' })
  ];
  return {
    screenCode: 'SCR_CODE', runtimeSchemaVersion: 2,
    renderPackage: {
      canvasStyle: { presentation },
      components,
      bindSnapshots: Object.fromEntries(components.map(component => [String(component.blockId), component.snapshot]))
    },
    panoramaInstitutions: [{ orgCode: 'A', orgName: '甲' }, { orgCode: 'B', orgName: '乙' }]
  };
}

describe('usePanoramaData 到新展示模型的真实 raw 结果链路', () => {
  beforeEach(() => queryScreenData.mockReset());

  it('按发布 blockId 暴露只读 blockResults，四类展示模型都能读取各自配置字段', async () => {
    queryScreenData.mockImplementation(request => ({
      11: { columns: ['deposit_raw'], rows: [[4]] },
      12: { columns: ['data_date', 'deposit_raw'], rows: [['2026-08', 2], ['2026-09', 4]] },
      13: { columns: ['corp_raw', 'retail_raw', 'total_raw'], rows: [[4, 6, 10]] },
      14: { columns: ['org_code_raw', 'org_name_raw', 'value_raw'], rows: [['A', '甲', 4], ['B', '乙', 2]] }
    }[(request || {}).blockId] || { columns: [], rows: [] }));
    const state = usePanoramaData(ref(view()), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });

    await state.refresh();

    const blocks = state.model.value.blockResults;
    expect(blocks).toBeTruthy();
    expect(blocks['11']).toMatchObject({ value: 4, deposit_raw: 4, columns: ['deposit_raw'], units: { value: 'HUNDRED_MILLION' } });
    expect(blocks['11'].rows).toEqual([{ deposit_raw: 4, value: 4 }]);
    expect(blocks['13']).toMatchObject({ corporate: 4, retail: 6, total: 10, corp_raw: 4, retail_raw: 6, total_raw: 10 });

    const metrics = buildDisplayMetricsModel(presentation, state.model.value);
    expect(metrics.components[0]).toMatchObject({ blockId: 11, rawValue: 4, state: 'READY' });

    const series = buildDisplaySeriesTableModel(presentation, state.model.value);
    expect(series.components.find(item => item.componentId === 'trend')).toMatchObject({
      state: 'READY', rows: [{ date: '2026-08', deposit: 2 }, { date: '2026-09', deposit: 4 }]
    });

    const composition = buildCompositionTabsModel(presentation, state.model.value);
    expect(composition.tabs[0]).toMatchObject({ state: 'READY', corporate: { value: 4 }, retail: { value: 6 }, total: { value: 10 } });
    expect(composition.tabs[0].corporate.share).toBe(40);
    expect(composition.tabs[0].retail.share).toBe(60);

    const ranking = buildInstitutionRankingModel({
      institutions: state.model.value.institutions,
      sourceAuthorized: true,
      rows: state.model.value.blockResults['14'].rows,
      rankingMetrics: presentation.display.components[3].content.rankingMetrics
    });
    expect(ranking.rows.map(item => [item.orgCode, item.value])).toEqual([['A', 4], ['B', 2]]);
  });

  it('失败和403的刷新不保留本轮新 raw blockResults，也不把错误响应伪装成旧成功结果', async () => {
    queryScreenData.mockImplementation(request => (request || {}).blockId === 11
      ? { columns: ['deposit_raw'], rows: [[4]] }
      : { columns: [], rows: [] });
    const viewRef = ref(view());
    const state = usePanoramaData(viewRef, ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(state.model.value.blockResults['11'].value).toBe(4);

    queryScreenData.mockRejectedValueOnce(Object.assign(new Error('服务失败'), { status: 500 }));
    await state.refresh();
    expect(state.model.value.blockResults).toEqual({});

    queryScreenData.mockRejectedValueOnce(Object.assign(new Error('无权访问'), { status: 403 }));
    await state.refresh();
    expect(state.model.value.blockResults).toEqual({});
    expect(state.model.value.permissionStatus).toBe(403);
  });

  it('缺列或重复 raw 列只跳过不可信语义别名，并记录不可用原因，不猜测列名', () => {
    const result = normalizeBlockResult(99, {
      fields: { value: 'missing_value', date: 'raw_date', deposit: 'raw_date', rows: 'raw_date' },
      units: { value: 'HUNDRED_MILLION' }
    }, { columns: ['raw_date'], rows: [['2026-09']] });

    expect(result.value).toBeUndefined();
    expect(result.date).toBe('2026-09');
    expect(result.deposit).toBeUndefined();
    expect(result.rows).toEqual([{ raw_date: '2026-09', date: '2026-09' }]);
    expect(result.aliasIssues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'MISSING_ALIAS_COLUMN', semantic: 'value' }),
      expect.objectContaining({ code: 'DUPLICATE_ALIAS_COLUMN', semantic: 'deposit' }),
      expect.objectContaining({ code: 'INVALID_SEMANTIC_ALIAS', semantic: 'rows' })
    ]));
  });
});
