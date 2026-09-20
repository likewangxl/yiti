// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PanoramaMap from '../PanoramaMap.vue';
const geoJson = { type: 'FeatureCollection', features: [{ type: 'Feature', properties: { adcode: 610100, name: '西安市' }, geometry: { type: 'Polygon', coordinates: [[[108,34],[109,34],[109,35],[108,35],[108,34]]] } }] };
describe('地图指标联动显示', () => {
  it('按调用方已确认的范围显示指标，切换后更新且无数据不补零', async () => {
    const wrapper = mount(PanoramaMap, { props: { geoJson, metricLabel: '存款余额', metricValues: { '610100': '125万元' } } });
    expect(wrapper.get('[data-testid="map-metric-label"]').text()).toBe('存款余额');
    expect(wrapper.text()).toContain('125万元');
    await wrapper.setProps({ metricLabel: '净增', metricValues: { '610100': '-3万元' } });
    expect(wrapper.text()).toContain('-3万元');
    expect(wrapper.text()).not.toContain('125万元');
    await wrapper.setProps({ metricValues: {} });
    expect(wrapper.find('[data-testid="map-region-metric"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
