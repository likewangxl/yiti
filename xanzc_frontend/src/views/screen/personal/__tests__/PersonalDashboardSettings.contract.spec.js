// @vitest-environment happy-dom
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPersonalSettings, buildPersonalComponents } from '../personalDashboardConfig';
const api = vi.hoisted(() => ({ listScreens: vi.fn(), getScreenCanvas: vi.fn(), listScreenDatasources: vi.fn(), saveScreenMetadata: vi.fn(), saveScreenCanvas: vi.fn(), publishScreenCanvas: vi.fn() }));
const permissions = vi.hoisted(() => ({ loaded: true, canAccess: vi.fn(() => true), load: vi.fn() }));
vi.mock('@/api/screen', () => api);
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => permissions }));
import Settings from '../PersonalDashboardSettings.vue';
let canvas;
let wrapper;
beforeEach(() => {
  vi.clearAllMocks(); permissions.canAccess.mockReturnValue(true);
  canvas = { screenId: 7, canvasVersion: 1, canvasStyleJson: '{}', canvasDraftJson: JSON.stringify({ components: buildPersonalComponents(createPersonalSettings()) }) };
  api.listScreens.mockResolvedValue([{ id: 7, screenCode: 'PERSONAL_COCKPIT' }]);
  api.listScreenDatasources.mockResolvedValue([]);
  api.getScreenCanvas.mockImplementation(async () => structuredClone(canvas));
  api.saveScreenCanvas.mockImplementation(async body => {
    canvas = { ...canvas, canvasVersion: canvas.canvasVersion + 1, canvasDraftJson: JSON.stringify({ components: body.components }) };
    return { canvasVersion: canvas.canvasVersion };
  });
});
afterEach(() => { wrapper?.unmount(); vi.restoreAllMocks(); });
async function open() { wrapper = mount(Settings); await flushPromises(); return wrapper; }
describe('个人配置真实画布保存与权限边界', () => {
  it('新增指标自动分配不同颜色', async () => {
    await open();
    await wrapper.get('[data-testid="personal-add-metric"]').trigger('click');
    await wrapper.get('[data-testid="personal-add-metric"]').trigger('click');
    const colors = wrapper.findAll('input[type="color"]').map(input => input.element.value);
    expect(new Set(colors).size).toBe(2);
  });
  it('创建时携带真实PERSON视角，空白零版本画布可初始化', async () => {
    api.listScreens.mockResolvedValueOnce([]);
    canvas = { screenId: 7, canvasVersion: 0, publishStatus: 0, canvasStyleJson: null, canvasDraftJson: null, blocks: [] };
    api.saveScreenMetadata.mockResolvedValue(7);
    await open(); await wrapper.get('[data-testid="personal-create-screen"]').trigger('click'); await flushPromises();
    expect(api.saveScreenMetadata).toHaveBeenCalledWith(expect.objectContaining({ viewLevel: 'PERSON' }));
    expect(wrapper.find('[data-testid="personal-add-metric"]').exists()).toBe(true);
  });
  it('标题修改保存后独立回读最新版本，恢复可发布状态', async () => {
    await open(); await wrapper.find('input').setValue('新标题');
    expect(wrapper.get('[data-testid="personal-publish"]').attributes('disabled')).toBeDefined();
    await wrapper.get('form').trigger('submit'); await flushPromises();
    expect(api.saveScreenCanvas).toHaveBeenCalledWith(expect.objectContaining({ expectedVersion: 1 }));
    expect(api.getScreenCanvas).toHaveBeenCalledTimes(2);
    expect(wrapper.find('input').element.value).toBe('新标题');
    expect(wrapper.find('[data-testid="personal-error"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="personal-publish"]').attributes('disabled')).toBeUndefined();
  });
  it('保存回读旧版或内容不同不会伪装成功，仍禁止发布', async () => {
    api.saveScreenCanvas.mockResolvedValue({ canvasVersion: 2 });
    await open(); await wrapper.find('input').setValue('新标题');
    await wrapper.get('form').trigger('submit'); await flushPromises();
    expect(wrapper.text()).toContain('回读未确认');
    expect(wrapper.get('[data-testid="personal-publish"]').attributes('disabled')).toBeDefined();
  });
  it('损坏的个人配置节点不能被当空白配置覆盖', async () => {
    canvas.canvasDraftJson = JSON.stringify({ components: [{ id: 'personal-cockpit-settings', component: 'TextLabel', propValue: { personalDashboard: { version: 99 } } }] });
    await open();
    expect(wrapper.text()).toContain('设置节点损坏');
    expect(wrapper.find('form').exists()).toBe(false);
    expect(api.saveScreenCanvas).not.toHaveBeenCalled();
  });
  it('缺少发布资源不能发送发布请求', async () => {
    permissions.canAccess.mockReturnValue(false);
    await open(); await wrapper.get('[data-testid="personal-publish"]').trigger('click'); await flushPromises();
    expect(api.publishScreenCanvas).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('没有发布');
  });
  it('来源目录失败关闭，不把坏目录当空列表允许覆盖绑定', async () => {
    api.listScreenDatasources.mockResolvedValue({ bad: true });
    await open(); expect(wrapper.text()).toContain('数据源目录');
    expect(wrapper.find('form').exists()).toBe(false);
  });
});
