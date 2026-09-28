// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import InstitutionRankingWidget from '../InstitutionRankingWidget.vue';

const model = {
  activeMetricKey: 'deposit',
  metrics: [{
    metricKey: 'deposit',
    label: '存款余额',
    unit: '亿元',
    receivedCount: 3,
    expectedCount: 4,
    rankable: [
      { orgCode: 'A', name: '甲机构', rank: 1, value: 100, state: 'READY' },
      { orgCode: 'B', name: '乙机构', rank: 2, value: 90, state: 'READY' },
      { orgCode: 'C', name: '丙机构', rank: 3, value: 80, state: 'READY' }
    ],
    missing: [
      { orgCode: 'M', name: '未参与机构', rank: null, value: null, state: 'MISSING' },
      { orgCode: '', name: '无编码机构', rank: null, value: null, state: 'MISSING' }
    ]
  }]
};

const mounted = [];
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

function mountWidget() {
  const wrapper = mount(InstitutionRankingWidget, {
    props: { model, title: '机构排名', paginate: true, pageSize: 2, pageInterval: 60000, metricCarousel: false },
    attachTo: document.body
  });
  mounted.push(wrapper);
  return wrapper;
}

describe('InstitutionRankingWidget 机构名称导航', () => {
  it('名称使用原生按钮，READY 与 MISSING 有 code 行点击只发一次 branch-select，空 code 拒绝', async () => {
    const wrapper = mountWidget();
    const first = wrapper.get('[data-testid="institution-ranking-name"]');
    expect(first.element.tagName.toLowerCase()).toBe('button');
    expect(first.attributes('type')).toBe('button');
    expect(first.attributes('aria-label')).toContain('甲机构');
    expect(wrapper.get('[data-testid="institution-ranking-row"]').attributes('data-state')).toBe('READY');

    await first.trigger('click');
    expect(wrapper.emitted('branch-select')).toEqual([['A']]);
    expect(wrapper.get('[data-testid="institution-ranking-page"]').text()).toContain('第 1/3 页');

    await wrapper.get('[data-action="ranking-page-next"]').trigger('click');
    const secondPageName = wrapper.get('[data-testid="institution-ranking-name"]');
    expect(secondPageName.attributes('data-org-code')).toBe('C');
    await secondPageName.trigger('click');
    expect(wrapper.emitted('branch-select')).toContainEqual(['C']);

    const missingName = wrapper.findAll('[data-testid="institution-ranking-name"]')[1];
    expect(missingName.attributes('data-org-code')).toBe('M');
    expect(wrapper.get('[data-testid="institution-ranking-row"][data-org-code="M"]').attributes('data-state')).toBe('MISSING');
    await missingName.trigger('click');
    expect(wrapper.emitted('branch-select')).toContainEqual(['M']);

    await wrapper.get('[data-action="ranking-page-next"]').trigger('click');
    const noCode = wrapper.get('[data-testid="institution-ranking-row"] td:nth-child(2) span');
    expect(noCode.text()).toBe('无编码机构');
    await noCode.trigger('click');
    expect(wrapper.emitted('branch-select')).toEqual([['A'], ['C'], ['M']]);
  });

  it('分页第二页名称可通过键盘聚焦且名称点击不改变分页状态', async () => {
    const wrapper = mountWidget();
    await wrapper.get('[data-action="ranking-page-next"]').trigger('click');
    const name = wrapper.get('[data-testid="institution-ranking-name"]');
    expect(wrapper.get('[data-testid="institution-ranking-page"]').text()).toContain('第 2/3 页');
    expect(name.attributes('type')).toBe('button');
    name.element.focus();
    expect(document.activeElement).toBe(name.element);
    await name.trigger('click');
    expect(wrapper.get('[data-testid="institution-ranking-page"]').text()).toContain('第 2/3 页');
  });
});
