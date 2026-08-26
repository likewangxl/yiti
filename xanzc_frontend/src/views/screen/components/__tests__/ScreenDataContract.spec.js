// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const queryMock = vi.fn().mockResolvedValue({ columns: [], rows: [] });
vi.mock('@/api/screen', () => ({ queryScreenData: (...args) => queryMock(...args) }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('vue-echarts', () => ({ default: { name: 'VChart', props: ['option'], template: '<div class="v-chart-stub" />' } }));

import BlockContainer from '../BlockContainer.vue';
import DrillTrend from '../DrillTrend.vue';
import MetricCard from '../MetricCard.vue';

const componentGlobals = { stubs: { 'el-icon': true, InfoFilled: true }, directives: { loading: {} } };

describe('大屏区块取数请求契约', () => {
  beforeEach(() => { queryMock.mockClear(); });

  it('SCR_BRANCH 的 5 个 schema1 区块只提交非空日期/上下文，保留 orgCode=128 与既有身份契约', async () => {
    const bindings = [
      { dsId: 9002, period: 'LATEST' },
      { dsId: 9002, period: 'LAST_1M' },
      { dsId: 9011, period: 'LATEST' },
      { dsId: 9011, period: 'LATEST' },
      { dsId: 9011, period: 'LATEST' }
    ];
    const wrappers = bindings.map((bind, index) => mount(BlockContainer, {
      props: {
        block: {
          id: 101 + index,
          componentType: 'METRIC_CARD',
          bindJson: JSON.stringify(bind),
          styleJson: JSON.stringify({ refreshSec: 0 }),
          drillJson: '{}'
        },
        context: { screenCode: 'SCR_BRANCH', schemaVersion: 1, orgCode: '128', empId: null }
      },
      global: componentGlobals
    }));

    await vi.waitFor(() => expect(queryMock).toHaveBeenCalledTimes(5));
    expect(queryMock.mock.calls.map(([body]) => body)).toEqual(bindings.map(bind => ({
      schemaVersion: 1,
      screenCode: 'SCR_BRANCH',
      dsId: bind.dsId,
      period: bind.period,
      contextParams: { orgCode: '128' }
    })));
    wrappers.forEach(item => item.unmount());
  });

  it('根据 screenCode + blockId 取数，不提交客户端 dsId/orgGroupCode/orgCodes', async () => {
    mount(BlockContainer, {
      props: {
        block: { id: 12, blockId: 12, componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 99, period: 'LATEST' }), styleJson: '{}', drillJson: '{}' },
        context: { screenCode: 'SCR_RETAIL', schemaVersion: 2, orgCode: 'O1', empId: '' }
      },
      global: componentGlobals
    });
    await vi.waitFor(() => expect(queryMock).toHaveBeenCalled());
    const body = queryMock.mock.calls[0][0];
    expect(body).toMatchObject({ schemaVersion: 2, screenCode: 'SCR_RETAIL', blockId: 12, period: 'LATEST', contextParams: { orgCode: 'O1', empId: '' } });
    expect(body.dateFrom).toBeUndefined();
    expect(body.dateTo).toBeUndefined();
    expect(body.contextParams.empId).toBe('');
    expect(body.dsId).toBeUndefined();
    expect(body.orgGroupCode).toBeUndefined();
    expect(body.orgCodes).toBeUndefined();
  });

  it('草稿预览统一提交 previewState=draft + blockId，不把草稿 dsId 当作客户端可信身份', async () => {
    mount(BlockContainer, {
      props: {
        block: { id: 77, componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 9002, period: 'LATEST' }), styleJson: '{}', drillJson: '{}' },
        context: { screenCode: 'SCR_DRAFT', schemaVersion: 1, previewState: 'draft', orgCode: '128' }
      },
      global: componentGlobals
    });
    await vi.waitFor(() => expect(queryMock).toHaveBeenCalledTimes(1));
    expect(queryMock.mock.calls[0][0]).toEqual({
      schemaVersion: 1,
      previewState: 'draft',
      screenCode: 'SCR_DRAFT',
      blockId: 77,
      period: 'LATEST',
      contextParams: { orgCode: '128' }
    });
  });

  it.each([
    ['v1', { schemaVersion: 1, screenCode: 'SCR_V1', orgCode: 0, empId: false }, { orgCode: 0, empId: false }],
    ['v2', { schemaVersion: 2, screenCode: 'SCR_V2', orgCode: '', empId: 0 }, { orgCode: '', empId: 0 }]
  ])('BlockContainer %s 端到端保留合法 falsy 上下文', async (_label, context, expectedContext) => {
    const wrapper = mount(BlockContainer, {
      props: {
        block: { id: 12, componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 9002, period: 'LATEST' }),
          styleJson: JSON.stringify({ refreshSec: 0 }), drillJson: '{}' },
        context
      },
      global: componentGlobals
    });
    await vi.waitFor(() => expect(queryMock).toHaveBeenCalledTimes(1));
    expect(queryMock.mock.calls[0][0].contextParams).toEqual(expectedContext);
    wrapper.unmount();
  });

  it.each([
    ['v1', { schemaVersion: 1, screenCode: 'SCR_V1', orgCode: null }],
    ['v2', { schemaVersion: 2, screenCode: 'SCR_V2', orgCode: undefined, empId: null }]
  ])('BlockContainer %s 端到端剔除 null/undefined 上下文', async (_label, context) => {
    const wrapper = mount(BlockContainer, {
      props: {
        block: { id: 12, componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 9002 }),
          styleJson: JSON.stringify({ refreshSec: 0 }), drillJson: '{}' },
        context
      },
      global: componentGlobals
    });
    await vi.waitFor(() => expect(queryMock).toHaveBeenCalledTimes(1));
    expect(queryMock.mock.calls[0][0].contextParams).toEqual({});
    wrapper.unmount();
  });

  it.each([
    ['v1', { schemaVersion: 1, screenCode: 'SCR_V1', blockId: 31, orgCode: false, empId: '' }, { orgCode: false, empId: '' }],
    ['v2', { schemaVersion: 2, screenCode: 'SCR_V2', blockId: 32, orgCode: 0, empId: false }, { orgCode: 0, empId: false }]
  ])('DrillTrend %s 端到端保留合法 falsy 上下文', async (_label, context, expectedContext) => {
    const wrapper = mount(DrillTrend, {
      props: {
        bind: { dsId: 9002 }, context,
        item: { col: 'metric', label: '指标' }, periods: ['LAST_10D']
      },
      global: componentGlobals
    });
    await vi.waitFor(() => expect(queryMock).toHaveBeenCalledTimes(1));
    expect(queryMock.mock.calls[0][0].contextParams).toEqual(expectedContext);
    wrapper.unmount();
  });

  it.each([
    ['v1', { schemaVersion: 1, screenCode: 'SCR_V1', blockId: 31, orgCode: null }],
    ['v2', { schemaVersion: 2, screenCode: 'SCR_V2', blockId: 32, orgCode: undefined, empId: null }]
  ])('DrillTrend %s 端到端剔除 null/undefined 上下文', async (_label, context) => {
    const wrapper = mount(DrillTrend, {
      props: {
        bind: { dsId: 9002 }, context,
        item: { col: 'metric', label: '指标' }, periods: ['LAST_10D']
      },
      global: componentGlobals
    });
    await vi.waitFor(() => expect(queryMock).toHaveBeenCalledTimes(1));
    expect(queryMock.mock.calls[0][0].contextParams).toEqual({});
    wrapper.unmount();
  });

  it('未知版本或不完整 schema2 身份时 Fail Close，不发出兼容 v1 请求', async () => {
    mount(BlockContainer, {
      props: {
        block: { id: 13, componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 99 }), styleJson: '{}', drillJson: '{}' },
        context: { schemaVersion: 3 }
      },
      global: { stubs: { 'el-icon': true, InfoFilled: true }, directives: { loading: {} } }
    });
    await Promise.resolve();
    expect(queryMock).not.toHaveBeenCalled();
  });

  it('运行时响应中的 schemaVersion/blockId 为字符串时 Fail Close，不能用 Number() 把 "2"/"123" 变成 v2 请求', async () => {
    const wrapper = mount(BlockContainer, {
      props: {
        block: { id: '123', componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 99 }), styleJson: '{}', drillJson: '{}' },
        context: { screenCode: 'SCR_RETAIL', schemaVersion: '2' }
      },
      global: { stubs: { 'el-icon': true, InfoFilled: true }, directives: { loading: {} } }
    });
    await Promise.resolve();
    expect(queryMock).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('已拒绝取数');
  });

  it('当前可信发布快照缺失 RPT-43023 时提示需受控迁移或重新发布，不能自动退回 v1', async () => {
    queryMock.mockRejectedValueOnce({ code: 'RPT-43023', message: '当前发布包缺少可信绑定快照' });
    const wrapper = mount(BlockContainer, {
      props: {
        block: { id: 12, componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 99 }), styleJson: '{}', drillJson: '{}' },
        context: { screenCode: 'SCR_RETAIL', schemaVersion: 2 }
      },
      global: { stubs: { 'el-icon': true, InfoFilled: true }, directives: { loading: {} } }
    });
    await vi.waitFor(() => expect(wrapper.text()).toContain('需受控迁移或重新发布'));
    expect(queryMock).toHaveBeenCalledTimes(1);
    expect(queryMock.mock.calls[0][0]).toMatchObject({ schemaVersion: 2, blockId: 12 });
  });

  it('BlockContainer 注册并下发六种新增图表，propValue/columnsMeta 只对扩展类型透传', async () => {
    for (const componentType of [
      'COMBO_CHART', 'FUNNEL_CHART', 'SCATTER_BUBBLE',
      'HEATMAP_MATRIX', 'SUNBURST_CHART', 'SPARKLINE_CARD'
    ]) {
      const payload = componentType === 'SCATTER_BUBBLE'
        ? { columns: ['name', 'x', 'y'], rows: [['A', 1, 2], ['B', 3, 4]], columnsMeta: [] }
        : componentType === 'HEATMAP_MATRIX'
          ? { columns: ['region', 'month', 'value'], rows: [['甲', '一月', 10], ['乙', '一月', 12]], columnsMeta: [] }
          : componentType === 'SUNBURST_CHART'
            ? { columns: ['region', 'branch', 'value'], rows: [['甲', 'A', 10], ['乙', 'B', 12]], columnsMeta: [] }
            : componentType === 'FUNNEL_CHART'
              ? { columns: ['stage', 'value'], rows: [['浏览', 10], ['成交', 2]], columnsMeta: [] }
              : { columns: ['data_date', 'value'], rows: [['2026-08-01', 10], ['2026-08-02', 12]], columnsMeta: [] };
      queryMock.mockResolvedValue(payload);
      const wrapper = mount(BlockContainer, {
        props: {
          block: { id: 31, componentType, bindJson: JSON.stringify({ dsId: 9002 }),
            styleJson: JSON.stringify({ refreshSec: 0 }), drillJson: '{}' },
          propValue: { showLabels: true }, context: { schemaVersion: 1, screenCode: 'SCR_NEW' }
        },
        global: componentGlobals
      });
      await vi.waitFor(() => expect(queryMock).toHaveBeenCalled());
      await vi.waitFor(() => expect(wrapper.find('.v-chart-stub').exists() || wrapper.find('[data-testid="sparkline-value"]').exists(), componentType)
        .toBe(true));
      expect(wrapper.find('.scr-block-empty').exists()).toBe(false);
      wrapper.unmount();
    }
  });

  it('BlockContainer 统一转换金额量级后再下发旧图表，保留接口原始 rows 不变', async () => {
    const payload = {
      columns: ['机构', '存款余额'],
      rows: [['甲行', 100000000]],
      columnsMeta: [{ col: '存款余额', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN' }]
    };
    queryMock.mockResolvedValueOnce(payload);
    const wrapper = mount(BlockContainer, {
      props: {
        block: { id: 91, componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 9002 }), styleJson: '{}', drillJson: '{}' },
        context: { schemaVersion: 1, screenCode: 'SCR_AMOUNT' }
      },
      global: componentGlobals
    });

    await vi.waitFor(() => expect(wrapper.findComponent(MetricCard).props('rows')).toEqual([['甲行', 1]]));
    expect(payload.rows).toEqual([['甲行', 100000000]]);
    wrapper.unmount();
  });

  it('DrillTrend 统一转换钻取指标的金额量级后生成趋势序列', async () => {
    queryMock.mockResolvedValueOnce({
      columns: ['data_date', '存款余额'],
      rows: [['2026-08-01', 10000], ['2026-08-02', 20000]],
      columnsMeta: [{ col: '存款余额', role: 'METRIC', amountScale: 'TEN_THOUSAND_YUAN' }]
    });
    const wrapper = mount(DrillTrend, {
      props: {
        bind: { dsId: 9002 }, context: { schemaVersion: 1, screenCode: 'SCR_AMOUNT', blockId: 92 },
        item: { col: '存款余额', label: '存款余额' }, periods: ['LAST_10D']
      },
      global: componentGlobals
    });

    await vi.waitFor(() => expect(wrapper.findComponent({ name: 'VChart' }).props('option').series[0].data)
      .toEqual([1, 2]));
    wrapper.unmount();
  });
});
