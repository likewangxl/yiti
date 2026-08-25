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

  it('陕西地图按十个地市稳定分色，并用静默偏移图层形成拟 3D 厚度', () => {
    const wrapper = mount(MapCenter, { props: { mapPoints: [] } });
    const option = wrapper.findComponent({ name: 'VChart' }).props('option');
    const regions = option.geo.regions;
    expect(regions.map(region => region.name)).toEqual([
      '西安市', '铜川市', '宝鸡市', '咸阳市', '渭南市',
      '延安市', '汉中市', '榆林市', '安康市', '商洛市'
    ]);
    expect(new Set(regions.map(region => region.itemStyle.areaColor)).size).toBe(10);
    expect(regions.every(region => region.emphasis?.itemStyle?.areaColor)).toBe(true);

    const depthLayers = option.series.filter(series => series.type === 'map' && series.silent === true);
    expect(depthLayers).toHaveLength(3);
    expect(depthLayers.every(series => series.map === 'shaanxi')).toBe(true);
    expect(depthLayers.map(series => series.layoutCenter[1])).toEqual(['56%', '55%', '54%']);
  });

  it('无真实指标时明确启用稳定模拟数据，不把演示值伪装成数据库数据', () => {
    const wrapper = mount(MapCenter, { props: { mapPoints: [] } });
    expect(wrapper.find('.mp-demo-badge').text()).toContain('模拟演示');
    const option = wrapper.findComponent({ name: 'VChart' }).props('option');
    expect(option.geo.label.formatter({ name: '西安市' })).toMatch(/\d+\.\d%/);
    expect(option.geo.label.formatter({ name: '西安市' }))
      .toBe(option.geo.label.formatter({ name: '西安市' }));
    expect(option.tooltip.formatter({ name: '西安市' })).toContain('模拟指标');
  });

  it('点击区县后列出数据库机构画像位置，并明确标识缺失指标使用模拟值', async () => {
    const wrapper = mount(MapCenter, { props: {
      mode: 'design',
      mapConfig: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', satelliteNodes: [] },
      profiles: [
        { orgCode: 'XA-001', orgName: '未央支行', cityCode: '610100', operatingLevel: 'PRIMARY',
          lng: 108.917593, lat: 34.342883, coordSys: 'GCJ02', status: 'ACTIVE' },
        { orgCode: 'BJ-001', orgName: '宝鸡支行', cityCode: '610300', operatingLevel: 'PRIMARY',
          lng: 107.238, lat: 34.362, coordSys: 'GCJ02', status: 'ACTIVE' }
      ]
    } });
    wrapper.findComponent({ name: 'VChart' }).vm.$emit('click', { componentType: 'geo', name: '未央区' });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.mp-region-detail').text()).toContain('未央区支行指标明细');
    expect(wrapper.find('.mp-region-detail').text()).toContain('未央支行');
    expect(wrapper.find('.mp-region-detail').text()).toContain('108.917593, 34.342883');
    expect(wrapper.find('.mp-region-detail').text()).toContain('模拟指标');
    expect(wrapper.find('.mp-region-detail').text()).not.toContain('宝鸡支行');
  });

  it('点击区域但数据库没有带坐标机构时展示真实空态', async () => {
    const wrapper = mount(MapCenter, { props: { mapPoints: [], profiles: [] } });
    wrapper.findComponent({ name: 'VChart' }).vm.$emit('click', { componentType: 'geo', name: '西安市' });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.mp-region-empty').text()).toContain('数据库中暂无带坐标的支行画像');
  });

  it('地图指标模式默认按综合达成率着色，标签直显达成率且无数据保持中性', () => {
    const wrapper = mount(MapCenter, { props: {
      mapPoints: [
        { orgCode: '128', orgName: '宝鸡分行', lng: 107.237974, lat: 34.361979 },
        { orgCode: '169', orgName: '咸阳分行', lng: 108.708991, lat: 34.329605 }
      ],
      regionMetrics: [
        { orgCode: '128', orgName: '宝鸡分行', dataDate: '2026-08-23', metricValues: {
          KPI_ACHIEVE_RATE_ORG: 86.2, DEP_ACHIEVE_RATE_ORG: 92, M_0265: 86200
        } },
        { orgCode: '169', orgName: '咸阳分行', dataDate: '2026-08-23', metricValues: {
          KPI_ACHIEVE_RATE_ORG: 103.4, DEP_ACHIEVE_RATE_ORG: 98, M_0265: 103400
        } }
      ]
    } });
    expect(wrapper.find('.mp-kpi-toolbar').text()).toContain('综合达成');
    const option = wrapper.findComponent({ name: 'VChart' }).props('option');
    const baoji = option.geo.regions.find(region => region.name === '宝鸡市');
    const xian = option.geo.regions.find(region => region.name === '西安市');
    expect(baoji.itemStyle.areaColor).toBe('#b9852f');
    expect(xian.itemStyle.areaColor).toBe('#27364f');
    expect(option.geo.label.formatter({ name: '宝鸡市' })).toContain('86.2%');
    expect(option.geo.label.formatter({ name: '西安市' })).toContain('--');
  });

  it('切换存款达成后同步更新地图颜色、排名与包含目标缺口的悬浮卡', async () => {
    const wrapper = mount(MapCenter, { props: {
      mapPoints: [{ orgCode: '128', orgName: '宝鸡分行', lng: 107.237974, lat: 34.361979 }],
      regionMetrics: [{ orgCode: '128', orgName: '宝鸡分行', dataDate: '2026-08-23', metricValues: {
        KPI_ACHIEVE_RATE_ORG: 86.2, DEP_ACHIEVE_RATE_ORG: 92, M_0265: 92000,
        DEP_BAL_YOY_RATE: 6.3, DEP_BAL_MOM_RATE: -1.2
      } }]
    } });
    await wrapper.get('button[data-metric="DEP_ACHIEVE_RATE_ORG"]').trigger('click');
    const option = wrapper.findComponent({ name: 'VChart' }).props('option');
    expect(option.geo.regions.find(region => region.name === '宝鸡市').itemStyle.areaColor).toBe('#236b8e');
    const tooltip = option.tooltip.formatter({ name: '宝鸡市' });
    expect(tooltip).toContain('存款达成率');
    expect(tooltip).toContain('92.0%');
    expect(tooltip).toContain('目标值');
    expect(tooltip).toContain('缺口');
    expect(tooltip).toContain('同比 +6.3%');
    expect(tooltip).toContain('环比 -1.2%');
    expect(tooltip).toContain('第 1');
  });

  it('西安复合地图按六个行政区稳定分色，并保留悬停强调色', () => {
    const wrapper = mount(MapCenter, { props: {
      mode: 'design',
      mapConfig: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', satelliteNodes: [] }
    } });
    const regions = wrapper.findComponent({ name: 'VChart' }).props('option').geo.regions;
    expect(regions.map(region => region.name)).toEqual([
      '未央区', '莲湖区', '新城区', '碑林区', '雁塔区', '长安区'
    ]);
    expect(new Set(regions.map(region => region.itemStyle.areaColor)).size).toBe(6);
    expect(regions.every(region => region.emphasis?.itemStyle?.areaColor)).toBe(true);
  });

  it('选择陕西地市后展示对应区县地图、区县名称和稳定分色', () => {
    const wrapper = mount(MapCenter, { props: {
      mode: 'design',
      mapConfig: { schemaVersion: 1, mode: 'SHAANXI_LEGACY', regionCode: '610300' }
    } });
    expect(wrapper.text()).toContain('宝鸡市区县经营地图');
    const option = wrapper.findComponent({ name: 'VChart' }).props('option');
    expect(option.geo.map).toBe('shaanxi-city-610300');
    expect(option.geo.regions.map(region => region.name)).toEqual([
      '渭滨区', '金台区', '陈仓区', '凤翔区', '岐山县', '扶风县',
      '眉县', '陇县', '千阳县', '麟游县', '凤县', '太白县'
    ]);
    expect(new Set(option.geo.regions.map(region => region.itemStyle.areaColor)).size).toBe(12);
    expect(option.series.filter(series => series.type === 'map').every(series => series.map === 'shaanxi-city-610300')).toBe(true);
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

  it('本地网点名称和完成率在地图两侧均衡排布，并以引导线连接真实点位', () => {
    const localPoints = Array.from({ length: 12 }, (_, index) => ({
      orgCode: `XA-${String(index + 1).padStart(3, '0')}`,
      orgName: `西安测试支行${index + 1}`,
      lng: 108.84 + (index % 4) * 0.07,
      lat: 34.42 - index * 0.025
    }));
    const wrapper = mount(MapCenter, { props: {
      mapPayload: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', localPoints, satelliteNodes: [] }
    } });
    const callouts = wrapper.findAll('.mp-local-node.mp-callout');
    expect(callouts).toHaveLength(12);
    expect(wrapper.findAll('.mp-leader-line')).toHaveLength(12);
    expect(wrapper.findAll('.mp-callout.side-left')).toHaveLength(6);
    expect(wrapper.findAll('.mp-callout.side-right')).toHaveLength(6);
    expect(wrapper.find('.mp-callout.side-left').attributes('style')).toContain('left: 24%');
    expect(wrapper.find('.mp-callout.side-right').attributes('style')).toContain('right: 24%');
    for (const side of ['left', 'right']) {
      const tops = wrapper.findAll(`.mp-callout.side-${side}`).map(node => node.attributes('style').match(/top:\s*([^;]+)/)?.[1]);
      expect(new Set(tops).size).toBe(6);
    }
    expect(callouts[0].text()).toMatch(/西安测试支行\d+.*%/);
    expect(callouts[0].attributes('aria-label')).toContain('完成率');
    expect(wrapper.find('.mp-leader-line').attributes('style')).toMatch(/left:.*top:.*width:.*transform:/);
    const leaderWidths = wrapper.findAll('.mp-leader-line').map(line => Number(line.attributes('style').match(/width:\s*([\d.]+)%/)?.[1]));
    expect(Math.max(...leaderWidths)).toBeLessThanOrEqual(26);
    const scatter = wrapper.findComponent({ name: 'VChart' }).props('option').series
      .find(series => series.type === 'effectScatter');
    expect(scatter.label.show).toBe(false);
    expect(wrapper.findComponent({ name: 'VChart' }).props('option').geo.layoutSize).toBe('46%');
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
