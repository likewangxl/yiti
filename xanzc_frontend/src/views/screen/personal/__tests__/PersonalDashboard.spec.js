// @vitest-environment happy-dom
import { describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
vi.mock("vue-echarts", () => ({
  default: {
    name: "VChartStub",
    template: '<div data-testid="chart-stub" />',
    props: ["option"],
  },
}));
import PersonalDashboard from "../PersonalDashboard.vue";

const model = {
  identity: { name: "李经理", orgName: "西安支行" },
  settings: { title: "我的驾驶舱", defaultRange: "7d" },
  metrics: {
    items: [
      {
        metricCode: "a",
        metricName: "客户数",
        unit: "户",
        currentValue: 10,
        targetValue: 20,
        achievementRate: 50,
        dataDate: "2026-09-22",
        orgValue: 100,
        orgUnit: "户",
        orgDataDate: "2026-09-22",
        history: [
          { date: "2026-09-20", value: 8 },
          { date: "2026-09-22", value: 10 },
        ],
      },
      {
        metricCode: "b",
        metricName: "余额",
        unit: "万元",
        currentValue: 0,
        targetValue: 0,
        achievementRate: null,
        history: [],
      },
    ],
  },
  refreshedAt: "2026-09-22 12:00",
};

describe("PersonalDashboard 个人经营驾驶舱", () => {
  it('没有指定颜色时，切换单项保留该指标在全量图中的颜色', async () => {
    const items = model.metrics.items.map((item, index) => ({ ...item, color: '', history: model.metrics.items[0].history, metricCode: String(index) }));
    const wrapper = mount(PersonalDashboard, { props: { model: { ...model, metrics: { items } } } });
    const all = wrapper.findComponent({ name: 'VChartStub' }).props('option');
    await wrapper.findAll('[data-testid="personal-metric"]')[1].trigger('click');
    const one = wrapper.findComponent({ name: 'VChartStub' }).props('option');
    expect(one.series).toHaveLength(1);
    expect(one.series[0].lineStyle.color).toBe(all.series[1].lineStyle.color);
    wrapper.unmount();
  });
  it("只呈现核心指标、趋势、机构占比和目标完成，不呈现旧清单", () => {
    const wrapper = mount(PersonalDashboard, {
      props: { model },
      global: { stubs: { "v-chart": true } },
    });
    expect(wrapper.text()).toContain("我的驾驶舱");
    expect(wrapper.text()).toContain("客户数");
    expect(wrapper.text()).toContain("指标趋势");
    expect(wrapper.text()).toContain("机构占比");
    expect(wrapper.text()).toContain("个人目标完成情况");
    expect(wrapper.text()).not.toContain("今日优先事项");
    expect(wrapper.text()).not.toContain("我的客户");
  });

  it("指标卡、范围和全部恢复发出受控事件", async () => {
    const wrapper = mount(PersonalDashboard, {
      props: { model },
      global: { stubs: { "v-chart": true } },
    });
    await wrapper.get('[data-testid="personal-metric"]').trigger("click");
    await wrapper
      .get(".personal-range-tabs button:nth-child(2)")
      .trigger("click");
    await wrapper.get('[data-action="reset-metric"]').trigger("click");
    expect(wrapper.emitted("metric-change").map((item) => item[0])).toEqual([
      "a",
      "",
    ]);
    expect(wrapper.emitted("range-change")).toEqual([["6m"]]);
  });

  it("保留刷新、全屏、返回动作和无历史空态", async () => {
    const wrapper = mount(PersonalDashboard, {
      props: {
        model: {
          ...model,
          metrics: { items: [{ ...model.metrics.items[1] }] },
        },
      },
      global: { stubs: { "v-chart": true } },
    });
    await wrapper.get('[data-action="refresh"]').trigger("click");
    await wrapper.get('[data-action="fullscreen"]').trigger("click");
    await wrapper.get('[data-action="back"]').trigger("click");
    expect(wrapper.emitted("refresh")).toHaveLength(1);
    expect(wrapper.emitted("fullscreen")).toHaveLength(1);
    expect(wrapper.emitted("back")).toHaveLength(1);
    expect(wrapper.get('[data-testid="personal-trend-empty"]').exists()).toBe(
      true,
    );
  });

  it("错误指标状态不渲染残留条目，目标进度按当前值/目标计算", () => {
    const wrapper = mount(PersonalDashboard, {
      props: {
        model: {
          metrics: {
            status: "error",
            message: "指标服务失败",
            items: [{ metricCode: "stale", metricName: "残留" }],
          },
        },
      },
      global: { stubs: { "v-chart": true } },
    });
    expect(wrapper.find('[data-testid="personal-metric"]').exists()).toBe(
      false,
    );
    expect(wrapper.text()).toContain("指标服务失败");
    wrapper.unmount();
    const progress = mount(PersonalDashboard, {
      props: {
        model: {
          metrics: {
            items: [
              {
                metricCode: "goal",
                metricName: "目标",
                currentValue: 120,
                targetValue: 100,
                achievementRate: 1,
                unit: "户",
              },
            ],
          },
        },
      },
      global: { stubs: { "v-chart": true } },
    });
    expect(progress.text()).toContain("120 / 100 户");
    expect(progress.text()).toContain("120.00%");
  });
});
