// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const model = {
  identity: { name: '李经理', orgName: '西安高新支行' },
  metrics: {
    status: 'ready',
    message: '个人指标',
    items: [
      {
        metricCode: 'customerCount',
        metricName: '我的客户数',
        currentValue: 0,
        previousValue: null,
        targetValue: 100,
        achievementRate: null,
        unit: '户',
        dataDate: '2026-09-20',
        mom: null
      },
      {
        metricCode: 'deposit',
        metricName: '存款余额',
        currentValue: 1280,
        previousValue: 1200,
        targetValue: null,
        achievementRate: 88.5,
        unit: '万元',
        dataDate: '2026-09-20',
        mom: 6.7
      }
    ]
  },
  priorities: {
    status: 'ready',
    message: '当前返回事项按最近跟进时间展示',
    total: 0,
    items: [{
      id: 'todo-1',
      title: '补充授信材料',
      customerName: '陕西省一家名称很长的客户有限公司',
      typeLabel: '授信',
      nodeLabel: '材料补充',
      urgency: 'overdue',
      deadline: null,
      reason: '客户已提交申请',
      actionLabel: '去处理',
      target: { kind: 'todo', id: 'todo-1', source: 'workflow' }
    }]
  },
  customers: {
    status: 'ready',
    message: '',
    total: 1,
    items: [{
      id: 'customer-1',
      name: '陕西省一家名称很长的客户有限公司',
      isKey: true,
      opened: true,
      lastTouchTime: '2026-09-19 10:20',
      touchRestricted: false,
      target: { kind: 'customer', id: 'customer-1' }
    }]
  },
  progress: {
    status: 'ready',
    message: '',
    total: 1,
    items: [{
      id: 'progress-1',
      title: '授信申请',
      customerName: '示例客户',
      typeLabel: '授信',
      statusLabel: '审批中',
      nodeLabel: '分行审批',
      submittedTime: '2026-09-18 09:00',
      target: { kind: 'progress', id: 'progress-1' }
    }]
  },
  refreshedAt: '2026-09-20 14:30'
};

describe('PersonalDashboard 个人经营驾驶舱', () => {
  it('按契约呈现核心指标，保留 0、空值和返回的完成率，不自行计算', async () => {
    const { default: PersonalDashboard } = await import('../PersonalDashboard.vue');
    const wrapper = mount(PersonalDashboard, { props: { model } });

    expect(wrapper.attributes('aria-label')).toBe('个人经营驾驶舱');
    expect(wrapper.text()).toContain('我的经营驾驶舱');
    expect(wrapper.text()).toContain('李经理');
    expect(wrapper.findAll('[data-testid="personal-metric"]')).toHaveLength(2);
    expect(wrapper.find('[data-testid="personal-metric"]').text()).toContain('0');
    expect(wrapper.findAll('[data-testid="personal-metric"]')[0].find('.personal-metric__previous').exists()).toBe(false);
    expect(wrapper.text()).toContain('完成率 88.50%');
    expect(wrapper.text()).not.toContain('1280 /');
    expect(wrapper.text()).toContain('当前返回事项按最近跟进时间展示');
    expect(wrapper.text()).toContain('陕西省一家名称很长的客户有限公司');
    expect(wrapper.text()).toContain('审批中');
    wrapper.unmount();
  });

  it('四个区域独立呈现空、错误和无权限状态', async () => {
    const { default: PersonalDashboard } = await import('../PersonalDashboard.vue');
    const wrapper = mount(PersonalDashboard, {
      props: {
        model: {
          metrics: { status: 'empty', message: '暂无指标' },
          priorities: { status: 'error', message: '事项服务暂不可用' },
          customers: { status: 'forbidden', message: '无权查看客户' },
          progress: { status: 'ready', message: '', items: [] }
        }
      }
    });

    expect(wrapper.get('[data-testid="personal-metrics-state"]').text()).toContain('暂无指标');
    expect(wrapper.get('[data-testid="personal-priorities-state"]').text()).toContain('事项服务暂不可用');
    expect(wrapper.get('[data-testid="personal-customers-state"]').text()).toContain('无权查看客户');
    expect(wrapper.get('[data-testid="personal-progress-state"]').text()).toContain('暂无可展示数据');
    expect(wrapper.find('[data-testid="personal-priority"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="personal-customer"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('清单条目和查看更多发送带 kind、id 的导航事件，头部按钮发送动作事件', async () => {
    const { default: PersonalDashboard } = await import('../PersonalDashboard.vue');
    const wrapper = mount(PersonalDashboard, { props: { model } });

    await wrapper.get('[data-testid="personal-priority"]').trigger('click');
    await wrapper.get('[data-testid="personal-customer"]').trigger('click');
    await wrapper.get('[data-testid="personal-progress"]').trigger('click');
    await wrapper.get('[data-action="view-todos"]').trigger('click');
    await wrapper.get('[data-action="view-customers"]').trigger('click');
    await wrapper.get('[data-action="view-progress"]').trigger('click');
    await wrapper.get('[data-action="refresh"]').trigger('click');
    await wrapper.get('[data-action="fullscreen"]').trigger('click');
    await wrapper.get('[data-action="back"]').trigger('click');

    expect(wrapper.emitted('navigate')).toEqual([
      [{ kind: 'todo', id: 'todo-1', source: 'workflow' }],
      [{ kind: 'customer', id: 'customer-1' }],
      [{ kind: 'progress', id: 'progress-1' }],
      [{ kind: 'todos' }],
      [{ kind: 'customers' }],
      [{ kind: 'progress' }]
    ]);
    expect(wrapper.emitted('refresh')).toHaveLength(1);
    expect(wrapper.emitted('fullscreen')).toHaveLength(1);
    expect(wrapper.emitted('back')).toHaveLength(1);
    wrapper.unmount();
  });

  it('展示受限触达和加载态时不泄露或伪造数据', async () => {
    const { default: PersonalDashboard } = await import('../PersonalDashboard.vue');
    const wrapper = mount(PersonalDashboard, {
      props: {
        loading: true,
        model: {
          customers: {
            status: 'ready',
            total: 1,
            items: [{ id: 'c-1', name: '客户甲', touchRestricted: true, lastTouchTime: '2026-09-20 10:00' }]
          }
        }
      }
    });

    expect(wrapper.get('[data-testid="personal-loading"]').text()).toContain('正在加载');
    expect(wrapper.get('[data-testid="personal-customer"]').text()).toContain('触达受限');
    expect(wrapper.get('[data-testid="personal-customer"]').text()).toContain('2026-09-20 10:00');
    expect(wrapper.text()).toContain('—');
    wrapper.unmount();
  });

  it('缺少时效或重点标识时保持未知，不从空白或布尔值猜测数字', async () => {
    const { default: PersonalDashboard } = await import('../PersonalDashboard.vue');
    const wrapper = mount(PersonalDashboard, {
      props: {
        model: {
          metrics: {
            status: 'ready',
            items: [{ metricName: '待更新指标', currentValue: ' ', previousValue: false, achievementRate: null, unit: '户' }]
          },
          priorities: {
            status: 'ready',
            items: [{ id: 'todo-unknown', title: '待确认事项', urgency: null }]
          },
          customers: {
            status: 'ready',
            items: [{ id: 'customer-unknown', name: '客户乙', isKey: null, opened: null }]
          }
        }
      }
    });

    expect(wrapper.text()).toContain('时效待确认');
    expect(wrapper.text()).toContain('重点标识未知');
    expect(wrapper.text()).not.toContain('普通客户');
    expect(wrapper.get('[data-testid="personal-metric"]').text()).toContain('—');
    expect(wrapper.get('[data-testid="personal-metric"]').find('.personal-metric__previous').exists()).toBe(false);
    wrapper.unmount();
  });
});
