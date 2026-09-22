// @vitest-environment happy-dom
import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import SeriesTableWidgets from '../widgets/SeriesTableWidgets.vue';

vi.mock('../../panorama/PanoramaTrend.vue', () => ({ default: {
  props: ['rows', 'series', 'title'],
  template: '<div data-testid="configured-trend">{{ title }}|{{ series.map(s => s.label).join(",") }}|{{ rows.length }}</div>'
} }));

describe('SeriesTableWidgets', () => {
  it('渲染配置趋势、列顺序、零值和缺值', () => {
    const wrapper = mount(SeriesTableWidgets, { props: { components: [
      { componentId: 'trend', componentType: 'TREND', title: '趋势', state: 'READY', rows: [{ date: '2026-01', value: 1 }], series: [{ key: 'value', label: '余额' }], issues: [] },
      { componentId: 'table', componentType: 'DETAIL_TABLE', title: '明细', state: 'READY', issues: [],
        columns: [{ columnKey: 'a', label: '贷款' }, { columnKey: 'b', label: '存款' }],
        rows: [{ key: '1', cells: [{ key: 'a', text: '0元' }, { key: 'b', text: '—' }] }] }
    ] } });
    expect(wrapper.find('[data-testid="configured-trend"]').text()).toContain('趋势|余额|1');
    expect(wrapper.findAll('th').map(node => node.text())).toEqual(['贷款', '存款']);
    expect(wrapper.findAll('td').map(node => node.text())).toEqual(['0元', '—']);
  });

  it('将恶意字符串作为纯文本渲染，不创建HTML节点', () => {
    const unsafe = '<script>alert(1)</script>';
    const wrapper = mount(SeriesTableWidgets, { props: { components: [{
      componentId: 'table', componentType: 'DETAIL_TABLE', title: '明细', state: 'READY', issues: [],
      columns: [{ columnKey: 'orgName', label: '机构名称' }],
      rows: [{ key: '001', cells: [{ key: 'orgName', text: unsafe }] }]
    }] } });

    expect(wrapper.find('td').text()).toBe(unsafe);
    expect(wrapper.find('script').exists()).toBe(false);
  });
});
