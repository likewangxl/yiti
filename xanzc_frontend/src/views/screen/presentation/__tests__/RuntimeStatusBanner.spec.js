// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import RuntimeStatusBanner from '../runtime/RuntimeStatusBanner.vue';

describe('RuntimeStatusBanner', () => {
  it('显示逐来源日期、批次说明和统一异常状态，不把不同日期合并成一个日期', () => {
    const wrapper = mount(RuntimeStatusBanner, {
      props: {
        runtime: {
          enabled: true,
          status: 'STALE',
          statusLabel: '过期',
          message: '刷新失败，保留上一完整批次',
          queriedAt: '2026-09-22T09:00:00Z',
          dataDate: '',
          sourceDates: { deposit: '2026-09-20', customers: '2026-09-19' },
          batch: { batchId: 'B-OLD', version: 'V1', status: 'STALE', dataDate: '2026-09-20', explanation: '过期完整批次' },
          slots: {
            deposit: { slot: 'deposit', status: 'STALE', statusLabel: '过期', message: '过期完整批次' },
            customers: { slot: 'customers', status: 'NO_DATA', statusLabel: '缺数', message: '客户当前无有效值' }
          }
        }
      }
    });

    expect(wrapper.get('[data-testid="presentation-runtime-status"]').attributes('data-state')).toBe('STALE');
    expect(wrapper.get('[data-testid="presentation-runtime-batch"]').text()).toContain('B-OLD');
    expect(wrapper.get('[data-testid="presentation-runtime-batch"]').text()).toContain('过期完整批次');
    expect(wrapper.findAll('[data-testid="presentation-runtime-source-date"]').map(node => node.text()))
      .toEqual(expect.arrayContaining(['存款余额：2026-09-20', '营销有效归属客户数：2026-09-19']));
    expect(wrapper.text()).toContain('本次刷新：2026-09-22T09:00:00Z');
    expect(wrapper.text()).toContain('客户当前无有效值');
  });

  it('旧协议或未启用状态模型不渲染状态条', () => {
    const wrapper = mount(RuntimeStatusBanner, { props: { runtime: { enabled: false } } });
    expect(wrapper.find('[data-testid="presentation-runtime-status"]').exists()).toBe(false);
  });
});
