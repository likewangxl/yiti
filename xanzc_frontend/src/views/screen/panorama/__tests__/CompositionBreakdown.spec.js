// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';

import CompositionBreakdown from '../CompositionBreakdown.vue';

function mountBreakdown(items) {
  return mount(CompositionBreakdown, {
    props: { items },
    attachTo: document.body
  });
}

describe('CompositionBreakdown', () => {
  it('金额构成以真实合计、100%基准带和两列明细呈现，金额各只显示一次', () => {
    const wrapper = mountBreakdown([
      { name: '对公业务', value: 714.26, unit: '亿元' },
      { name: '零售业务', value: 572.16, unit: '亿元' }
    ]);

    expect(wrapper.get('[data-testid="composition-total"]').text()).toContain('1,286.42');
    expect(wrapper.get('[data-testid="composition-total-unit"]').text()).toBe('亿元');
    expect(wrapper.get('[data-testid="composition-track"]').attributes('data-state')).toBe('valid');
    expect(wrapper.get('[data-testid="composition-scale-50"]').text()).toBe('50%');
    expect(wrapper.get('[data-testid="composition-scale-100"]').text()).toBe('100%');

    const segments = wrapper.findAll('[data-testid="composition-segment"]');
    expect(segments).toHaveLength(2);
    const widthOf = segment => Number.parseFloat(segment.attributes('style').match(/width:\s*([\d.]+)%/)[1]);
    expect(widthOf(segments[0])).toBeCloseTo(55.523, 2);
    expect(widthOf(segments[1])).toBeCloseTo(44.477, 2);

    const details = wrapper.findAll('[data-testid="composition-detail"]');
    expect(details).toHaveLength(2);
    expect(wrapper.text()).toContain('55.5%');
    expect(wrapper.text()).toContain('44.5%');
    expect(wrapper.text().match(/714\.26/g)).toHaveLength(1);
    expect(wrapper.text().match(/572\.16/g)).toHaveLength(1);
    expect(wrapper.text()).not.toContain('全行总额');
  });

  it('百分比输入仅在合计约为100时作为100%基准，不擅自归一化', () => {
    const valid = mountBreakdown([
      { name: '对公业务', value: 55.5, unit: '%' },
      { name: '零售业务', value: 44.5, unit: '%' }
    ]);
    expect(valid.get('[data-testid="composition-total"]').text()).toContain('100');
    expect(valid.get('[data-testid="composition-total-unit"]').text()).toBe('%');
    expect(valid.get('[data-testid="composition-track"]').attributes('data-state')).toBe('valid');
    expect(valid.findAll('[data-testid="composition-segment"]')[0].attributes('style')).toContain('55.5%');
    expect(valid.text().match(/55\.5/g)).toHaveLength(1);
    expect(valid.text().match(/44\.5/g)).toHaveLength(1);

    const unverified = mountBreakdown([
      { name: '对公业务', value: 60, unit: '%' },
      { name: '零售业务', value: 50, unit: '%' }
    ]);
    expect(unverified.get('[data-testid="composition-track"]').attributes('data-state')).toBe('unavailable');
    expect(unverified.findAll('[data-testid="composition-segment"]')).toHaveLength(0);
    expect(unverified.text()).toContain('60%');
    expect(unverified.text()).toContain('50%');
    expect(unverified.text()).toContain('比例口径待核对');

    const roundingDrift = mountBreakdown([
      { name: '对公业务', value: 60.4, unit: '%' },
      { name: '零售业务', value: 40, unit: '%' }
    ]);
    expect(roundingDrift.get('[data-testid="composition-track"]').attributes('data-state')).toBe('unavailable');
  });

  it('混单位、空值或负值时保留原明细且不让剩余项伪造占比', () => {
    const wrapper = mountBreakdown([
      { name: '对公业务', value: 714.26, unit: '亿元' },
      { name: '零售业务', value: null, unit: '亿元' },
      { name: '其他业务', value: -2, unit: '万元' }
    ]);

    expect(wrapper.get('[data-testid="composition-track"]').attributes('data-state')).toBe('unavailable');
    expect(wrapper.findAll('[data-testid="composition-segment"]')).toHaveLength(0);
    expect(wrapper.findAll('[data-testid="composition-detail"]')).toHaveLength(3);
    expect(wrapper.text()).toContain('714.26');
    expect(wrapper.text()).toContain('—');
    expect(wrapper.text()).toContain('-2');
    expect(wrapper.text()).toContain('不能计算占比');
    expect(wrapper.text()).not.toContain('占比 100%');
  });

  it('全零构成仍展示0与真实单位，但不伪造100%比例', () => {
    const wrapper = mountBreakdown([
      { name: '对公业务', value: 0, unit: '亿元' },
      { name: '零售业务', value: 0, unit: '亿元' }
    ]);

    expect(wrapper.get('[data-testid="composition-total"]').text()).toContain('0');
    expect(wrapper.get('[data-testid="composition-track"]').attributes('data-state')).toBe('unavailable');
    expect(wrapper.findAll('[data-testid="composition-segment"]')).toHaveLength(0);
    expect(wrapper.findAll('[data-testid="composition-detail"]')[0].text()).toContain('0');
    expect(wrapper.text()).toContain('无法计算占比');
    expect(wrapper.text()).not.toContain('100%');
  });

  it('极小构成项保留原始数值且份额宽度不设置最小值', () => {
    const wrapper = mountBreakdown([
      { name: '微量业务', value: 0.001, unit: '亿元' },
      { name: '主要业务', value: 100, unit: '亿元' }
    ]);

    expect(wrapper.findAll('[data-testid="composition-detail"]')[0].text()).toContain('0.001');
    const width = Number.parseFloat(wrapper.findAll('[data-testid="composition-segment"]')[0].attributes('style').match(/width:\s*([\d.]+)%/)[1]);
    expect(width).toBeGreaterThan(0);
    expect(width).toBeLessThan(0.01);

    const smaller = mountBreakdown([
      { name: '极小业务', value: 0.0000001, unit: '亿元' },
      { name: '主要业务', value: 1, unit: '亿元' }
    ]);
    expect(smaller.findAll('[data-testid="composition-detail"]')[0].text()).toContain('<0.000001');
    expect(smaller.findAll('[data-testid="composition-detail"]')[0].text()).not.toContain('0.000000');
  });

  it('数组、对象和溢出求和不被Number隐式转成可用比例', () => {
    const wrapper = mountBreakdown([
      { name: '数组值', value: [], unit: '亿元' },
      { name: '对象值', value: {}, unit: '亿元' }
    ]);
    expect(wrapper.get('[data-testid="composition-track"]').attributes('data-state')).toBe('unavailable');
    expect(wrapper.text()).toContain('不能计算占比');

    const overflow = mountBreakdown([
      { name: '超大项一', value: Number.MAX_VALUE, unit: '亿元' },
      { name: '超大项二', value: Number.MAX_VALUE, unit: '亿元' }
    ]);
    expect(overflow.get('[data-testid="composition-track"]').attributes('data-state')).toBe('unavailable');
    expect(overflow.text()).toContain('构成合计不是有限数值');
  });

  it('比例段可指向和键盘聚焦，详情由原生title与aria标签提供', () => {
    const wrapper = mountBreakdown([
      { name: '对公业务', value: 714.26, unit: '亿元' },
      { name: '零售业务', value: 572.16, unit: '亿元' }
    ]);

    wrapper.findAll('[data-testid="composition-segment"]').forEach(segment => {
      expect(segment.attributes('tabindex')).toBe('0');
      expect(segment.attributes('title')).toContain('亿元');
      expect(segment.attributes('aria-label')).toContain('占比');
    });
  });
});
