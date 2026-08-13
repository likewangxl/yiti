// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({ default: { name: 'VChart', props: ['option'], emits: ['click'], template: '<div class="v-chart-stub" />' } }));
const routerPush = vi.hoisted(() => vi.fn());
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }));

import MapCenter from '../MapCenter.vue';

describe('MapCenter schema v1/v2', () => {
  beforeEach(() => routerPush.mockClear());

  it('v2 显示西安复合地图、四锚点语义和不可隐藏声明', () => {
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
    expect(wrapper.text()).toContain('西安复合经营地图');
    expect(wrapper.text()).toContain('组织分布示意，非地理比例');
    expect(wrapper.findAll('[data-anchor]').map(x => x.attributes('data-anchor'))).toEqual(['LEFT', 'RIGHT', 'TOP', 'FAR_TOP']);
    expect(wrapper.findAll('button[tabindex="0"]').length).toBe(5); // 4 示意节点 + 1 个本地真实点位
  });

  it('v1 仍使用陕西地图，不要求画像或新机构组', () => {
    const wrapper = mount(MapCenter, { props: { mapPoints: [{ orgCode: 'O1', orgName: '旧支行', lng: 108, lat: 34 }] } });
    expect(wrapper.text()).not.toContain('组织分布示意，非地理比例');
    expect(wrapper.find('.mp-chart').exists()).toBe(true);
  });

  it('消费后端已授权的 mapPackage(localPoints/satelliteNodes) 时不再要求画像 selector 字段', () => {
    const wrapper = mount(MapCenter, { props: {
      mapPayload: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', baseRegion: 'XIAN_OUTLINE',
        disclaimer: '组织分布示意，非地理比例',
        localPoints: [{ orgCode: 'X1', orgName: '本地机构', lng: 108.9, lat: 34.2 }],
        satelliteNodes: [{ orgCode: '128', orgName: '宝鸡分行', anchor: 'LEFT' }] }
    } });
    expect(wrapper.findAll('[data-anchor]')).toHaveLength(1);
    expect(wrapper.findAll('.mp-local-node')).toHaveLength(1);
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

  it('复合地图锚点可通过键盘 Enter 导航，并携带机构编码', async () => {
    const wrapper = mount(MapCenter, { props: {
      mapPayload: { schemaVersion: 2, mode: 'XIAN_COMPOSITE', localPoints: [],
        satelliteNodes: [{ orgCode: '128', orgName: '宝鸡分行', anchor: 'LEFT', targetScreenCode: 'SCR_BRANCH' }] }
    } });
    await wrapper.find('[data-anchor="LEFT"]').trigger('keydown.enter');
    expect(routerPush).toHaveBeenCalledWith({ path: '/screen/SCR_BRANCH', query: { orgCode: '128' } });
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
