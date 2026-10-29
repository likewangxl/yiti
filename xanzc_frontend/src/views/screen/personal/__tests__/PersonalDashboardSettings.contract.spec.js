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
  it('工作台缩略图逐一对应个人运行页区域，并完整展示四种绑定', async () => {
    await open();
    expect(wrapper.find('[data-testid="personal-config-thumbnail"]').exists()).toBe(true);
    expect(wrapper.find('[data-config-key="page-title"]').exists()).toBe(true);
    expect(wrapper.find('[data-config-key="metrics"]').exists()).toBe(true);
    expect(wrapper.find('[data-config-key="trend"]').exists()).toBe(true);
    expect(wrapper.find('[data-config-key="organization"]').exists()).toBe(true);
    expect(wrapper.find('[data-config-key="target"]').exists()).toBe(true);
    expect(wrapper.find('.personal-config-thumbnail__lower').findAll(':scope > [data-config-key="trend"], :scope > [data-config-key="organization"]')).toHaveLength(2);
    expect(wrapper.find('.personal-config-thumbnail__lower [data-config-key="target"]').classes()).toContain('is-target');
    await wrapper.get('[data-testid="personal-add-metric"]').trigger('click');
    expect(wrapper.text()).toContain('当前值');
    expect(wrapper.text()).toContain('历史趋势');
    expect(wrapper.text()).toContain('机构贡献');
    expect(wrapper.text()).toContain('目标值');
  });

  it('点击标题与运行区域定位配置，标题修改立即同步到缩略图', async () => {
    await open();
    await wrapper.get('[data-config-key="trend"]').trigger('click');
    expect(wrapper.get('[data-config-key="trend"]').attributes('aria-pressed')).toBe('true');
    const title = wrapper.find('input').element;
    await wrapper.find('input').setValue('我的驾驶舱');
    expect(wrapper.get('[data-config-key="page-title"]').text()).toContain('我的驾驶舱');
    await wrapper.get('[data-config-key="page-title"]').trigger('click');
    expect(wrapper.get('[data-config-key="page-title"]').attributes('aria-pressed')).toBe('true');
    expect(title.value).toBe('我的驾驶舱');
  });

  it('指标增删排序保持选中身份，不因索引变化串写配置', async () => {
    await open();
    await wrapper.get('[data-testid="personal-add-metric"]').trigger('click');
    await wrapper.get('[data-testid="personal-add-metric"]').trigger('click');
    const metrics = wrapper.findAll('[data-testid^="personal-metric-"]');
    const stableKey = wrapper.findAll('.personal-config-thumbnail__metric')[1].attributes('data-config-key');
    await metrics[1].get('input').setValue('stable-code');
    await wrapper.get(`.personal-config-thumbnail [data-config-key="${stableKey}"]`).trigger('click');
    expect(wrapper.get(`.personal-config-thumbnail [data-config-key="${stableKey}"]`).attributes('aria-pressed')).toBe('true');
    expect(wrapper.find(`[data-config-key="${stableKey}"]`).exists()).toBe(true);
    expect(wrapper.text()).toContain('stable-code');
  });

  it('指标按钮独立可键盘选中，metricCode修改不改变稳定DOM key，禁用时原生不可操作', async () => {
    await open();
    await wrapper.get('[data-testid="personal-add-metric"]').trigger('click');
    const metricButton = wrapper.find('.personal-config-thumbnail__metric');
    const stableKey = metricButton.attributes('data-config-key');
    expect(metricButton.element.tagName).toBe('BUTTON');
    await metricButton.trigger('keydown.enter');
    expect(metricButton.attributes('aria-pressed')).toBe('true');
    await wrapper.find('[data-testid="personal-metric-0"] input').setValue('changed-code');
    expect(wrapper.get(`.personal-config-thumbnail [data-config-key="${stableKey}"]`).exists()).toBe(true);
    await wrapper.get('[data-config-key="page-title"]').trigger('click');
    expect(wrapper.get('[data-config-key="page-title"]').attributes('aria-pressed')).toBe('true');
    wrapper.vm.accessVisible = true;
    await wrapper.vm.$nextTick();
    expect(wrapper.get(`.personal-config-thumbnail [data-config-key="${stableKey}"]`).attributes('disabled')).toBeDefined();
  });

  it('定位会真正切换右侧可见配置，区域显示对应来源绑定', async () => {
    await open();
    await wrapper.get('[data-testid="personal-add-metric"]').trigger('click');
    const metricStableKey = wrapper.find('.personal-config-thumbnail__metric').attributes('data-config-key');
    const metric = wrapper.find('[data-testid="personal-metric-0"]');
    await metric.findAll('input')[0].setValue('metric-a');
    await metric.findAll('input')[1].setValue('指标甲');
    await wrapper.get(`.personal-config-thumbnail [data-config-key="${metricStableKey}"]`).trigger('click');
    expect(wrapper.findAll('.metric.is-config-selected')).toHaveLength(1);
    expect(wrapper.findAll('.metric').filter((item) => item.isVisible())).toHaveLength(1);
    expect(wrapper.findAll('.binding')).toHaveLength(4);
    await wrapper.get('[data-config-key="trend"]').trigger('click');
    expect(wrapper.get('[data-testid="personal-region-editor-trend"]').isVisible()).toBe(true);
    expect(wrapper.get('[data-testid="personal-region-editor-trend"]').text()).toContain('历史趋势');
    await wrapper.get('[data-config-key="organization"]').trigger('click');
    expect(wrapper.get('[data-testid="personal-region-editor-organization"]').text()).toContain('当前值');
    expect(wrapper.get('[data-testid="personal-region-editor-organization"]').text()).toContain('机构贡献');
    await wrapper.get('[data-config-key="target"]').trigger('click');
    expect(wrapper.get('[data-testid="personal-region-editor-target"]').text()).toContain('目标值');
  });

  it('选中指标保存回读后仍保持该指标可见，不因重建_key回退或串项', async () => {
    await open();
    await wrapper.get('[data-testid="personal-add-metric"]').trigger('click');
    const metricKey = wrapper.find('.personal-config-thumbnail__metric').attributes('data-config-key');
    await wrapper.find('[data-testid="personal-metric-0"] input').setValue('saved-metric');
    await wrapper.findAll('[data-testid="personal-metric-0"] input')[1].setValue('已保存指标');
    await wrapper.get(`.personal-config-thumbnail [data-config-key="${metricKey}"]`).trigger('click');
    expect(wrapper.get('[data-testid="personal-save"]').attributes('disabled')).toBeUndefined();
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.findAll('.metric').filter((item) => item.isVisible())).toHaveLength(1);
    expect(wrapper.find('.metric input').element.value).toBe('saved-metric');
  });

  it('趋势/机构贡献/目标区域复用真实metric绑定表单并可编辑后保存回读', async () => {
    api.listScreenDatasources.mockResolvedValueOnce([{
      id: 10, dsName: '个人测试源', sourceKind: 'WIDE_TABLE', dsType: 'SINGLE', scopeMode: 'SUBJECT', bizLine: 'COMMON',
      configJson: JSON.stringify({ table: 'EMP_INDEX_RESULT', fieldMeta: [
        ...['trend-history', 'organization-current', 'organization-organization', 'target-current', 'target-target'].flatMap(prefix => [{ col: `${prefix}-value`, role: 'METRIC' }, { col: `${prefix}-date`, role: 'DIM' }])
      ] })
    }, {
      id: 11, dsName: '个人历史源', sourceKind: 'WIDE_TABLE', dsType: 'TIMESERIES', scopeMode: 'SUBJECT', bizLine: 'COMMON',
      configJson: JSON.stringify({ table: 'EMP_INDEX_RESULT', fieldMeta: [
        ...['trend-history', 'organization-current', 'organization-organization', 'target-current', 'target-target'].flatMap(prefix => [{ col: `${prefix}-value`, role: 'METRIC' }, { col: `${prefix}-date`, role: 'DIM' }])
      ] })
    }, {
      id: 12, dsName: '机构测试源', sourceKind: 'WIDE_TABLE', dsType: 'SINGLE', scopeMode: 'SUBJECT', bizLine: 'COMMON',
      configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', fieldMeta: [
        ...['trend-history', 'organization-current', 'organization-organization', 'target-current', 'target-target'].flatMap(prefix => [{ col: `${prefix}-value`, role: 'METRIC' }, { col: `${prefix}-date`, role: 'DIM' }])
      ] })
    }]);
    await open();
    await wrapper.get('[data-testid="personal-add-metric"]').trigger('click');
    await wrapper.find('[data-testid="personal-metric-0"] input').setValue('region-metric');
    await wrapper.findAll('[data-testid="personal-metric-0"] input')[1].setValue('区域指标');
    for (const [region, kinds] of [['trend', ['history']], ['organization', ['current', 'organization']], ['target', ['current', 'target']]]) {
      await wrapper.get(`[data-config-key="${region}"]`).trigger('click');
      const visibleMetrics = wrapper.findAll('.metric').filter((item) => item.isVisible());
      expect(visibleMetrics).toHaveLength(1);
      expect(visibleMetrics[0].findAll('.binding')).toHaveLength(kinds.length);
      for (const kind of kinds) {
        const binding = visibleMetrics[0].findAll('.binding').find(item => item.find(`input[list$="-${kind}"]`).exists());
        await binding.find('select').setValue(kind === 'history' ? '11' : (region === 'organization' && kind === 'organization' ? '12' : '10'));
        const inputs = binding.findAll('input');
        await inputs[0].setValue(`${region}-${kind}-value`);
        await inputs[1].setValue(`${region}-${kind}-date`);
        await inputs[2].setValue('YUAN');
      }
    }
    expect(wrapper.get('[data-testid="personal-save"]').attributes('disabled')).toBeUndefined();
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('保存草稿');
    expect(canvas.canvasDraftJson).toContain('target-current-value');
    expect(canvas.canvasDraftJson).toContain('target-target-value');
  });

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
