// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

import PanoramaDataVerification from '../PanoramaDataVerification.vue';

describe('PanoramaDataVerification', () => {
  afterEach(() => vi.restoreAllMocks());

  it('初始状态保持中性，展示结构核验边界且点击后展示逐槽结果', async () => {
    const service = {
      verify: vi.fn().mockResolvedValue({
        overallStatus: 'STRUCTURE_CHECKED',
        overallStatusLabel: '结构核验完成（不代表业务口径正确）',
        disclaimer: '取数结构核验不证明指标业务口径和单位正确',
        results: [{
          slot: 'deposit', label: '存款余额', binding: { datasourceName: '机构宽表', fields: { value: 'amount' } },
          rowCount: 1, coverage: { label: '返回 1 行', missing: [] }, status: 'STRUCTURE_VERIFIED',
          statusLabel: '结构通过', issues: []
        }]
      })
    };
    const wrapper = mount(PanoramaDataVerification, {
      props: {
        screen: { id: 7, screenCode: 'SCR_VERIFY' },
        canvas: { canvasVersion: 3 },
        slotOrder: ['deposit'], bindingState: { deposit: {} }, service
      }
    });
    expect(wrapper.get('[data-testid="panorama-verification-status"]').text()).toContain('尚未验证');
    expect(wrapper.text()).toContain('取数结构核验不证明指标业务口径和单位正确');
    await wrapper.get('[data-testid="verify-saved-draft-data"]').trigger('click');
    await flushPromises();
    expect(service.verify).toHaveBeenCalledWith(expect.objectContaining({
      screen: expect.any(Object), canvas: expect.any(Object), slotOrder: ['deposit']
    }));
    expect(wrapper.get('[data-testid="panorama-verification-table"]').text()).toContain('存款余额');
    expect(wrapper.get('[data-testid="panorama-verification-table"]').text()).toContain('机构宽表');
  });

  it('未保存状态提示先保存，不提供自动写入或发布动作', async () => {
    const service = {
      verify: vi.fn().mockResolvedValue({
        overallStatus: 'UNSAVED',
        overallStatusLabel: '当前绑定未保存',
        message: '当前编辑绑定尚未保存，请先保存后再验证。',
        disclaimer: '取数结构核验不证明指标业务口径和单位正确', results: []
      })
    };
    const wrapper = mount(PanoramaDataVerification, {
      props: { screen: { id: 7 }, canvas: {}, slotOrder: [], bindingState: {}, service }
    });
    await wrapper.get('[data-testid="verify-saved-draft-data"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('请先保存');
    expect(wrapper.find('[data-testid="auto-save-verification"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="publish-verification-result"]').exists()).toBe(false);
  });

  it('切屏/编辑后迟到的旧验证响应不能覆盖下一轮成功结果', async () => {
    let resolveOld;
    const oldResult = {
      overallStatus: 'HAS_GAPS', overallStatusLabel: '旧结果',
      disclaimer: '取数结构核验不证明指标业务口径和单位正确', results: []
    };
    const freshResult = {
      overallStatus: 'STRUCTURE_CHECKED', overallStatusLabel: '新结果',
      disclaimer: '取数结构核验不证明指标业务口径和单位正确', results: []
    };
    const service = {
      verify: vi.fn()
        .mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }))
        .mockResolvedValueOnce(freshResult),
      invalidate: vi.fn(),
      dispose: vi.fn()
    };
    const wrapper = mount(PanoramaDataVerification, {
      props: { screen: { id: 7 }, canvas: { canvasVersion: 3 }, service }
    });
    await wrapper.get('[data-testid="verify-saved-draft-data"]').trigger('click');
    await wrapper.setProps({ canvas: { canvasVersion: 4 } });
    resolveOld(oldResult);
    await flushPromises();
    expect(wrapper.get('[data-testid="panorama-verification-status"]').text()).not.toContain('旧结果');
    await wrapper.get('[data-testid="verify-saved-draft-data"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="panorama-verification-status"]').text()).toContain('新结果');
    expect(wrapper.get('[data-testid="panorama-verification-status"]').text()).not.toContain('旧结果');
  });

  it('绑定对象发生变化时立即清除旧表格并回到中性状态', async () => {
    const service = {
      verify: vi.fn().mockResolvedValue({
        overallStatus: 'STRUCTURE_CHECKED', overallStatusLabel: '结构核验完成',
        disclaimer: '取数结构核验不证明指标业务口径和单位正确',
        results: [{ slot: 'deposit', label: '存款余额', rowCount: 1, coverage: { label: '返回 1 行' }, status: 'STRUCTURE_VERIFIED', statusLabel: '结构通过', issues: [] }]
      }),
      invalidate: vi.fn(), dispose: vi.fn()
    };
    const wrapper = mount(PanoramaDataVerification, {
      props: { screen: { id: 7 }, canvas: { canvasVersion: 3 }, bindingState: { deposit: { dsId: 1 } }, service }
    });
    await wrapper.get('[data-testid="verify-saved-draft-data"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="panorama-verification-table"]').exists()).toBe(true);
    await wrapper.setProps({ bindingState: { deposit: { dsId: 2 } } });
    expect(wrapper.get('[data-testid="panorama-verification-status"]').text()).toContain('尚未验证');
    expect(wrapper.find('[data-testid="panorama-verification-table"]').exists()).toBe(false);
    expect(service.invalidate).toHaveBeenCalled();
  });

  it('绑定展示使用中文字段和单位，身份维度不显示单位未配或内部单位编码', async () => {
    const service = {
      verify: vi.fn().mockResolvedValue({
        overallStatus: 'HAS_GAPS', overallStatusLabel: '存在数据或结构缺口',
        disclaimer: '取数结构核验不证明指标业务口径和单位正确',
        results: [
          {
            slot: 'branches', label: '支行机构',
            binding: {
              datasourceName: '机构宽表',
              fields: { orgCode: 'org_code', orgName: 'org_name' },
              units: {}
            },
            rowCount: 114, coverage: { label: '0/0', available: 0, total: 0, missing: [], authorizedOnly: true },
            status: 'VERIFIED_WITH_WARNINGS', statusLabel: '结构通过，存在缺口', issues: []
          },
          {
            slot: 'deposit', label: '存款余额',
            binding: {
              datasourceName: '机构宽表', fields: { value: 'amount' }, units: { value: 'HUNDRED_MILLION' }
            },
            rowCount: 1, coverage: { label: '返回 1 行' }, status: 'STRUCTURE_VERIFIED', statusLabel: '结构通过', issues: []
          }
        ]
      })
    };
    const wrapper = mount(PanoramaDataVerification, {
      props: { screen: { id: 7 }, canvas: { canvasVersion: 3 }, slotOrder: ['branches', 'deposit'], service }
    });
    await wrapper.get('[data-testid="verify-saved-draft-data"]').trigger('click');
    await flushPromises();
    const table = wrapper.get('[data-testid="panorama-verification-table"]').text();
    expect(table).toContain('机构号=org_code');
    expect(table).toContain('机构名称=org_name');
    expect(table).not.toContain('orgCode=');
    expect(table).not.toContain('单位未配');
    expect(table).toContain('数值单位：亿元');
    expect(table).not.toContain('value:HUNDRED_MILLION');
  });

  it('授权目录为空时不把 0/0 展示成覆盖正确，明确返回行数和覆盖不可核验', async () => {
    const service = {
      verify: vi.fn().mockResolvedValue({
        overallStatus: 'HAS_GAPS', overallStatusLabel: '存在数据或结构缺口',
        disclaimer: '取数结构核验不证明指标业务口径和单位正确',
        results: [{
          slot: 'branches', label: '支行机构',
          binding: { datasourceName: '机构宽表', fields: { orgCode: 'org_code' }, units: {} },
          rowCount: 114,
          coverage: { label: '0/0', available: 0, total: 0, missing: [], authorizedOnly: true },
          status: 'VERIFIED_WITH_WARNINGS', statusLabel: '结构通过，存在缺口', issues: []
        }]
      })
    };
    const wrapper = mount(PanoramaDataVerification, {
      props: { screen: { id: 7 }, canvas: { canvasVersion: 3 }, slotOrder: ['branches'], service }
    });
    await wrapper.get('[data-testid="verify-saved-draft-data"]').trigger('click');
    await flushPromises();
    const table = wrapper.get('[data-testid="panorama-verification-table"]').text();
    expect(table).toContain('授权目录未提供');
    expect(table).toContain('已返回 114 家机构');
    expect(table).toContain('覆盖无法核验');
    expect(table).not.toContain('0/0');
  });
});
