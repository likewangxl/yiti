// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PanoramaMap from '../PanoramaMap.vue';

const geoJson = {
  type: 'FeatureCollection',
  features: [{
    type: 'Feature',
    properties: { adcode: '610100', name: '西安市', center: [108.5, 34.5] },
    geometry: { type: 'Polygon', coordinates: [[[108, 34], [109, 34], [109, 35], [108, 35], [108, 34]]] }
  }]
};

const cityDetails = {
  '610100': {
    institutionCount: 3,
    locatedCount: 1,
    dataDate: '2026-09-28',
    metrics: [{ key: 'deposit', label: '存款余额', value: '暂无数据' }],
    institutions: [
      {
        orgCode: 'LOCATED-1',
        orgName: '已定位机构',
        metrics: [{ key: 'deposit', label: '存款余额', value: '10.00亿元' }],
        dataDate: '2026-09-28'
      },
      {
        orgCode: 'UNLOCATED-1',
        orgName: '待定位机构',
        located: false,
        metrics: [{ key: 'deposit', label: '存款余额', value: '暂无数据' }],
        dataDate: ''
      },
      {
        orgName: '无编码机构',
        metrics: [{ key: 'deposit', label: '存款余额', value: '暂无数据' }],
        dataDate: ''
      }
    ]
  }
};

describe('PanoramaMap 城市浮层机构导航', () => {
  it('机构名称是可访问按钮，点击只发出 branch-select，不触发城市选择', async () => {
    const wrapper = mount(PanoramaMap, {
      props: { geoJson, labelLayout: 'callout', metricValues: {}, cityDetails },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[data-city-code="610100"]').trigger('pointerenter');
    const institution = wrapper.get('button[data-org-code="LOCATED-1"]');
    expect(institution.attributes('type')).toBe('button');
    expect(institution.attributes('aria-label')).toContain('已定位机构');
    expect(institution.text()).toBe('已定位机构');

    await institution.trigger('click');

    expect(wrapper.emitted('branch-select')).toEqual([['LOCATED-1']]);
    expect(wrapper.emitted('region-select')).toBeUndefined();
    wrapper.unmount();
  });

  it('未定位但有 orgCode 的机构仍可点击，缺 orgCode 时不发导航事件', async () => {
    const wrapper = mount(PanoramaMap, {
      props: { geoJson, labelLayout: 'callout', cityDetails },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[data-city-code="610100"]').trigger('pointerenter');
    await wrapper.get('button[data-org-code="UNLOCATED-1"]').trigger('click');
    expect(wrapper.emitted('branch-select')).toEqual([['UNLOCATED-1']]);

    const missingCode = wrapper.get('button[data-org-code=""]');
    expect(missingCode.attributes('aria-label')).toContain('无编码机构');
    await missingCode.trigger('click');
    expect(wrapper.emitted('branch-select')).toEqual([['UNLOCATED-1']]);
    expect(wrapper.emitted('region-select')).toBeUndefined();
    wrapper.unmount();
  });
});
