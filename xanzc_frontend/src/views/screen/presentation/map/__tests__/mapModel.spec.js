import { describe, expect, it } from 'vitest';
import {
  buildMapModel,
  findVisibleMapComponent,
  formatMapMetric,
  mapContextForCity,
  mapContextForInstitution
} from '../mapModel';

const presentation = {
  displaySchemaVersion: 1,
  display: {
    components: [{
      componentId: 'map-main',
      componentType: 'MAP',
      layoutRegion: 'CENTER',
      order: 1,
      visible: true,
      text: { titleMode: 'AUTO', title: '', subtitle: '排名指标地图' },
      format: { displayUnit: 'HUNDRED_MILLION', decimals: 2, emptyText: '暂无数据' },
      content: { mainField: 'deposit', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] },
      interaction: { action: 'OPEN_CITY' },
      dataRefs: [{ blockId: 1, role: 'PRIMARY', metricCode: 'M_DEPOSIT', metricName: '存款余额', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
    }]
  }
};

const model = {
  dataDate: '2026-09-22',
  citySummaries: {
    '610100': { dataDate: '2026-09-22', kpis: [{ key: 'deposit', value: 100, unit: '亿元' }] },
    '610200': { dataDate: '2026-09-22', kpis: [{ key: 'deposit', value: null, unit: '亿元' }] }
  },
  institutions: [
    { orgCode: 'A', orgName: '甲机构', cityCode: '610100', ownerOperatingOrgCode: 'OWNER-2', located: true, lng: 108.9, lat: 34.2, coordSys: 'GCJ02' },
    { orgCode: 'B', orgName: '乙机构', cityCode: '610100', ownerOperatingOrgCode: '610200', located: false },
    { orgCode: 'C', orgName: '丙机构', cityCode: '610200', ownerOperatingOrgCode: '610100', located: true, lng: 109.1, lat: 35.1, coordSys: 'GCJ02' }
  ],
  rankings: [
    { orgCode: 'A', cityCode: '610100', deposit: 20, increase: 3 },
    { orgCode: 'B', cityCode: '610100', deposit: 30, increase: 4 },
    { orgCode: 'C', cityCode: '610200', deposit: null, increase: -2 }
  ]
};

describe('S12 MAP 展示适配', () => {
  it('只在 displaySchemaVersion=1 且存在可见 MAP 时启用', () => {
    expect(findVisibleMapComponent(presentation)?.componentId).toBe('map-main');
    expect(findVisibleMapComponent({ ...presentation, displaySchemaVersion: 2 })).toBeNull();
    expect(findVisibleMapComponent({ ...presentation, display: { components: [{ ...presentation.display.components[0], visible: false }] } })).toBeNull();
  });

  it('按选中的排名指标和日期生成区域值、单位、图例与缺数中性态', () => {
    const result = buildMapModel(presentation, model, { metricKey: 'deposit', level: 'province' });
    expect(result.enabled).toBe(true);
    expect(result.metricKey).toBe('deposit');
    expect(result.metricLabel).toBe('存款余额');
    expect(result.dataDate).toBe('2026-09-22');
    expect(result.viewFit.reliefFitHeight).toBeGreaterThan(0);
    expect(result.metricValues['610100']).toBe('100.00亿元');
    expect(result.metricStates['610200']).toMatchObject({ state: 'MISSING', color: '#65738a' });
    expect(result.legend.some(item => item.key === 'missing' && item.color === '#65738a')).toBe(true);
  });

  it('不把多个机构行相加，也不以 ownerOperatingOrgCode 替代 cityCode', () => {
    const province = buildMapModel(presentation, model, { metricKey: 'increase', level: 'province' });
    expect(province.metricValues['610100']).toBe('暂无数据');
    const city = buildMapModel(presentation, model, { metricKey: 'deposit', level: 'city', cityCode: '610100' });
    expect(city.viewFit.reliefFitHeight).toBeLessThan(province.viewFit.reliefFitHeight);
    expect(city.points.map(item => item.orgCode)).toEqual(['A', 'B']);
    expect(city.missingCoordinates.map(item => item.orgCode)).toEqual(['B']);
    expect(city.points.map(item => item.cityCode)).toEqual(['610100', '610100']);
  });

  it('机构没有坐标时仍保留在城市可访问集合，并明确无可见机构城市状态', () => {
    const empty = buildMapModel(presentation, { ...model, institutions: [] }, { level: 'city', cityCode: '610100' });
    expect(empty.noVisibleInstitutions).toBe(true);
    expect(empty.status).toBe('NO_VISIBLE_INSTITUTIONS');
    expect(formatMapMetric(null, { displayUnit: 'HUNDRED_MILLION', decimals: 2, emptyText: '暂无数据' })).toBe('暂无数据');
  });

  it('城市和机构上下文事件使用固定身份字段', () => {
    expect(mapContextForCity({ code: '610100', name: '西安市' }, { metricKey: 'deposit', dataDate: '2026-09-22' })).toEqual({
      level: 'CITY', cityCode: '610100', cityName: '西安市', orgCode: null, ownerOperatingOrgCode: null,
      metricKey: 'deposit', dataDate: '2026-09-22'
    });
    expect(mapContextForInstitution({ orgCode: 'A', orgName: '甲机构', cityCode: '610100', ownerOperatingOrgCode: 'OWNER-2' }, { metricKey: 'deposit', dataDate: '2026-09-22' })).toEqual({
      level: 'INSTITUTION', cityCode: '610100', cityName: null, orgCode: 'A', ownerOperatingOrgCode: 'OWNER-2',
      metricKey: 'deposit', dataDate: '2026-09-22'
    });
  });
});
