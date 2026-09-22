import { describe, expect, it } from 'vitest';
import {
  DISPLAY_COMPONENT_TYPES,
  normalizeDisplayConfig,
  resolveComponentTitle,
  validateDisplayConfig
} from '../contract/displayContract';
import {
  invalidDuplicateConfig,
  legacyPresentation,
  validDisplayConfig
} from '../contract/fixtures/goldenDisplayConfigs';

describe('经营大屏展示子协议', () => {
  it('旧 presentation 未声明 displaySchemaVersion 时保持未启用且不报错', () => {
    expect(normalizeDisplayConfig(legacyPresentation)).toBeNull();
    expect(validateDisplayConfig(legacyPresentation)).toEqual([]);
  });

  it('保留根 schemaVersion=2 并接受 displaySchemaVersion=1 的七类组件', () => {
    expect(validDisplayConfig.schemaVersion).toBe(2);
    expect(validateDisplayConfig(validDisplayConfig.presentation)).toEqual([]);
    expect(new Set(validDisplayConfig.presentation.display.components.map(item => item.componentType)))
      .toEqual(new Set(DISPLAY_COMPONENT_TYPES));
  });

  it('保留数组顺序、visible=false、小数位0和多指标系列', () => {
    const normalized = normalizeDisplayConfig(validDisplayConfig.presentation);
    expect(normalized.components.map(item => item.componentId)).toEqual([
      'deposit-card', 'completion-card', 'trend-main', 'composition-main',
      'ranking-main', 'province-map', 'detail-main'
    ]);
    expect(normalized.components[0].visible).toBe(false);
    expect(normalized.components[0].format.decimals).toBe(0);
    expect(normalized.components[2].content.series).toHaveLength(2);
  });

  it('按自定义标题、指标名称快照、旧覆盖、模板默认的顺序解析标题', () => {
    const component = validDisplayConfig.presentation.display.components[0];
    expect(resolveComponentTitle(component, {
      metricName: '指标快照', legacyMetricLabel: '旧覆盖', templateTitle: '模板标题'
    })).toBe('自定义存款');
    expect(resolveComponentTitle({ ...component, text: { titleMode: 'AUTO', title: '' } }, {
      metricName: '指标快照', legacyMetricLabel: '旧覆盖', templateTitle: '模板标题'
    })).toBe('指标快照');
    expect(resolveComponentTitle({ ...component, text: { titleMode: 'AUTO', title: '' } }, {
      legacyMetricLabel: '旧覆盖', templateTitle: '模板标题'
    })).toBe('旧覆盖');
  });

  it('拒绝重复组件、单组件重复 block、非法动作、未知字段和单位冲突', () => {
    const issues = validateDisplayConfig(invalidDuplicateConfig);
    expect(issues).toEqual(expect.arrayContaining([
      expect.stringContaining('组件ID重复'),
      expect.stringContaining('blockId重复'),
      expect.stringContaining('交互动作'),
      expect.stringContaining('未知字段'),
      expect.stringContaining('单位类型冲突')
    ]));
  });

  it('允许不同组件共享同一个已发布 blockId', () => {
    const source = structuredClone(validDisplayConfig.presentation);
    source.display.components[1].dataRefs[0].blockId = source.display.components[0].dataRefs[0].blockId;
    expect(validateDisplayConfig(source)).toEqual([]);
  });

  it('拒绝未知展示版本、未知组件和空白自定义标题', () => {
    const source = structuredClone(validDisplayConfig.presentation);
    source.displaySchemaVersion = 9;
    source.display.components[0].componentType = 'SCRIPT_WIDGET';
    source.display.components[0].text = { titleMode: 'CUSTOM', title: '   ' };
    expect(validateDisplayConfig(source)).toEqual(expect.arrayContaining([
      expect.stringContaining('展示协议版本'),
      expect.stringContaining('组件类型'),
      expect.stringContaining('自定义标题')
    ]));
  });

  it('拒绝把AUTO当作来源原始单位以及缺少必需配置对象', () => {
    const source = structuredClone(validDisplayConfig.presentation);
    source.display.components[0].dataRefs[0].unit = 'AUTO';
    delete source.display.components[0].format;
    delete source.display.components[0].interaction;
    expect(validateDisplayConfig(source)).toEqual(expect.arrayContaining([
      expect.stringContaining('来源原始单位不能为AUTO'),
      expect.stringContaining('format不能为空'),
      expect.stringContaining('interaction不能为空')
    ]));
  });

  it('拒绝重复系列身份、不完整系列和非法负数样式', () => {
    const source = structuredClone(validDisplayConfig.presentation);
    const trend = source.display.components.find(item => item.componentType === 'TREND');
    trend.content.series.push({ seriesKey: 'deposit', field: '', label: '', unit: 'AUTO' });
    source.display.components[0].format.negativeStyle = 'SCRIPT';
    expect(validateDisplayConfig(source)).toEqual(expect.arrayContaining([
      expect.stringContaining('series.seriesKey重复'),
      expect.stringContaining('字段和标签不能为空'),
      expect.stringContaining('单位不合法'),
      expect.stringContaining('负数样式')
    ]));
  });
});
