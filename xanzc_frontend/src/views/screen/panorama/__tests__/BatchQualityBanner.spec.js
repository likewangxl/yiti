// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import BatchQualityBanner from '../BatchQualityBanner.vue';

const quality = {
  batchId: 'opaque-batch-1', dataDate: '2026-09-10', version: 'V7', status: 'COMPLETE',
  dataClassification: 'TEST',
  calculatedAt: '2026-09-11T01:02:03Z', sourceAsOf: { financial: '2026-09-10', marketing: '2026-09-11' },
  expected: 14, received: 14, expectedSubjects: 4, receivedSubjects: 4,
  missingSubjects: [], missing: [], mixedPeriod: false, selectedComplete: true,
  newerIncomplete: [], message: '授权机构范围内最近完整批次'
};

describe('BatchQualityBanner', () => {
  it('展示数据日期、绩效计算批次时间、查询时间、机构覆盖和来源截至时间', () => {
    const wrapper = mount(BatchQualityBanner, { props: { quality, queriedAt: '2026-09-11T02:03:04Z' } });
    expect(wrapper.get('[data-testid="batch-quality-status"]').text()).toContain('完整批次');
    expect(wrapper.text()).toContain('opaque-batch-1');
    expect(wrapper.text()).toContain('数据日期 2026-09-10');
    expect(wrapper.text()).toContain('绩效计算批次时间 2026-09-11T01:02:03Z');
    expect(wrapper.text()).toContain('数据分类 TEST');
    expect(wrapper.text()).toContain('本次查询/刷新时间 2026-09-11T02:03:04Z');
    expect(wrapper.text()).toContain('机构覆盖 4/4');
    expect(wrapper.text()).toContain('财务 2026-09-10');
    expect(wrapper.text()).toContain('营销 2026-09-11');
  });

  it('STALE 显示过期警告，缺失和混期字段可见', () => {
    const wrapper = mount(BatchQualityBanner, {
      props: {
        quality: {
          ...quality, status: 'STALE', mixedPeriod: true, missing: ['revenue'], missingSubjects: ['B'],
          newerIncomplete: ['2026-09-11'],
          historyCoverage: [
            { dataDate: '2026-09-09', expected: 8, received: 7, expectedSubjects: 4, receivedSubjects: 3, complete: false, missingSubjects: ['B'], missing: ['M_0309'] },
            { dataDate: '2026-09-10', expected: 8, received: 8, expectedSubjects: 4, receivedSubjects: 4, complete: true, missingSubjects: [], missing: [] }
          ]
        }
      }
    });
    expect(wrapper.get('[data-testid="batch-quality-banner"]').classes()).toContain('is-stale');
    expect(wrapper.get('[data-testid="batch-quality-status"]').text()).toContain('过期');
    expect(wrapper.text()).toContain('混合统计期间');
    expect(wrapper.text()).toContain('口径/数据缺项 revenue');
    expect(wrapper.text()).toContain('缺失机构 B');
    expect(wrapper.text()).toContain('较新数据日期不完整 2026-09-11');
    expect(wrapper.text()).toContain('历史不完整日期 2026-09-09');
    expect(wrapper.get('[data-testid="batch-quality-history-details"]')).toBeTruthy();
    expect(wrapper.text()).toContain('历史覆盖（2个数据日期）');
  });

  it('长缺失列表只在折叠详情中展开，摘要不挤满质量横幅', () => {
    const wrapper = mount(BatchQualityBanner, {
      props: {
        quality: {
          ...quality,
          missingSubjects: ['A', 'B', 'C', 'D'],
          missing: ['deposit', 'loan', 'customers', 'revenue'],
          newerIncomplete: ['2026-09-07', '2026-09-08', '2026-09-09', '2026-09-10']
        }
      }
    });
    expect(wrapper.text()).toContain('缺失机构 A、B、C 等4项');
    expect(wrapper.text()).toContain('口径/数据缺项 deposit、loan、customers 等4项');
    expect(wrapper.text()).toContain('展开口径/数据缺项明细（4项）');
    expect(wrapper.text()).toContain('较新数据日期不完整 2026-09-07、2026-09-08、2026-09-09 等4项');
    expect(wrapper.findAll('details')).toHaveLength(3);
  });

  it('无完整批次或前端批次守卫失败时不显示正常批次内容', () => {
    const wrapper = mount(BatchQualityBanner, {
      props: {
        quality: { status: 'NO_COMPLETE_BATCH', batchId: null, message: '没有完整批次' },
        qualityGuard: { status: 'PARTIAL', code: 'QUALITY_MISSING', message: '响应缺少 report Quality' }
      }
    });
    expect(wrapper.get('[data-testid="batch-quality-status"]').text()).toContain('部分批次');
    expect(wrapper.text()).toContain('响应缺少 report Quality');
    expect(wrapper.text()).not.toContain('批次 opaque-batch');
  });
});
