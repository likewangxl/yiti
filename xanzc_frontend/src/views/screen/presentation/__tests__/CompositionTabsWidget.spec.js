// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import CompositionTabsWidget from '../widgets/CompositionTabsWidget.vue';

const readyRing = (ringKey, label, corporateShare, retailShare) => ({
  ringKey,
  tabKey: ringKey,
  label,
  corporate: { value: corporateShare, text: String(corporateShare), unit: '亿元', share: corporateShare, shareText: `${corporateShare}%` },
  retail: { value: retailShare, text: String(retailShare), unit: '亿元', share: retailShare, shareText: `${retailShare}%` },
  total: { value: 100, text: '100', unit: '亿元' },
  other: null,
  gap: null,
  state: 'READY'
});

describe('CompositionTabsWidget', () => {
  afterEach(() => { vi.restoreAllMocks(); });

  it('以存款、贷款、收入三枚圆环呈现公司/零售构成，不显示页签或轮播控制', async () => {
    const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');
    const wrapper = mount(CompositionTabsWidget, { props: { model: {
      title: '业务结构',
      rotationEnabled: true,
      rings: [
        readyRing('deposit', '存款', 40, 60),
        { ringKey: 'loan', label: '贷款', tabKey: 'loan', corporate: { value: null, text: '—', unit: '亿元', share: null }, retail: { value: 3, text: '3', unit: '亿元', share: null }, total: { value: null, text: '—', unit: '亿元' }, other: null, gap: null, state: 'PENDING' },
        readyRing('income', '收入', 30, 70)
      ]
    } } });

    expect(wrapper.find('[data-testid="composition-ring-deposit"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="composition-ring-loan"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="composition-ring-income"]').exists()).toBe(true);
    expect(wrapper.findAll('[role="tablist"]').length).toBe(0);
    expect(wrapper.find('[data-testid="composition-tabs-pause"]').exists()).toBe(false);
    expect(setIntervalSpy).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="composition-ring-deposit"]').text()).toContain('40%');
    expect(wrapper.find('[data-testid="composition-ring-loan"]').text()).toContain('待接入');
    expect(wrapper.find('[data-testid="composition-ring-loan-visual"]').attributes('style')).toBeUndefined();
    const income = wrapper.find('[data-testid="composition-ring-income"]');
    expect(income.text()).toContain('核定总量');

    await wrapper.find('[data-testid="business-line-deposit-corp"]').trigger('click');
    await wrapper.find('[data-testid="business-line-deposit-retail"]').trigger('click');
    expect(wrapper.emitted('business-line-select')).toEqual([
      [{ businessLine: 'CORP', tabKey: 'deposit' }],
      [{ businessLine: 'RETAIL', tabKey: 'deposit' }]
    ]);
  });

  it('ringKeys 只保留声明的已知圆环，compact 开启紧凑横向卡且不为未知项补零', () => {
    const wrapper = mount(CompositionTabsWidget, { props: {
      compact: true,
      ringKeys: ['loan', 'unknown', 'deposit'],
      model: { rings: [readyRing('deposit', '存款', 40, 60), readyRing('loan', '贷款', 30, 70)] }
    } });

    expect(wrapper.get('[data-testid="composition-tabs-root"]').classes()).toContain('composition-tabs-widget--compact');
    expect(wrapper.get('[data-testid="composition-tabs-root"]').attributes('data-compact')).toBe('true');
    expect(wrapper.findAll('[data-testid^="composition-ring-"]').filter(node => !node.attributes('data-testid').endsWith('-visual'))
      .map(node => node.attributes('data-testid'))).toEqual(['composition-ring-deposit', 'composition-ring-loan']);
    expect(wrapper.find('[data-testid="composition-ring-unknown"]').exists()).toBe(false);
  });

  it('未传 ringKeys 时保持默认三枚圆环兼容', () => {
    const wrapper = mount(CompositionTabsWidget, { props: { model: { rings: [readyRing('deposit', '存款', 40, 60)] } } });
    expect(wrapper.findAll('.composition-ring-card')).toHaveLength(3);
    expect(wrapper.get('[data-testid="composition-ring-loan"]').attributes('data-state')).toBe('PENDING');
    expect(wrapper.get('[data-testid="composition-ring-income"]').attributes('data-state')).toBe('PENDING');
  });

  it('showTotalCaption=false 时只隐藏圆环中心说明，数字居中且缺失状态仍在外部保留', () => {
    const wrapper = mount(CompositionTabsWidget, { props: {
      showTotalCaption: false,
      ringKeys: ['income'],
      model: { rings: [{
        ringKey: 'income', tabKey: 'income', label: '收入', state: 'MISSING_SIDE',
        corporate: { value: null, text: '—', share: null }, retail: { value: null, text: '—', share: null },
        total: { value: 0.17, text: '0.17', unit: '亿元' }
      }] }
    } });
    expect(wrapper.get('[data-testid="composition-tabs-root"]').classes()).toContain('composition-tabs-widget--no-total-caption');
    const ring = wrapper.get('[data-testid="composition-ring-income"]');
    expect(ring.find('.composition-ring__center strong').text()).toBe('0.17');
    expect(ring.find('.composition-ring__center small').exists()).toBe(false);
    expect(ring.text()).toContain('待接入');
  });

  it('旧业务结构 tab 只兼容映射到存款环，保留公司/零售原值且总量缺失时为空环', () => {
    const wrapper = mount(CompositionTabsWidget, { props: { model: {
      tabs: [{
        tabKey: 'business-structure',
        label: '业务结构',
        corporateField: '测试_直营对公存款',
        retailField: '测试_直营零售存款',
        corporate: { value: 12, text: '12', unit: '元', share: null },
        retail: { value: 8, text: '8', unit: '元', share: null },
        total: { value: null, text: '—', unit: '元' },
        state: 'NO_TOTAL',
        statusMessage: '总量来源待接入'
      }]
    } } });

    const deposit = wrapper.find('[data-testid="composition-ring-deposit"]');
    expect(deposit.attributes('data-state')).toBe('PENDING');
    expect(deposit.text()).toContain('公司 12');
    expect(deposit.text()).toContain('零售 8');
    expect(deposit.text()).toContain('核定总量');
    expect(deposit.text()).toContain('待接入');
    expect(wrapper.find('[data-testid="composition-ring-loan"]').attributes('data-state')).toBe('PENDING');
    expect(wrapper.find('[data-testid="composition-ring-income"]').attributes('data-state')).toBe('PENDING');
    expect(wrapper.text()).not.toContain('测试_直营');
  });

  it('缺口和其他只展示模型提供的真实状态，不凭空补占比', () => {
    const wrapper = mount(CompositionTabsWidget, { props: { model: { rings: [{
      ...readyRing('deposit', '存款', 40, 40),
      other: { value: 20, text: '20', unit: '亿元' },
      otherShareText: '20%'
    }, {
      ...readyRing('loan', '贷款', 60, 60),
      gap: { value: 20, text: '20', unit: '亿元' },
      gapShareText: '20%'
    }] } } });
    expect(wrapper.find('[data-testid="composition-ring-deposit"]').text()).toContain('其他 20 亿元（20%）');
    expect(wrapper.find('[data-testid="composition-ring-loan"]').text()).toContain('缺口 20 亿元（20%）');
    expect(wrapper.find('[data-testid="composition-ring-income"]').attributes('data-state')).toBe('PENDING');
  });

  it('圆环中和图例把金额、占比收敛到可读精度，计算仍使用模型原值', () => {
    const wrapper = mount(CompositionTabsWidget, { props: { model: { rings: [{
      ringKey: 'deposit', tabKey: 'deposit', label: '存款', state: 'READY',
      corporate: { value: 11.94750925, text: '11.94750925', unit: '亿元', share: 52.79611313, shareText: '52.79611313%' },
      retail: { value: 10.68201505, text: '10.68201505', unit: '亿元', share: 47.20388687, shareText: '47.20388687%' },
      total: { value: 22.6295243, text: '22.6295243', unit: '亿元' }
    }] } } });
    const deposit = wrapper.get('[data-testid="composition-ring-deposit"]');
    expect(deposit.get('.composition-ring__center strong').text()).toBe('22.63');
    expect(deposit.text()).toContain('公司 11.95');
    expect(deposit.text()).toContain('52.8%');
    expect(deposit.text()).not.toContain('52.79611313%');
  });

  it('已知收入总量但缺公司/零售分项时正确标注总量，超额缺口时不用截断圆环伪装完整占比', () => {
    const wrapper = mount(CompositionTabsWidget, { props: { model: { rings: [{
      ringKey: 'income', tabKey: 'income', label: '收入', state: 'MISSING_SIDE',
      corporate: { value: null, share: null }, retail: { value: null, share: null },
      total: { value: 0.17, text: '0.17', unit: '亿元' }
    }, {
      ...readyRing('loan', '贷款', 60, 60),
      gap: { value: 20, text: '20', unit: '亿元', share: 20, shareText: '20%' }
    }] } } });
    const income = wrapper.get('[data-testid="composition-ring-income"]');
    expect(income.text()).toContain('0.17');
    expect(income.text()).toContain('核定总量');
    expect(income.text()).not.toContain('核定总量待接入');
    const loan = wrapper.get('[data-testid="composition-ring-loan"]');
    expect(loan.attributes('data-state')).toBe('PENDING');
    expect(loan.text()).toContain('缺口 20 亿元');
    expect(loan.get('[data-testid="composition-ring-loan-visual"]').attributes('style')).toBeUndefined();
  });
});
