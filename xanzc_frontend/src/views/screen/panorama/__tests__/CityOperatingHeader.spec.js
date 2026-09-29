// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';

import CityOperatingHeader from '../CityOperatingHeader.vue';

const sourcePresentation = {
  displayPresentation: {
    displaySchemaVersion: 1,
    template: 'branch-overview-v1',
    display: { components: [] }
  }
};

const presentationLayoutStub = {
  name: 'PresentationLayout',
  props: ['presentation', 'model', 'draftOverview', 'amountUnit', 'dataDate'],
  emits: ['business-line-select'],
  template: `
    <main data-testid="presentation-layout-stub">
      <span data-testid="header-component-count">{{ presentation?.display?.components?.length }}</span>
      <span data-testid="header-deposit">{{ model?.kpis?.find(item => item.key === 'deposit')?.value ?? '—' }}</span>
      <span data-testid="header-data-date">{{ dataDate }}</span>
      <span data-testid="header-draft-overview">{{ draftOverview }}</span>
      <span data-testid="header-amount-unit">{{ amountUnit }}</span>
      <button type="button" data-testid="header-business-line" @click="$emit('business-line-select', { businessLine: 'CORP', tabKey: 'deposit' })">业务线</button>
    </main>
  `
};

describe('CityOperatingHeader', () => {
  it('复用 PresentationLayout 的 draftOverview 五卡布局并传递城市摘要模型', async () => {
    const wrapper = mount(CityOperatingHeader, {
      props: {
        sourcePresentation,
        citySummary: {
          dataDate: '2026-09-20',
          kpis: [{ key: 'deposit', value: 12, unit: '亿元' }]
        },
        amountUnit: 'HUNDRED_MILLION'
      },
      global: { stubs: { PresentationLayout: presentationLayoutStub } }
    });

    expect(wrapper.get('[data-testid="city-operating-header"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="header-draft-overview"]').text()).toBe('true');
    expect(wrapper.get('[data-testid="header-deposit"]').text()).toBe('12');
    expect(wrapper.get('[data-testid="header-data-date"]').text()).toBe('2026-09-20');
    expect(wrapper.get('[data-testid="header-amount-unit"]').text()).toBe('HUNDRED_MILLION');
    expect(Number(wrapper.get('[data-testid="header-component-count"]').text())).toBeGreaterThan(0);

    await wrapper.get('[data-testid="header-business-line"]').trigger('click');
    expect(wrapper.emitted('business-line-select')).toEqual([[{ businessLine: 'CORP', tabKey: 'deposit' }]]);
  });

  it('真实渲染 PresentationLayout 时展示十张顶栏卡和四个完成率环', () => {
    const wrapper = mount(CityOperatingHeader, {
      props: {
        sourcePresentation,
        citySummary: {
          dataDate: '2026-09-20',
          kpis: [
            { key: 'deposit', value: 120, unit: '亿元', dataDate: '2026-09-20' },
            { key: 'loan', value: 80, unit: '亿元', dataDate: '2026-09-20' },
            { key: 'retailDeposit', value: 50, unit: '亿元', dataDate: '2026-09-20' },
            { key: 'retailLoan', value: 30, unit: '亿元', dataDate: '2026-09-20' },
            { key: 'corpDeposit', value: 70, unit: '亿元', dataDate: '2026-09-20' },
            { key: 'corpLoan', value: 50, unit: '亿元', dataDate: '2026-09-20' },
            { key: 'retailDepositRate', value: 90, unit: '%', dataDate: '2026-09-20' },
            { key: 'retailLoanRate', value: 88, unit: '%', dataDate: '2026-09-20' },
            { key: 'corpDepositRate', value: 86, unit: '%', dataDate: '2026-09-20' },
            { key: 'corpLoanRate', value: 84, unit: '%', dataDate: '2026-09-20' },
            { key: 'revenue', value: 12, unit: '亿元', dataDate: '2026-09-20' },
            { key: 'intermediaryIncome', value: 3, unit: '亿元', dataDate: '2026-09-20' }
          ]
        }
      }
    });

    expect(wrapper.findAll('.presentation-metric-widget')).toHaveLength(10);
    expect(wrapper.findAll('[data-testid="completion-ring-gauge"]')).toHaveLength(4);
  });
});
