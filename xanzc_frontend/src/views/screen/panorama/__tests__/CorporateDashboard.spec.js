// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    props: ['geoJson', 'points', 'demo', 'mode', 'selectedRegionCode', 'appearance', 'metricLabel', 'metricValues'],
    emits: ['region-select'],
    template: '<div data-testid="corporate-map" :data-mode="mode" :data-appearance="appearance" :data-metric-label="metricLabel"><button type="button" data-action="stub-select-city" @click="$emit(\'region-select\', { code: \'610100\', name: \'西安市\' })">选西安</button></div>'
  }
}));

vi.mock('../CorporateTrend.vue', () => ({
  default: { props: ['trend', 'dataDate', 'scopeLabel'], template: '<div data-testid="corporate-trend" />' }
}));

describe('CorporateDashboard 对公经营总览', () => {
  it('画像关闭和刷新过渡保留搜索与选中，混层值不标为城市汇总', async () => {
    const {default:Dashboard}=await import('../CorporateDashboard.vue');
    const model={scopeLabel:'授权组A',rankings:[{orgCode:'A',name:'甲机构',cityCode:'610100',deposit:1}],institutions:[{orgCode:'A',orgName:'甲机构',cityCode:'610100'}]};
    const wrapper=mount(Dashboard,{props:{model,sourcePresentation:{sourceAvailability:{corpRanking:{status:'PARTIAL',message:'混合层级测试对照'},corpAttention:{status:'NO_SOURCE',message:'尚未接入'}}}}});
    await wrapper.get('[data-testid="corporate-ranking-row"]').trigger('click');
    await wrapper.get('[data-testid="corporate-directory-search"]').setValue('甲');
    await wrapper.get('button[aria-label="关闭机构目录"]').trigger('click');
    expect(wrapper.get('[data-testid="corporate-ranking-row"]').attributes('aria-current')).toBe('true');
    await wrapper.setProps({loading:true,model:{scopeLabel:'授权组A',institutions:[],rankings:[]}});
    await wrapper.setProps({loading:false,model:{...model}});
    await wrapper.get('[data-testid="corporate-ranking-row"]').trigger('click');
    expect(wrapper.get('[data-testid="corporate-directory-search"]').element.value).toBe('甲');
    expect(wrapper.findComponent('[data-testid="corporate-map"]').props('metricValues')).toEqual({});
    expect(wrapper.get('[data-testid="corporate-leadership-insights"]').text()).not.toContain('0项');
    wrapper.unmount();
  });
  it('只为对公页启用选定的浮雕地图外观', async () => {
    const { default: Dashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(Dashboard, { props: { demo: true, model: {} } });
    expect(wrapper.get('[data-testid="corporate-map"]').attributes('data-appearance')).toBe('relief');
    wrapper.unmount();
  });

  it('以对公业务语义呈现 KPI、重点客群信贷、经营关注、排名和目标', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      props: {
        demo: true,
        model: {
          title: '对公经营总览', scopeLabel: '省分行授权范围', dataDate: '2026-09-08',
          kpis: [
            { key: 'corpDeposit', value: 1200, unit: '亿元', change: 2.5 },
            { key: 'corpDepositAverage', value: 1180, unit: '亿元', change: null },
            { key: 'corpLoan', value: 980, unit: '亿元', change: -1 },
            { key: 'corpRevenue', value: 24.5, unit: '亿元', change: 4 },
            { key: 'corpCustomers', value: 8.6, unit: '万户', change: null },
            { key: 'corpNplRate', value: 1.2, unit: '%', change: -0.1 }
          ],
          trend: [{ date: '2026-09', deposit: 1200, loan: 980 }],
          segments: [{ name: '制造业', customers: 2, loan: 310 }],
          rankings: [{ orgCode: 'A', name: '西安分行', deposit: 420, increase: -3, rate: 88, nplRate: 1.1, cityCode: null }],
          attention: [{ label: '重点客户授信逾期', count: 0, owner: '公司部', deadline: '2026-09-30' }],
          targets: [{ name: '对公存款', actual: 1200, target: 1300 }],
          institutions: []
        }
      }
    });
    expect(wrapper.attributes('aria-label')).toBe('对公经营总览');
    expect(wrapper.findAll('[data-testid="corporate-kpi"]')).toHaveLength(6);
    expect(wrapper.text()).toContain('对公存款经营');
    expect(wrapper.text()).toContain('重点客群信贷');
    expect(wrapper.text()).toContain('需要协调的事项');
    expect(wrapper.text()).toContain('对公存款排名');
    expect(wrapper.text()).toContain('对公经营目标');
    expect(wrapper.find('[data-testid="corporate-demo-badge"]').text()).toContain('非业务数据');
    expect(wrapper.find('[data-testid="corporate-ranking-row"]').text()).toContain('西安分行');
  });

  it('排名可切换存款、净增和完成率，负数仍可排序', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      props: {
        model: {
          rankings: [
            { orgCode: 'A', name: '甲', deposit: 100, increase: -10, rate: 80 },
            { orgCode: 'B', name: '乙', deposit: 80, increase: 5, rate: 70 }
          ]
        }
      }
    });
    await wrapper.find('[data-ranking-metric="increase"]').trigger('click');
    expect(wrapper.findAll('[data-testid="corporate-ranking-row"]')[0].text()).toContain('乙');
    await wrapper.find('[data-ranking-order="lagging"]').trigger('click');
    expect(wrapper.findAll('[data-testid="corporate-ranking-row"]')[0].text()).toContain('甲');
  });

  it('事项详情和机构目录使用原生键盘焦点、Escape 与背景滚动锁', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      attachTo: document.body,
      props: {
        demo: true,
        model: {
          scopeLabel: '全辖（示例）', dataDate: '2026-09-08',
          attention: [{ label: '重点项目', count: 0, owner: null, deadline: null }],
          institutions: [{ orgCode: 'A', name: '甲支行', cityCode: '610100', cityName: '西安市', located: false }],
          rankings: [{ orgCode: 'A', name: '甲支行', deposit: 1, increase: 0, rate: 80, nplRate: null }]
        }
      }
    });
    const attentionRow = wrapper.get('[data-testid="corporate-attention-row"]');
    await attentionRow.trigger('click');
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="corporate-attention-detail"]')).toBeTruthy();
    expect(document.body.style.overflow).toBe('hidden');
    const attentionDialog = wrapper.get('[data-testid="corporate-attention-detail"] .corporate-attention-dialog');
    await attentionDialog.trigger('keydown', { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(wrapper.findAll('[data-action="close-corporate-attention"]').at(-1).element);
    await wrapper.get('[data-action="close-corporate-attention"]').trigger('click');
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="corporate-attention-detail"]').exists()).toBe(false);
    expect(document.body.style.overflow).toBe('');

    await wrapper.get('[data-action="open-corporate-directory"]').trigger('click');
    await wrapper.vm.$nextTick();
    expect(document.activeElement).toBe(wrapper.get('[data-testid="corporate-directory-search"]').element);
    await wrapper.get('[data-testid="corporate-directory-dialog"]').trigger('keydown', { key: 'Escape' });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="corporate-directory-dialog"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('经营观察按目标缺口、负增/落后机构、协调事项和数据缺项组织', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      props: {
        model: {
          rankings: [{ orgCode: 'A', name: '甲', deposit: 100, increase: -2, rate: 80 }],
          targets: [{ name: '对公存款', actual: 80, target: 100 }],
          attention: [{ label: '甲机构待跟进任务', count: 2 }]
        }
      }
    });
    const items = wrapper.findAll('[data-testid="corporate-leadership-insights"] .corporate-insight-item');
    expect(items.map(item => item.find('span').text())).toEqual(['目标缺口', '负增 / 落后机构', '协调事项', '数据缺项']);
    expect(items[0].text()).toContain('有缺口 1 项');
    expect(items[1].text()).toContain('1');
    expect(items[2].text()).toContain('2');
    expect(items[3].text()).toContain('');
    wrapper.unmount();
  });

  it('刷新保持有效的城市、指标、顺序和目录筛选，范围变化才清理旧筛选', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const baseModel = {
      scopeLabel: '全辖机构',
      rankings: [
        { orgCode: 'A', name: '甲', cityCode: '610100', deposit: 100, increase: -2, rate: 80 },
        { orgCode: 'B', name: '乙', cityCode: '610800', deposit: 80, increase: 4, rate: 95 }
      ],
      institutions: [{ orgCode: 'A', name: '甲', cityCode: '610100', cityName: '西安市', located: true }]
    };
    const wrapper = mount(CorporateDashboard, { props: { model: baseModel } });
    await wrapper.get('[data-action="stub-select-city"]').trigger('click');
    await wrapper.get('[data-ranking-metric="increase"]').trigger('click');
    await wrapper.get('[data-ranking-order="lagging"]').trigger('click');
    expect(wrapper.get('[data-testid="corporate-selected-city"]').text()).toContain('西安市');
    await wrapper.setProps({ model: { ...baseModel, kpis: [{ key: 'corpDeposit', value: 101 }] } });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-ranking-metric="increase"]').classes()).toContain('active');
    expect(wrapper.get('[data-ranking-order="lagging"]').classes()).toContain('active');
    expect(wrapper.get('[data-testid="corporate-selected-city"]').text()).toContain('西安市');
    await wrapper.setProps({ model: { ...baseModel, scopeLabel: '仅西安机构', rankings: [] } });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="corporate-selected-city"]').exists()).toBe(false);
    expect(wrapper.get('[data-ranking-metric="deposit"]').classes()).toContain('active');
    wrapper.unmount();
  });

  it('混合层级排名只保留数值对照，隐藏业务名次和领先短板并禁用无来源指标', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      props: {
        sourcePresentation: {
          sourceAvailability: {
            corpRanking: {
              status: 'PARTIAL',
              message: '混合层级测试对照，仅按数值排序，不代表同层绩效排名',
              fields: {
                increase: { status: 'NO_SOURCE', message: '净增来源暂无数据' },
                rate: { status: 'NO_SOURCE', message: '完成率来源暂无数据' },
                nplRate: { status: 'NO_SOURCE', message: '不良率来源暂无数据' }
              }
            }
          }
        },
        model: { rankings: [{ orgCode: 'A', name: '甲', deposit: 100, increase: null, rate: null }] }
      }
    });
    expect(wrapper.get('[data-testid="corporate-ranking-title"]').text()).toContain('数值对照');
    expect(wrapper.text()).toContain('混合层级测试对照');
    expect(wrapper.find('[data-ranking-order="leading"]').exists()).toBe(false);
    expect(wrapper.get('[data-ranking-metric="increase"]').element.disabled).toBe(true);
    expect(wrapper.get('[data-testid="corporate-ranking-row"] .corporate-ranking-row__number').text()).toBe('·');
    wrapper.unmount();
  });

  it('对公协调来源未接入时明确显示未接入，不把空数组误报为0项', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      props: {
        sourcePresentation: { sourceAvailability: { corpAttention: { status: 'NO_SOURCE', message: '对公协调来源尚未绑定' } } },
        model: { attention: [] }
      }
    });
    expect(wrapper.get('[data-testid="corporate-leadership-insights"] .corporate-insight-item:nth-child(3)').text()).toContain('—');
    expect(wrapper.get('[data-testid="corporate-attention"]').text()).toContain('未接入');
    expect(wrapper.get('[data-testid="corporate-attention"]').text()).toContain('尚未绑定');
    wrapper.unmount();
  });
});
