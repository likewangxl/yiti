// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({ default: { name: 'VChart', props: ['option'], emits: ['click'], template: '<div class="v-chart-stub" />' } }));
const routerPush = vi.hoisted(() => vi.fn());
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }));

import MapCenter from '../MapCenter.vue';
import xianSixDistricts, { XIAN_SIX_DISTRICTS_METADATA } from '@/assets/geo/xian-six-districts';

describe('MapCenter schema v1/v2', () => {
  beforeEach(() => routerPush.mockClear());

  it('运行态复合地图消费外层标题安全区，设计态预览不偏移', () => {
    const config = { schemaVersion: 2, mode: 'XIAN_COMPOSITE', satelliteNodes: [] };
    const runtime = mount(MapCenter, { props: { mapConfig: config, runtimeHeaderInset: 72 } });
    const design = mount(MapCenter, { props: { mode: 'design', mapConfig: config, runtimeHeaderInset: 72 } });
    expect(runtime.find('.mp-composite').attributes('style')).toContain('padding-top: 72px');
    expect(design.find('.mp-composite').attributes('style') || '').not.toContain('padding-top');
  });

  it('陕西 v1 与非 XIAN 拒绝态即使收到安全区也不得产生 padding', () => {
    const legacy = mount(MapCenter, { props: {
      mapConfig: { schemaVersion: 1, mode: 'SHAANXI_LEGACY' }, runtimeHeaderInset: 72
    } });
    const nonXian = mount(MapCenter, { props: {
      mapConfig: { schemaVersion: 2, mode: 'SHAANXI_LEGACY' }, runtimeHeaderInset: 72
    } });
    expect(legacy.find('.mp-block').attributes('style') || '').not.toContain('padding');
    expect(nonXian.find('.mp-block').attributes('style') || '').not.toContain('padding');
  });

  it('六区边界资产只包含约定行政区，并记录可再分发来源、代码、坐标系与裁剪方式', () => {
    expect(xianSixDistricts.type).toBe('FeatureCollection');
    expect(xianSixDistricts.features.map(feature => ({
      name: feature.properties.name,
      adcode: feature.properties.adcode,
      osmRelationId: feature.properties.osmRelationId
    }))).toEqual([
      { name: '未央区', adcode: '610112', osmRelationId: 3226095 },
      { name: '莲湖区', adcode: '610104', osmRelationId: 3226093 },
      { name: '新城区', adcode: '610102', osmRelationId: 3226096 },
      { name: '碑林区', adcode: '610103', osmRelationId: 3226088 },
      { name: '雁塔区', adcode: '610113', osmRelationId: 3226098 },
      { name: '长安区', adcode: '610116', osmRelationId: 3226089 }
    ]);
    expect(XIAN_SIX_DISTRICTS_METADATA).toMatchObject({
      sourceName: 'OpenStreetMap contributors',
      license: 'ODbL 1.0',
      sourceCoordinateSystem: 'WGS84 / EPSG:4326',
      deliveredCoordinateSystem: 'GCJ-02',
      deliveredContentSha256: 'fb3980443aada65db8703c1c29399ba065f91a34959b9072ebf6d373a086e389'
    });
    expect(XIAN_SIX_DISTRICTS_METADATA.cropMethod).toContain('仅保留');
    expect(XIAN_SIX_DISTRICTS_METADATA.districts).toHaveLength(6);
  });

  it('v2 显示西安六区地图、四个二级分行语义和不可隐藏声明', () => {
    const wrapper = mount(MapCenter, {
      props: {
        mapConfig: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', satelliteNodes: [
          { orgCode: '128', orgName: '宝鸡分行', anchor: 'LEFT' },
          { orgCode: '191', orgName: '渭南分行', anchor: 'RIGHT' },
          { orgCode: '169', orgName: '咸阳分行', anchor: 'TOP' },
          { orgCode: '129', orgName: '榆林分行', anchor: 'FAR_TOP' }
        ] },
        profiles: [
          { orgCode: 'X1', orgName: '本地机构', cityCode: '610100', operatingLevel: 'PRIMARY', lng: 108.9, lat: 34.2, coordSys: 'GCJ02', status: 'ACTIVE' },
          { orgCode: '128', orgName: '宝鸡分行', operatingLevel: 'PRIMARY', status: 'ACTIVE' },
          { orgCode: '191', orgName: '渭南分行', operatingLevel: 'PRIMARY', status: 'ACTIVE' },
          { orgCode: '169', orgName: '咸阳分行', operatingLevel: 'PRIMARY', status: 'ACTIVE' },
          { orgCode: '129', orgName: '榆林分行', operatingLevel: 'PRIMARY', status: 'ACTIVE' }
        ]
      }
    });
    expect(wrapper.text()).toContain('西安六区经营地图');
    expect(wrapper.text()).toContain('二级分行示意位置，非地理比例');
    expect(wrapper.text()).toContain('边界数据：© OpenStreetMap contributors（ODbL）');
    expect(wrapper.find('.mp-attribution a').attributes()).toMatchObject({
      href: 'https://www.openstreetmap.org/copyright',
      target: '_blank',
      rel: 'noopener noreferrer'
    });
    expect(wrapper.findAll('[data-anchor]').map(x => x.attributes('data-anchor'))).toEqual(['LEFT', 'RIGHT', 'TOP', 'FAR_TOP']);
    expect(wrapper.findAll('button[tabindex="0"]').length).toBe(5); // 4 示意节点 + 1 个本地真实点位
    expect(wrapper.findComponent({ name: 'VChart' }).props('option').geo).toMatchObject({
      map: 'xian-six-districts',
      label: { show: true }
    });
  });

  it('v1 仍使用陕西地图，不要求画像或新机构组', () => {
    const wrapper = mount(MapCenter, { props: { mapPoints: [{ orgCode: 'O1', orgName: '旧支行', lng: 108, lat: 34 }] } });
    expect(wrapper.text()).not.toContain('组织分布示意，非地理比例');
    expect(wrapper.find('.mp-chart').exists()).toBe(true);
    expect(wrapper.findComponent({ name: 'VChart' }).props('option').geo.map).toBe('shaanxi');
  });

  it.each([
    ['mode-only', { mode: 'XIAN_COMPOSITE' }],
    ['schema-only', { schemaVersion: 2 }],
    ['v1-xian-conflict', { schemaVersion: 1, mode: 'XIAN_COMPOSITE' }],
    ['string-schema', { schemaVersion: '2', mode: 'XIAN_COMPOSITE' }],
    ['illegal-mode', { schemaVersion: 2, mode: 'SHAANXI_LEGACY' }],
    ['missing-schema', { schemaVersion: null, mode: 'XIAN_COMPOSITE' }],
    ['missing-all-contract-fields', {}]
  ])('运行包 %s 缺少精确 schema/mode 契约时 Fail Close', (_name, mapPayload) => {
    const wrapper = mount(MapCenter, { props: { mapPayload } });
    expect(wrapper.find('[role="alert"]').text()).toContain('已拒绝渲染');
    expect(wrapper.find('.mp-chart').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('西安六区经营地图');
  });

  it('运行态以授权 mapPayload 为准，不能由伴随组件配置掩盖其缺失 schema', () => {
    const wrapper = mount(MapCenter, { props: {
      mapConfig: { schemaVersion: 2, mode: 'XIAN_COMPOSITE' },
      mapPayload: { mode: 'XIAN_COMPOSITE', localPoints: [], satelliteNodes: [] }
    } });
    expect(wrapper.find('[role="alert"]').text()).toContain('已拒绝渲染');
    expect(wrapper.find('.mp-chart').exists()).toBe(false);
  });

  it('消费后端已授权的 mapPackage(localPoints/satelliteNodes) 时不再要求画像 selector 字段', () => {
    const wrapper = mount(MapCenter, { props: {
      mapPayload: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', baseRegion: 'XIAN_OUTLINE',
        disclaimer: '组织分布示意，非地理比例',
        localPoints: [{ orgCode: 'X1', orgName: '本地机构', lng: 108.9, lat: 34.2 }],
        satelliteNodes: [
          { orgCode: '128', orgName: '伪造名称', anchor: 'RIGHT', targetScreenCode: 'OTHER' },
          { orgCode: 'NOT_AUTHORIZED', orgName: '越权节点', anchor: 'LEFT' }
        ] }
    } });
    expect(wrapper.findAll('[data-anchor]')).toHaveLength(1);
    expect(wrapper.findAll('.mp-local-node')).toHaveLength(1);
    const baoji = wrapper.find('[data-anchor="LEFT"]');
    expect(baoji.text()).toContain('宝鸡分行');
    expect(baoji.attributes('aria-label')).toContain('示意位置，非地理比例');
  });

  it('后端异常锚点不会落到左上角，前端只渲染固定四种示意布局', () => {
    const wrapper = mount(MapCenter, { props: {
      mapPayload: { schemaVersion: 2, mode: 'XIAN_COMPOSITE',
        localPoints: [], satelliteNodes: [{ orgCode: 'BAD', orgName: '异常节点', anchor: 'UNKNOWN' }] }
    } });
    expect(wrapper.findAll('[data-anchor]')).toHaveLength(0);
  });

  it('未知地图 schema 显示拒绝态，不退回陕西 v1 地图', () => {
    const wrapper = mount(MapCenter, { props: { mapConfig: { schemaVersion: 99, mode: 'FUTURE_MAP' } } });
    expect(wrapper.find('[role="alert"]').text()).toContain('版本不受支持');
    expect(wrapper.find('.mp-chart').exists()).toBe(false);
  });

  it('设计态配置缺口忽略已停用画像，不把停用机构误报为缺坐标', () => {
    const wrapper = mount(MapCenter, { props: {
      mode: 'design',
      mapConfig: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', satelliteNodes: [
        { orgCode: '128', anchor: 'LEFT' }, { orgCode: '191', anchor: 'RIGHT' },
        { orgCode: '169', anchor: 'TOP' }, { orgCode: '129', anchor: 'FAR_TOP' }
      ] },
      profiles: [{ orgCode: 'DISABLED', orgName: '停用本地机构', cityCode: '610100', operatingLevel: 'PRIMARY', status: 'DISABLED' }]
    } });
    expect(wrapper.find('.mp-config-gap').exists()).toBe(false);
  });

  it('设计态读取设计器提供的机构画像，渲染真实本地节点而不是恒为空图', () => {
    const satelliteNodes = [
      { orgCode: '128', anchor: 'LEFT' }, { orgCode: '191', anchor: 'RIGHT' },
      { orgCode: '169', anchor: 'TOP' }, { orgCode: '129', anchor: 'FAR_TOP' }
    ];
    const profiles = [
      { orgCode: 'X1', orgName: '西安机构', cityCode: '610100', operatingLevel: 'PRIMARY',
        lng: 108.9, lat: 34.2, coordSys: 'GCJ02', status: 'ACTIVE' },
      ...['128', '191', '169', '129'].map(orgCode => ({
        orgCode, orgName: orgCode, operatingLevel: 'PRIMARY', status: 'ACTIVE'
      }))
    ];
    const wrapper = mount(MapCenter, {
      props: { mode: 'design', mapConfig: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', satelliteNodes } },
      global: { provide: { screenProfiles: profiles } }
    });
    expect(wrapper.findAll('.mp-local-node')).toHaveLength(1);
    expect(wrapper.find('.mp-config-gap').exists()).toBe(false);
  });

  it('设计态只预览地图，所有本地和二级分行节点均不可聚焦或钻取', async () => {
    const wrapper = mount(MapCenter, { props: {
      mode: 'design',
      mapConfig: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', satelliteNodes: [{ orgCode: '128', anchor: 'LEFT' }] },
      profiles: [
        { orgCode: 'XA-001', orgName: '西安本地机构', cityCode: '610100', operatingLevel: 'PRIMARY', lng: 108.95, lat: 34.25, coordSys: 'GCJ02', status: 'ACTIVE' },
        { orgCode: '128', orgName: '宝鸡分行', operatingLevel: 'PRIMARY', status: 'ACTIVE' }
      ]
    } });
    const nodes = wrapper.findAll('.mp-local-node, .mp-satellite-node');
    expect(nodes).toHaveLength(2);
    for (const node of nodes) {
      expect(node.attributes('tabindex')).toBe('-1');
      expect(node.attributes('aria-disabled')).toBe('true');
      expect(node.attributes('disabled')).toBeDefined();
      await node.trigger('click');
      await node.trigger('keydown.enter');
      await node.trigger('keydown.space');
    }
    expect(routerPush).not.toHaveBeenCalled();
  });

  it('四个二级分行均可键盘导航至机构详情屏，且运行态绝不跳转红色引擎', async () => {
    const wrapper = mount(MapCenter, { props: {
      mapPayload: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', localPoints: [],
        satelliteNodes: [
          { orgCode: '128', anchor: 'RIGHT', targetScreenCode: 'OTHER' },
          { orgCode: '191', anchor: 'LEFT' },
          { orgCode: '169', anchor: 'FAR_TOP' },
          { orgCode: '129', anchor: 'TOP' }
        ] }
    } });
    for (const node of wrapper.findAll('[data-anchor]')) await node.trigger('keydown.enter');
    expect(routerPush.mock.calls.map(([target]) => target)).toEqual([
      { path: '/screen/SCR_BRANCH', query: { orgCode: '128' } },
      { path: '/screen/SCR_BRANCH', query: { orgCode: '191' } },
      { path: '/screen/SCR_BRANCH', query: { orgCode: '169' } },
      { path: '/screen/SCR_BRANCH', query: { orgCode: '129' } }
    ]);
    expect(routerPush.mock.calls.every(([target]) => !target.path.startsWith('/redengine'))).toBe(true);
  });

  it('二级分行的空格键与鼠标点击同样进入机构详情屏', async () => {
    const wrapper = mount(MapCenter, { props: {
      mapPayload: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', localPoints: [],
        satelliteNodes: [{ orgCode: '128', anchor: 'LEFT' }] }
    } });
    const baoji = wrapper.find('[data-anchor="LEFT"]');
    await baoji.trigger('keydown.space');
    await baoji.trigger('click');
    expect(routerPush).toHaveBeenNthCalledWith(1, { path: '/screen/SCR_BRANCH', query: { orgCode: '128' } });
    expect(routerPush).toHaveBeenNthCalledWith(2, { path: '/screen/SCR_BRANCH', query: { orgCode: '128' } });
  });

  it('已授权的西安本地真实机构仍沿用既有详情屏点击行为', async () => {
    const wrapper = mount(MapCenter, { props: {
      mapPayload: { schemaVersion: 2, mode: 'XIAN_COMPOSITE',
        localPoints: [{ orgCode: 'XA-001', orgName: '西安本地机构', lng: 108.95, lat: 34.25 }],
        satelliteNodes: [] }
    } });
    await wrapper.find('.mp-local-node').trigger('click');
    expect(routerPush).toHaveBeenCalledWith({ path: '/screen/SCR_BRANCH', query: { orgCode: 'XA-001' } });
  });

  it('FAR_TOP 固定映射榆林，使用容器内 top 定位并保持 button 键盘入口', () => {
    const wrapper = mount(MapCenter, { props: {
      mapPayload: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', localPoints: [],
        satelliteNodes: [{ orgCode: '129', orgName: '榆林分行', anchor: 'FAR_TOP' }] }
    } });
    const yulin = wrapper.find('[data-anchor="FAR_TOP"]');
    expect(yulin.exists()).toBe(true);
    expect(yulin.attributes('style')).toContain('top: 8px');
    expect(yulin.element.tagName).toBe('BUTTON');
    expect(yulin.attributes('tabindex')).toBe('0');
  });
});
