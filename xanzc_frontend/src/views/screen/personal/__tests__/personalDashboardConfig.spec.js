import { describe, expect, it } from 'vitest';
import {
  PERSONAL_SCREEN_CODE,
  buildPersonalComponents,
  createPersonalSettings,
  parseCanvasResponse,
  readPersonalSettings,
  validatePersonalSettings
} from '../personalDashboardConfig';

describe('personalDashboardConfig', () => {
  it('拒绝重复设置节点和非数组指标，不静默变为空配置', () => {
    const node = { id: 'personal-cockpit-settings', component: 'TextLabel', propValue: { personalDashboard: createPersonalSettings() } };
    expect(readPersonalSettings([node, node])).toBeNull();
    expect(readPersonalSettings([{ ...node, propValue: { personalDashboard: { ...createPersonalSettings(), metrics: {} } } }])).toBeNull();
  });
  it('新建零版本空画布可初始化，缺失版本和损坏包不能初始化', () => {
    expect(parseCanvasResponse({ canvasVersion: 0, publishStatus: 0, canvasStyleJson: null, canvasDraftJson: null, blocks: [] })).toMatchObject({ canvasVersion: 0, components: [] });
    expect(parseCanvasResponse({ canvasVersion: null, canvasStyleJson: '{}', canvasDraftJson: '{"components":[]}' })).toBeNull();
    expect(parseCanvasResponse({ canvasVersion: 3, canvasStyleJson: null, canvasDraftJson: null, blocks: [] })).toBeNull();
  });
  it('提供固定编码和默认空配置', () => {
    expect(PERSONAL_SCREEN_CODE).toBe('PERSONAL_COCKPIT');
    expect(createPersonalSettings()).toMatchObject({ version: 1, title: '个人经营驾驶舱', defaultRange: '7d', metrics: [] });
  });
  it('读取个人 TextLabel 配置，损坏时返回 null', () => {
    const settings = createPersonalSettings();
    expect(readPersonalSettings([{ id: 'personal-cockpit-settings', component: 'TextLabel', propValue: { personalDashboard: settings } }])).toEqual(settings);
    expect(readPersonalSettings([{ id: 'personal-cockpit-settings', component: 'TextLabel', propValue: {} }])).toBeNull();
  });
  it('仅替换个人节点，保留无关组件和已有 blockId，绑定生成 TableList', () => {
    const settings = { ...createPersonalSettings(), metrics: [{ metricCode: 'sales/1', metricName: '销售', unit: '元', color: '#fff', bindings: {
      current: { dsId: 2, valueField: 'v', dateField: 'd' }, history: { dsId: 3, valueField: 'hv', dateField: 'hd' }, organization: null, target: null
    } }] };
    const result = buildPersonalComponents(settings, [{ id: 'other', component: 'TextLabel', propValue: { text: 'keep' } }, { id: 'personal-cockpit-settings', component: 'TextLabel', propValue: {} }, { id: 'personal-sales%2F1-current', component: 'ChartWidget', blockId: 91, innerType: 'OLD' }]);
    expect(result.find(x => x.id === 'other')).toBeTruthy();
    expect(result.find(x => x.id === 'personal-cockpit-settings').propValue.personalDashboard).toEqual(settings);
    expect(result.find(x => x.id === 'personal-cockpit-settings').style).toEqual({ top: 0, left: 0, width: 320, height: 180 });
    expect(result.find(x => x.id === 'personal-sales%2F1-current')).toMatchObject({ blockId: 91, innerType: 'TABLE_LIST', bindJson: JSON.stringify({ dsId: 2, period: 'LATEST', fieldMap: { value: 'v', date: 'd' } }) });
    expect(result.find(x => x.id === 'personal-sales%2F1-history').bindJson).toContain('"dsId":3');
  });
  it('校验标题、指标和四种绑定，返回中文错误', () => {
    const errors = validatePersonalSettings({ ...createPersonalSettings(), title: '', metrics: [{ metricCode: '', metricName: '', bindings: { current: { dsId: 1 } } }] });
    expect(errors.join('；')).toMatch(/标题/);
    expect(errors.join('；')).toMatch(/指标编码/);
    expect(errors.join('；')).toMatch(/current/);
  });
  it('解析真实字符串画布响应，坏响应 fail close', () => {
    expect(parseCanvasResponse({ canvasVersion: 2, canvasStyleJson: '{}', canvasDraftJson: '{"components":[]}' }).components).toEqual([]);
    expect(parseCanvasResponse({ canvasVersion: 2, canvasStyleJson: '{}', canvasDraftJson: '{}' })).toBeNull();
  });
  it('空绑定允许缺失态，部分绑定才提示字段错误；专属旧节点删除且保留无关节点', () => {
    const settings = { ...createPersonalSettings(), metrics: [{ metricCode: 'm', metricName: 'M', bindings: { current: {}, history: null, organization: null, target: null } }] };
    expect(validatePersonalSettings(settings)).toEqual([]);
    const components = buildPersonalComponents(settings, [
      { id: 'old', component: 'TextLabel', propValue: { personalMetricCode: 'gone', personalSourceKind: 'current' } },
      { id: 'keep', component: 'TextLabel', propValue: { text: 'x' } }
    ]);
    expect(components.find(item => item.id === 'old')).toBeUndefined();
    expect(components.find(item => item.id === 'keep')).toBeTruthy();
  });
});
