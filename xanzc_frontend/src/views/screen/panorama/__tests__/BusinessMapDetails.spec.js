// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { shallowMount, mount } from '@vue/test-utils';
import PanoramaMap from '../PanoramaMap.vue';
import { provinceGeo } from '../geography.js';
import RetailDashboard from '../RetailDashboard.vue';
import CorporateDashboard from '../CorporateDashboard.vue';
import { buildCityMapDetails, cityMapMetricValues } from '../cityMapDetails';
const institutions = [{ orgCode: 'A', orgName: '西安支行', cityCode: '610100' }];
const valueOf = (detail, key) => detail.metrics.find(metric => metric.key === key)?.value;
describe('业务经营地图详情', () => {
  it('业务 KPI 优先并保留单位，不读取分行或另一业务 KPI', () => {
    const model = { institutions, citySummaries: { '610100': { kpis: [{ key: 'retailDeposit', value: 125, unit: '万元' }, { key: 'corpDeposit', value: 300, unit: '万元' }, { key: 'loan', value: 999, unit: '亿元' }] } } };
    const retail = buildCityMapDetails(model, { business: 'retail' })['610100'];
    const corporate = buildCityMapDetails(model, { business: 'corporate' })['610100'];
    expect(valueOf(retail, 'deposit')).toBe('125万元');
    expect(valueOf(corporate, 'deposit')).toBe('300万元');
    expect(valueOf(retail, 'loan')).toBe('暂无数据');
    expect(valueOf(corporate, 'loan')).toBe('暂无数据');
  });
  it('多机构城市逐家展示业务排名，不累加或读取顶层 KPI', () => {
    const model = { institutions: [...institutions, { orgCode: 'B', cityCode: '610100' }], kpis: [{ key: 'retailDeposit', value: 999 }], rankings: [{ orgCode: 'A', cityCode: '610100', deposit: 2, average: 1, nplRate: 0 }] };
    const detail = buildCityMapDetails(model, { business: 'retail' })['610100'];
    expect(valueOf(detail, 'deposit')).toBe('暂无数据');
    expect(valueOf(detail.institutions[0], 'deposit')).toBe('2亿元');
    expect(valueOf(detail.institutions[0], 'average')).toBe('1亿元');
    expect(valueOf(detail.institutions[1], 'deposit')).toBe('暂无数据');
  });
  it.each([[RetailDashboard, 'retail', 'retailDeposit'], [CorporateDashboard, 'corporate', 'corpDeposit']])('%s 使用引线与业务详情，排行切换同步地图', async (Dashboard, business, key) => {
    const wrapper = shallowMount(Dashboard, { props: { model: { institutions, rankings: [{ orgCode: 'A', name: '西安支行', cityCode: '610100', deposit: 2, average: 1, increase: 0.5 }], citySummaries: { '610100': { kpis: [{ key, value: 125, unit: '万元' }] } } } } });
    const map = wrapper.findComponent({ name: 'PanoramaMap' });
    expect(map.props('labelLayout')).toBe('callout');
    expect(valueOf(map.props('cityDetails')['610100'], 'deposit')).toBe('125万元');
    expect(map.props('metricValues')['610100']).toBe('125万元');
    await wrapper.get(`[data-ranking-metric="${business === 'retail' ? 'average' : 'increase'}"]`).trigger('click');
    expect(map.props('metricValues')['610100']).toBe(business === 'retail' ? '1亿元' : '5,000.00万元');
    wrapper.unmount();
  });
});

it('业务城市悬浮卡展示每家机构指标并允许滚动查看', async () => {
  const details = buildCityMapDetails({ institutions, rankings: [{ orgCode: 'A', cityCode: '610100', deposit: 2 }] }, { business: 'retail' });
  const wrapper = mount(PanoramaMap, { props: { geoJson: provinceGeo, labelLayout: 'callout', cityDetails: details }, global: { stubs: { Teleport: true } } });
  await wrapper.get('button[data-city-code="610100"]').trigger('pointerenter');
  const list = wrapper.get('[data-testid="map-institution-metrics"]');
  expect(list.text()).toContain('西安支行');
  expect(list.text()).toContain('零售存款余额');
  expect(list.text()).toContain('2亿元');
  expect(list.attributes('tabindex')).toBe('0');
  wrapper.unmount();
});

it('业务城市汇总不把机构目录的分行通用金额作为业务金额', () => {
  const detail = buildCityMapDetails({ institutions: [{ ...institutions[0], deposit: 999 }], rankings: [] }, { business: 'retail' })['610100'];
  expect(valueOf(detail, 'deposit')).toBe('暂无数据');
});

it('多机构城市缺少汇总时显示机构数，对公不完整批次不透出排名值', () => {
  const model = { institutions, rankings: [{ orgCode: 'A', cityCode: '610100', deposit: 99 }] };
  const details = buildCityMapDetails({ institutions: [...institutions, { orgCode: 'B', cityCode: '610100' }] }, { business: 'retail' });
  expect(cityMapMetricValues(details, 'deposit')['610100']).toBe('2家机构');
  const wrapper = shallowMount(CorporateDashboard, { props: { model, sourcePresentation: { sourceAvailability: { corpRanking: { status: 'NO_COMPLETE_BATCH' } } } } });
  const detail = wrapper.findComponent({ name: 'PanoramaMap' }).props('cityDetails')['610100'];
  expect(valueOf(detail, 'deposit')).toBe('暂无数据');
  expect(valueOf(detail.institutions[0], 'deposit')).toBe('暂无数据');
  wrapper.unmount();
});
it('保留对公旧城市存款汇总契约，显式跨业务汇总不回退通用字段', () => {
  const model = { citySummaries: { '610100': { kpis: [{ key: 'deposit', value: 9, unit: '万元' }] } } };
  expect(valueOf(buildCityMapDetails(model, { business: 'corporate' })['610100'], 'deposit')).toBe('9万元');
  model.citySummaries['610100'].kpis.push({ key: 'retailDeposit', value: 1 });
  expect(valueOf(buildCityMapDetails(model, { business: 'corporate' })['610100'], 'deposit')).toBe('暂无数据');
});

it('对公混合层级只禁城市数值，保留授权机构原值并说明不汇总', () => {
  const model = { institutions, rankings: [{ orgCode: 'A', cityCode: '610100', deposit: 99, increase: 1 }], citySummaries: { '610100': { kpis: [{ key: 'corpDeposit', value: 999, unit: '亿元' }] } } };
  const wrapper = shallowMount(CorporateDashboard, { props: { model, sourcePresentation: { sourceAvailability: { corpRanking: { status: 'PARTIAL', message: '混合层级测试对照' } } } } });
  const map = wrapper.findComponent({ name: 'PanoramaMap' });
  const detail = map.props('cityDetails')['610100'];
  expect(valueOf(detail, 'deposit')).toBe('暂无数据');
  expect(valueOf(detail.institutions[0], 'deposit')).toBe('99亿元');
  expect(valueOf(detail.institutions[0], 'increase')).toBe('1亿元');
  expect(detail.scopeLabel).toBe('混合层级 · 机构原值，不作汇总');
  expect(map.props('metricValues')).toEqual({});
  wrapper.unmount();
});
it.each([['retail', 'retailRanking'], ['corporate', 'corpRanking']])('%s 机构日期使用本行或排名来源日期，不借用其他 KPI 全局日期', (business, slot) => {
  const model = { institutions, dataDate: '2026-07-22', sourceQualities: { [slot]: { dataDate: '2026-09-19' } }, rankings: [{ orgCode: 'A', cityCode: '610100', deposit: 9 }] };
  let detail = buildCityMapDetails(model, { business })['610100'];
  expect(detail.institutions[0].dataDate).toBe('2026-09-19');
  expect(detail.dataDate).toBe('2026-09-19');
  model.rankings[0].date = '2026-09-18';
  detail = buildCityMapDetails(model, { business })['610100'];
  expect(detail.institutions[0].dataDate).toBe('2026-09-18');
  delete model.rankings[0].date;
  delete model.sourceQualities;
  detail = buildCityMapDetails(model, { business })['610100'];
  expect(detail.institutions[0].dataDate).toBe('');
  expect(detail.dataDate).toBe('');
});
