// @vitest-environment happy-dom
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    props: ['geoJson', 'points', 'selectedRegionCode', 'mode', 'demo'],
    emits: ['region-select', 'branch-select'],
    template: '<div class="panorama-map-stub" data-testid="retail-map"><button type="button" data-action="select-xian" @click="$emit(\'region-select\', { code: \'610100\', name: \'西安市\' })">选择西安</button></div>'
  }
}));

vi.mock('vue-echarts', () => ({
  default: {
    props: { option: { type: Object, default: () => ({}) } },
    template: '<div data-testid="retail-chart" :data-option="JSON.stringify(option)" />'
  }
}));

vi.mock('element-plus', () => ({
  ElDialog: {
    name: 'ElDialog',
    props: { modelValue: Boolean, title: String },
    template: '<div v-if="modelValue" class="el-dialog-stub" role="dialog" aria-modal="true"><h2>{{ title }}</h2><slot /><slot name="footer" /></div>'
  }
}));

import RetailDashboard from '../RetailDashboard.vue';

const model = {
  title: '零售经营总览',
  dataDate: '2026-09-06',
  kpis: [
    { key: 'retailAum', label: '零售AUM', value: 1824.6, unit: '亿元', change: 3.2 },
    { key: 'retailDeposit', label: '储蓄余额', value: 1286.42, unit: '亿元', change: -0.1 },
    { key: 'retailRevenue', label: '零售营业收入', value: 32.68, unit: '亿元', change: 8.1 },
    { key: 'retailValueCustomers', label: '价值客户', value: 86.24, unit: '万户', change: 4.1 },
    { key: 'retailLoan', label: '个人贷款', value: 968.35, unit: '亿元', change: 5.4 },
    { key: 'retailNplRate', label: '个贷不良率', value: 1.8, unit: '%', change: 0.2 },
    { key: 'retailDepositAverage', label: '储蓄月日均', value: 1287.26, unit: '亿元', change: null }
  ],
  trend: [
    { date: '2026-04', aum: 1762.3, deposit: 1268.12 },
    { date: '2026-05', aum: 1781.4, deposit: 1261.4 },
    { date: '2026-06', aum: 1802.8, deposit: 1277.86 }
  ],
  segments: [
    { name: '私行客户', customers: 8.6, aum: 386.2 },
    { name: '代发客户', customers: 42.1, aum: 517.8 }
  ],
  rankings: [
    { orgCode: 'ORG-XIAN', name: '西安市分行', aum: 520.6, increase: 12.4, rate: 98.2, nplRate: 1.4, cityCode: '610100' },
    { orgCode: 'ORG-WEINAN', name: '渭南市分行', aum: 184.2, increase: -4.2, rate: 76.3, nplRate: 2.3, cityCode: '610500' },
    { orgCode: 'ORG-UNKNOWN', name: '零售数据服务中心', aum: null, increase: 0, rate: null, nplRate: null, cityCode: null }
  ],
  attention: [
    { label: '重点客户维护', count: 4, owner: '零售金融部', deadline: '2026-09-15' },
    { label: '风险数据待核验', count: 0, owner: null, deadline: null }
  ],
  targets: [
    { name: '零售AUM增长', actual: 12.5, target: 10 },
    { name: '储蓄余额净增', actual: -3.6, target: 12 },
    { name: '价值客户增量', actual: 0, target: 0 }
  ],
  institutions: [
    { orgCode: 'ORG-XIAN', name: '西安市分行', cityCode: '610100', cityName: '西安市' },
    { orgCode: 'ORG-WEINAN', name: '渭南市分行', cityCode: '610500', cityName: '渭南市' },
    { orgCode: 'ORG-UNKNOWN', name: '零售数据服务中心', cityCode: null, cityName: null }
  ],
  issues: []
};

const mounted = [];

function mountDashboard(overrides = {}) {
  const wrapper = mount(RetailDashboard, {
    props: { model, loading: false, error: '', demo: false, ...overrides },
    attachTo: document.body
  });
  mounted.push(wrapper);
  return wrapper;
}

afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount());
  document.body.style.overflow = '';
});

describe('RetailDashboard 零售经营总览', () => {
  it('演示从大屏中心进入时返回按钮使用大屏中心语义，正式页保留原文案', () => {
    const formalWrapper = mountDashboard();
    expect(formalWrapper.get('[data-action="back"]').attributes('aria-label')).toBe('返回分行预览');

    const screenCenterWrapper = mountDashboard({ demo: true, backLabel: '返回大屏中心' });
    expect(screenCenterWrapper.get('[data-action="back"]').attributes('aria-label')).toBe('返回大屏中心');
  });

  it('小额主库收入按万元显示，不将非零收入四舍五入为零', () => {
    const wrapper=mount(RetailDashboard,{props:{model:{kpis:[{key:'retailRevenue',label:'零售FTP收入',value:0.00020804809717,unit:'亿元'}]}}});
    const card=wrapper.findAll('[data-testid="retail-kpi"]').find(card=>card.text().includes('零售FTP收入'));
    expect(card.text()).toContain('2.08');expect(card.text()).toContain('万元');
    wrapper.unmount();
  });
  it('保留六项零售 KPI 并增加月日均卡片，已绑定值按原口径展示', () => {
    const wrapper = mountDashboard();
    expect(wrapper.findAll('[data-testid="retail-kpi"]')).toHaveLength(7);
    expect(wrapper.get('[data-kpi-key="retailAum"]').text()).toContain('1,824.60');
    expect(wrapper.get('[data-kpi-key="retailValueCustomers"]').text()).toContain('86.24');
    expect(wrapper.get('[data-kpi-key="retailNplRate"]').text()).toContain('↑ 0.20pp');
    expect(wrapper.get('[data-kpi-key="retailNplRate"] .retail-kpi__change').classes()).toContain('is-risk');
  });

  it('AUM 和价值客户未绑定时保留组件并显示明确缺失状态', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      kpis: model.kpis.filter(item => !['retailAum', 'retailValueCustomers'].includes(item.key))
    } });
    expect(wrapper.get('[data-kpi-key="retailAum"]').text()).toContain('暂无数据源');
    expect(wrapper.get('[data-kpi-key="retailValueCustomers"]').text()).toContain('口径未配置');
  });

  it('AUM 和价值客户槽位已绑定但无有效值时不误报为未配置', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      kpis: [
        { key: 'retailAum', label: '零售AUM', value: null, unit: '亿元' },
        { key: 'retailValueCustomers', label: '价值客户', value: null, unit: '万户' }
      ]
    } });
    expect(wrapper.get('[data-kpi-key="retailAum"]').text()).toContain('暂无有效值');
    expect(wrapper.get('[data-kpi-key="retailAum"]').text()).not.toContain('暂无数据源');
    expect(wrapper.get('[data-kpi-key="retailValueCustomers"]').text()).toContain('暂无有效值');
  });

  it('未接入客户分层时明确说明价值客户口径和数据源缺失', () => {
    const wrapper = mountDashboard({ model: { ...model, segments: [] } });
    expect(wrapper.get('[data-testid="retail-segments"]').text()).toContain('价值客户口径与客户分层数据源尚未接入');
  });

  it('储蓄经营使用单独的月日均字段，客户分层不求和重叠客户', () => {
    const wrapper = mountDashboard();
    expect(wrapper.get('[data-testid="retail-deposit-balance"]').text()).toContain('1,286.42');
    expect(wrapper.get('[data-testid="retail-deposit-average"]').text()).toContain('1,287.26');
    expect(wrapper.get('[data-testid="retail-deposit-change"]').text()).toContain('↓ 0.10%');
    expect(wrapper.get('[data-testid="retail-segments"]').text()).toContain('分层口径以业务定义为准');
    expect(wrapper.findAll('[data-testid="retail-segment-row"]')).toHaveLength(2);
  });

  it('地图选择城市只过滤机构排名，辖内 KPI 与趋势仍使用全辖模型', async () => {
    const wrapper = mountDashboard();
    expect(wrapper.get('[data-testid="retail-scope-note"]').text()).toContain('KPI 与趋势不随城市筛选变化');
    await wrapper.get('[data-action="select-xian"]').trigger('click');
    expect(wrapper.get('[data-testid="retail-selected-city"]').text()).toContain('西安市');
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="retail-ranking-row"]').text()).toContain('西安市分行');
    expect(wrapper.get('[data-testid="retail-trend"]').attributes('data-point-count')).toBe('3');
    await wrapper.get('[data-action="clear-city"]').trigger('click');
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')).toHaveLength(3);
  });

  it('排名可切换指标与领先/短板顺序，null 保留为缺失且不参与首位', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-ranking-metric="increase"]').trigger('click');
    await wrapper.get('[data-ranking-order="lagging"]').trigger('click');
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')[0].text()).toContain('渭南市分行');
    expect(wrapper.get('[data-testid="retail-ranking-row"]').text()).toContain('-4.20');
    await wrapper.get('[data-ranking-metric="rate"]').trigger('click');
    expect(wrapper.get('[data-testid="retail-ranking-unit"]').text()).toContain('%');
  });

  it('目标完成率保留真实超额或负数，进度条限制视觉宽度且无效目标明确提示', () => {
    const wrapper = mountDashboard();
    expect(wrapper.get('[data-target-name="零售AUM增长"]').text()).toContain('125.00%');
    expect(wrapper.get('[data-target-name="零售AUM增长"] .retail-target-bar i').attributes('style')).toContain('width: 100%');
    expect(wrapper.get('[data-target-name="储蓄余额净增"]').text()).toContain('-30.00%');
    expect(wrapper.get('[data-target-name="价值客户增量"]').text()).toContain('无有效目标');
  });

  it('经营关注只展示来源字段，不自动生成逾期任务', () => {
    const wrapper = mountDashboard();
    const attention = wrapper.get('[data-testid="retail-attention"]');
    expect(attention.text()).toContain('责任归属与跟进时限');
    expect(attention.text()).toContain('零售金融部');
    expect(attention.text()).toContain('2026-09-15');
    expect(attention.findAll('li')).toHaveLength(2);
  });

  it('事项行是原生按钮，点击后展示演示详情、责任范围与数据日期，并可关闭恢复焦点', async () => {
    const wrapper = mountDashboard({
      demo: true,
      model: {
        ...model,
        scopeLabel: '陕西省全辖（示例）',
        attention: [{
          label: '重点客户维护',
          count: 4,
          owner: '零售金融部',
          deadline: '2026-09-15',
          detail: {
            description: '维护重点客户关系',
            coordination: '请零售金融部协调客户经理跟进',
            source: '本地演示台账'
          }
        }]
      }
    });
    const row = wrapper.get('[data-testid="retail-attention-row"]');
    expect(row.element.tagName).toBe('BUTTON');
    expect(row.attributes('type')).toBe('button');
    expect(row.attributes('aria-label')).toContain('重点客户维护');
    await row.trigger('click');
    const detail = wrapper.get('[data-testid="retail-attention-detail"]');
    expect(detail.text()).toContain('重点客户维护');
    expect(detail.get('[data-testid="retail-attention-detail-count"]').text()).toBe('4');
    expect(detail.text()).toContain('零售金融部');
    expect(detail.text()).toContain('2026-09-15');
    expect(detail.text()).toContain('陕西省全辖（示例）');
    expect(detail.text()).toContain('2026-09-06');
    expect(detail.text()).toContain('维护重点客户关系');
    expect(detail.text()).toContain('请零售金融部协调客户经理跟进');
    expect(detail.text()).toContain('本地演示台账');
    expect(detail.text()).toContain('本地演示 · 非业务数据');
    await wrapper.get('[data-action="close-retail-attention"]').trigger('click');
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="retail-attention-detail"]').exists()).toBe(false);
    expect(document.activeElement).toBe(row.element);
  });

  it('正式事项不展示演示扩展，数量区分 0 与缺失值，行使用原生按钮激活', async () => {
    const wrapper = mountDashboard({
      model: {
        ...model,
        attention: [
          { label: '零事项', count: 0, owner: null, deadline: null, detail: { description: '不应展示' } },
          { label: '缺失事项', count: null, owner: null, deadline: null }
        ]
      }
    });
    const zeroRow = wrapper.findAll('[data-testid="retail-attention-row"]')[0];
    expect(zeroRow.element.tagName).toBe('BUTTON');
    await zeroRow.trigger('click');
    const zeroDetail = wrapper.get('[data-testid="retail-attention-detail"]');
    expect(zeroDetail.get('[data-testid="retail-attention-detail-count"]').text()).toBe('0');
    expect(zeroDetail.text()).toContain('未提供事项说明');
    expect(zeroDetail.text()).toContain('未提供协调要求');
    expect(zeroDetail.text()).not.toContain('不应展示');
    await wrapper.get('[data-action="close-retail-attention"]').trigger('click');
    await wrapper.vm.$nextTick();

    const missingRow = wrapper.findAll('[data-testid="retail-attention-row"]')[1];
    await missingRow.trigger('click');
    expect(wrapper.get('[data-testid="retail-attention-detail-count"]').text()).toBe('—');
  });

  it('换屏或授权错误时清空事项详情，不残留上一个事项', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-testid="retail-attention-row"]').trigger('click');
    expect(wrapper.find('[data-testid="retail-attention-detail"]').exists()).toBe(true);
    await wrapper.setProps({ model: { ...model, attention: [] } });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="retail-attention-detail"]').exists()).toBe(false);
    await wrapper.setProps({ model, error: '403 Forbidden' });
    expect(wrapper.find('[data-testid="retail-attention-detail"]').exists()).toBe(false);
  });

  it('长内容完整保留且可键盘聚焦，不引入下拉筛选', () => {
    const longModel = {
      ...model,
      segments: Array.from({ length: 12 }, (_, index) => ({
        name: `客户分层${index + 1}`,
        customers: index + 1,
        aum: index + 2
      })),
      rankings: Array.from({ length: 14 }, (_, index) => ({
        orgCode: `ORG-${index + 1}`,
        name: `机构${index + 1}`,
        aum: index + 1,
        increase: index,
        rate: 80 + index,
        nplRate: 1 + index / 10
      })),
      attention: Array.from({ length: 10 }, (_, index) => ({
        label: `待协调事项${index + 1}`,
        count: index,
        owner: '零售金融部',
        deadline: '2026-09-15'
      })),
      targets: Array.from({ length: 9 }, (_, index) => ({
        name: `经营目标${index + 1}`,
        actual: index + 1,
        target: index + 2
      }))
    };
    const wrapper = mountDashboard({ model: longModel });
    const regions = [
      ['.retail-savings__body', '储蓄核心指标内容'],
      ['.retail-segment-list', '客户分层列表'],
      ['.retail-attention-list', '经营关注事项'],
      ['.retail-ranking-list', '机构排名列表'],
      ['.retail-target-list', '零售经营目标列表']
    ];

    regions.forEach(([selector, label]) => {
      const region = wrapper.get(selector);
      expect(region.attributes('tabindex')).toBe('0');
      expect(region.attributes('aria-label')).toBe(label);
    });
    expect(wrapper.findAll('[data-testid="retail-segment-row"]')).toHaveLength(12);
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')).toHaveLength(14);
    expect(wrapper.get('[data-testid="retail-attention"]').findAll('li')).toHaveLength(10);
    expect(wrapper.findAll('.retail-target-row')).toHaveLength(9);
    expect(wrapper.findAll('select')).toHaveLength(0);
  });

  it('机构行打开本地详情，详情仅展示目录身份和对应排名指标，Escape 可关闭', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-testid="retail-ranking-row"]').trigger('click');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').text()).toContain('西安市分行');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').text()).toContain('AUM');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').text()).not.toContain('趋势');
    await wrapper.get('[data-testid="retail-institution-dialog"]').trigger('keydown.esc');
    expect(wrapper.find('[data-testid="retail-institution-dialog"]').exists()).toBe(false);
  });

  it('机构目录支持搜索与键盘选择，并能看到没有 cityCode 的机构', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-action="open-retail-directory"]').trigger('click');
    expect(wrapper.get('[data-testid="retail-directory-search"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="retail-directory-row"]')).toHaveLength(3);
    await wrapper.get('[data-testid="retail-directory-search"]').setValue('数据服务');
    expect(wrapper.findAll('[data-testid="retail-directory-row"]')).toHaveLength(1);
    await wrapper.get('[data-testid="retail-directory-row"]').trigger('keydown.enter');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').text()).toContain('零售数据服务中心');
  });

  it('模型被拒绝或换屏清空时，目录、机构选择和城市筛选一起清除', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-action="select-xian"]').trigger('click');
    await wrapper.get('[data-testid="retail-ranking-row"]').trigger('click');
    expect(wrapper.find('[data-testid="retail-institution-dialog"]').exists()).toBe(true);
    await wrapper.setProps({ error: '403 Forbidden', model: { ...model, kpis: [], rankings: [], institutions: [] } });
    expect(wrapper.find('[data-testid="retail-institution-dialog"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="retail-selected-city"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="retail-scope-note"]').text()).toContain('当前大屏授权范围');
  });

  it('同一授权范围刷新保留城市、指标、顺序、目录搜索和有效机构选择', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-action="select-xian"]').trigger('click');
    await wrapper.get('[data-ranking-metric="increase"]').trigger('click');
    await wrapper.get('[data-ranking-order="lagging"]').trigger('click');
    await wrapper.get('[data-action="open-retail-directory"]').trigger('click');
    await wrapper.get('[data-testid="retail-directory-search"]').setValue('西安');
    await wrapper.get('[data-testid="retail-directory-row"]').trigger('click');
    expect(wrapper.get('[data-testid="retail-directory-search"]').element.value).toBe('西安');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').exists()).toBe(true);

    await wrapper.setProps({ model: {
      ...model,
      kpis: model.kpis.map(item => item.key === 'retailDeposit' ? { ...item, value: 1290 } : item)
    }, loading: false });
    await wrapper.vm.$nextTick();

    expect(wrapper.get('[data-testid="retail-selected-city"]').text()).toContain('西安市');
    expect(wrapper.get('[data-ranking-metric="increase"]').classes()).toContain('active');
    expect(wrapper.get('[data-ranking-order="lagging"]').classes()).toContain('active');
    expect(wrapper.get('[data-testid="retail-directory-search"]').element.value).toBe('西安');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').exists()).toBe(true);
  });

  it('loading期间的空模型过渡不丢失当前筛选状态', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-action="select-xian"]').trigger('click');
    await wrapper.get('[data-ranking-metric="increase"]').trigger('click');
    await wrapper.get('[data-ranking-order="lagging"]').trigger('click');

    await wrapper.setProps({
      loading: true,
      model: { ...model, kpis: [], trend: [], rankings: [], institutions: [] }
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.get('[data-testid="retail-selected-city"]').text()).toContain('西安市');
    expect(wrapper.get('[data-ranking-metric="increase"]').classes()).toContain('active');
    expect(wrapper.get('[data-ranking-order="lagging"]').classes()).toContain('active');
  });

  it('桌面三列按运行时 chrome 高度自然分配可视区', () => {
    const stylesheet = readFileSync(resolve(process.cwd(), 'src/views/screen/panorama/retail.scss'), 'utf8').replace(/\r\n/g, '\n');
    expect(stylesheet).toContain('@media (min-width: 1501px)');
    expect(stylesheet).toContain('height: calc(100dvh - var(--cockpit-chrome-height, 0px))');
    expect(stylesheet).toContain('min-height: calc(100dvh - var(--cockpit-chrome-height, 0px))');
    expect(stylesheet).toContain('.retail-main-grid {\n    flex: 1 1 0;');
  });

  it('区分目标未配置和实际值待更新，并直说距目标或超目标金额', () => {
    const wrapper = mountDashboard({ model: { ...model, targets: [
      { name: '收入待更新', actual: null, target: 10 },
      { name: '收入缺口', actual: 8, target: 10 },
      { name: '资产超额', actual: 12, target: 10 }
    ] } });
    expect(wrapper.get('[data-target-name="收入待更新"]').text()).toContain('实际待更新');
    expect(wrapper.get('[data-target-name="收入待更新"]').text()).not.toContain('无有效目标');
    expect(wrapper.get('[data-target-name="收入缺口"]').text()).toContain('距目标 2.00 亿元');
    expect(wrapper.get('[data-target-name="资产超额"]').text()).toContain('超目标 2.00 亿元');
  });

  it('零售顶部保留六项原指标并增加月日均卡片，缺失值用状态文案', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      kpis: model.kpis.filter(item => item.key !== 'retailDepositAverage')
    } });
    expect(wrapper.findAll('[data-testid="retail-kpi"]')).toHaveLength(7);
    expect(wrapper.get('[data-kpi-key="retailDepositAverage"]').text()).toContain('暂无数据源');
  });

  it('机构存款对比默认筛选分行，切换月日均后显示口径差额而非净增', async () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      kpis: model.kpis,
      trend: [
        { date: '2026-07', deposit: 100, depositAverage: 99 },
        { date: '2026-08', deposit: 103, depositAverage: 102 }
      ],
      rankings: [
        { orgCode: 'P1', name: '甲分行', deposit: 100, average: 98, date: '2026-09-06', orgNature: 'PRIMARY' },
        { orgCode: 'P2', name: '乙分行', deposit: 80, average: 81, date: '2026-09-06', operatingLevel: 'SECONDARY_BRANCH' },
        { orgCode: 'O1', name: '待分类机构', deposit: 50, average: 49, date: '2026-09-06', orgNature: 'NONE' }
      ],
      institutions: [
        { orgCode: 'P1', name: '甲分行', cityCode: '610100' },
        { orgCode: 'P2', name: '乙分行', cityCode: '610500' },
        { orgCode: 'O1', name: '待分类机构', cityCode: null }
      ]
    } });
    const panel = wrapper.get('[data-testid="retail-institution-comparison"]');
    expect(panel.text()).toContain('不汇总');
    expect(panel.text()).not.toContain('排名');
    expect(panel.get('[data-institution-filter="primary"]').classes()).toContain('active');
    expect(panel.findAll('[data-testid="retail-institution-row"]')).toHaveLength(2);
    expect(panel.text()).toContain('月均低于余额');
    await panel.get('[data-ranking-metric="average"]').trigger('click');
    expect(panel.get('[data-testid="retail-institution-value"]').text()).toContain('98.00');
    expect(panel.get('[data-testid="retail-institution-difference"]').text()).toContain('-2.00');
    expect(panel.text()).toContain('口径对照，不代表净增');
  });

  it('观察条展示机构覆盖、月均低于时点余额数量、目标和当前日期覆盖', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      rankings: [
        { orgCode: 'P1', deposit: 100, average: 98, date: '2026-09-06', orgNature: 'PRIMARY' },
        { orgCode: 'P2', deposit: 80, average: 82, date: '2026-09-06', orgNature: 'PRIMARY' },
        { orgCode: 'P3', deposit: null, average: null, date: null, orgNature: 'PRIMARY' }
      ],
      institutions: [{ orgCode: 'P1' }, { orgCode: 'P2' }, { orgCode: 'P3' }]
    } });
    const strip = wrapper.get('[data-testid="retail-leadership-insights"]');
    expect(strip.text()).toContain('机构数据覆盖');
    expect(strip.text()).toContain('2/3');
    expect(strip.text()).toContain('月均低于余额');
    expect(strip.text()).toContain('1 家');
    expect(strip.text()).toContain('日期覆盖');
    expect(strip.text()).toContain('2026-09-06');
  });

  it('重复的数据日期核验项合并展示指标和实际日期，不生成三条相同事项', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      issues: [
        { label: '数据日期待核验', metricLabel: '存款余额', actualDate: '2026-09-18' },
        { label: '数据日期待核验', metricLabel: '存款月日均', actualDate: '2026-09-19' },
        { label: '数据日期待核验', metricLabel: '机构对比', actualDate: '2026-09-19' }
      ]
    } });
    const attention = wrapper.get('[data-testid="retail-attention"]');
    expect(attention.findAll('[data-testid="retail-attention-row"]')).toHaveLength(3);
    expect(attention.text()).toContain('数据日期待核验（3项）');
    expect(attention.text()).toContain('2026-09-18、2026-09-19');
  });

  it('日期不一致事项使用已绑定指标标签和 KPI 日期，避免泛化为日期待确认', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      kpis: [
        { key: 'retailRevenue', label: '零售FTP收入', value: 1, unit: '亿元', date: '2026-07-24' },
        { key: 'retailLoan', label: '个人贷款', value: 2, unit: '亿元', date: '2026-07-24' },
        { key: 'retailNplRate', label: '个贷不良率', value: 1, unit: '%', date: '2026-07-24' },
        { key: 'retailDeposit', label: '零售一般性存款余额', value: 3, unit: '亿元', date: '2026-09-19' }
      ],
      attention: [],
      issues: [
        { slot: 'retailRevenue', code: 'MIXED_DATES', message: '日期不一致' },
        { slot: 'retailLoan', code: 'MIXED_DATES', message: '日期不一致' },
        { slot: 'retailNplRate', code: 'MIXED_DATES', message: '日期不一致' },
        { slot: 'retailDeposit', code: 'MIXED_DATES', message: '日期不一致' }
      ]
    } });
    const attention = wrapper.get('[data-testid="retail-attention"]');
    expect(attention.findAll('[data-testid="retail-attention-row"]')).toHaveLength(1);
    expect(attention.text()).toContain('指标统计日期不一致（4项）');
    expect(attention.text()).toContain('零售FTP收入：2026-07-24');
    expect(attention.text()).toContain('零售一般性存款余额：2026-09-19');
    expect(attention.text()).not.toContain('日期待确认');
  });

  it('关注面板补充当前已分类机构的月均低于余额差额，点击仍打开机构存款详情', async () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      attention: [],
      issues: [],
      rankings: [
        { orgCode: 'P1', name: '甲分行', deposit: 100, average: 98, orgNature: 'PRIMARY' },
        { orgCode: 'P2', name: '乙分行', deposit: 80, average: 70, orgNature: 'PRIMARY' },
        { orgCode: 'P3', name: '丙分行', deposit: 60, average: 58, orgNature: 'PRIMARY' },
        { orgCode: 'P4', name: '丁分行', deposit: 40, average: 39, orgNature: 'PRIMARY' }
      ],
      institutions: [
        { orgCode: 'P1', name: '甲分行' },
        { orgCode: 'P2', name: '乙分行' },
        { orgCode: 'P3', name: '丙分行' },
        { orgCode: 'P4', name: '丁分行' }
      ]
    } });
    const panel = wrapper.get('[data-testid="retail-attention"]');
    const list = panel.get('[data-testid="retail-attention-difference-list"]');
    expect(list.text()).toContain('日均与时点差额关注');
    expect(list.text()).toContain('乙分行');
    expect(list.text()).toContain('-10.00');
    expect(list.findAll('[data-testid="retail-attention-difference-row"]')).toHaveLength(3);
    expect(list.text()).toContain('不代表净增');
    await list.findAll('[data-testid="retail-attention-difference-row"]')[0].trigger('click');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').text()).toContain('乙分行');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').text()).toContain('月日均');
  });

});
