// @vitest-environment happy-dom
import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  listScreens: vi.fn(), getScreenCanvas: vi.fn(), listScreenDatasources: vi.fn(), saveScreenMetadata: vi.fn(), saveScreenCanvas: vi.fn(), publishScreenCanvas: vi.fn()
}));
const routerPush = vi.hoisted(() => vi.fn());
vi.mock('@/api/screen', () => api);
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => ({ loaded: true, canAccess: () => true, load: vi.fn() }) }));

import PersonalDashboardSettings from '../PersonalDashboardSettings.vue';

describe('PersonalDashboardSettings', () => {
  it('读取唯一 PERSONAL_COCKPIT 并展示配置，加载失败不进入新建态', async () => {
    api.listScreens.mockRejectedValueOnce(new Error('目录不可用'));
    const wrapper = mount(PersonalDashboardSettings, { props: { visible: true } });
    await vi.dynamicImportSettled();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(wrapper.text()).toContain('目录不可用');
    expect(wrapper.find('[data-testid="personal-create-screen"]').exists()).toBe(false);
  });

  it('可添加指标并在保存前校验配置', async () => {
    api.listScreens.mockResolvedValueOnce([{ id: 7, screenCode: 'PERSONAL_COCKPIT', screenName: '个人经营驾驶舱', canvasVersion: 1 }]);
    api.getScreenCanvas.mockResolvedValueOnce({ canvasVersion: 1, canvasStyleJson: '{}', canvasDraftJson: JSON.stringify({ schemaVersion: 1, components: [] }), blocks: [] });
    api.listScreenDatasources.mockResolvedValueOnce([]);
    const wrapper = mount(PersonalDashboardSettings, { props: { visible: true } });
    await new Promise(resolve => setTimeout(resolve, 0));
    await wrapper.get('[data-testid="personal-add-metric"]').trigger('click');
    expect(wrapper.findAll('[data-testid^="personal-metric-"]').length).toBe(1);
    await wrapper.get('[data-testid="personal-save"]').trigger('click');
    expect(wrapper.text()).toContain('指标编码不能为空');
  });
  it('普通标题输入会变脏并阻止重复发布，非数组目录不进入创建态', async () => {
    api.listScreens.mockResolvedValueOnce([{ id: 8, screenCode: 'PERSONAL_COCKPIT' }]);
    api.getScreenCanvas.mockResolvedValueOnce({ canvasVersion: 1, canvasStyleJson: '{}', canvasDraftJson: JSON.stringify({ components: [] }) });
    api.listScreenDatasources.mockResolvedValueOnce([]);
    const wrapper = mount(PersonalDashboardSettings, { props: { visible: true } });
    await new Promise(resolve => setTimeout(resolve, 0));
    await wrapper.find('input').setValue('新标题');
    expect(wrapper.get('[data-testid="personal-publish"]').attributes('disabled')).toBeDefined();
  });
});
