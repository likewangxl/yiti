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
});
