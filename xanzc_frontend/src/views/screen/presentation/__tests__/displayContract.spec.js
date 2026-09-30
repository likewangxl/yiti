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

  it('保存并保留服务端显式机构规则，不把规则降级为前端默认值', () => {
    const normalized = normalizeDisplayConfig(validDisplayConfig.presentation);

    expect(normalized.institutionRules).toEqual({
      allowedOperatingLevels: ['PRIMARY'],
      allowedOrgNatures: ['SECONDARY_BRANCH']
    });
    expect(validateDisplayConfig({
      ...validDisplayConfig.presentation,
      institutionRules: { allowedOperatingLevels: [], allowedOrgNatures: [] }
    })).toEqual(expect.arrayContaining([
      expect.stringContaining('机构层级白名单'),
      expect.stringContaining('机构性质白名单')
    ]));

    const duplicate = structuredClone(validDisplayConfig.presentation);
    duplicate.institutionRules.allowedOperatingLevels = ['PRIMARY', 'primary'];
    expect(validateDisplayConfig(duplicate)).toEqual(expect.arrayContaining([
      expect.stringContaining('不能重复')
    ]));

    const unknown = structuredClone(validDisplayConfig.presentation);
    unknown.institutionRules.extra = 'NO_GUESS';
    expect(validateDisplayConfig(unknown)).toEqual(expect.arrayContaining([
      expect.stringContaining('未知字段')
    ]));
  });

  it('支持名称排除关键词并严格拒绝空值、重复值、非文本和危险文本', () => {
    const source = structuredClone(validDisplayConfig.presentation);
    source.institutionRules.excludedOrgNameKeywords = ['小微支行', '社区支行'];
    expect(normalizeDisplayConfig(source).institutionRules).toEqual({
      allowedOperatingLevels: ['PRIMARY'],
      allowedOrgNatures: ['SECONDARY_BRANCH'],
      excludedOrgNameKeywords: ['小微支行', '社区支行']
    });
    expect(validateDisplayConfig(source)).toEqual([]);

    const invalidValues = [
      [],
      [''],
      ['小微支行', ' 小微支行 '],
      [123],
      ['a'.repeat(41)],
      ['a\nb'],
      ['a<b']
    ];
    for (const excludedOrgNameKeywords of invalidValues) {
      const invalid = structuredClone(validDisplayConfig.presentation);
      invalid.institutionRules.excludedOrgNameKeywords = excludedOrgNameKeywords;
      expect(validateDisplayConfig(invalid)).toEqual(expect.arrayContaining([
        expect.stringContaining('名称排除关键词')
      ]));
    }
  });

  it('保存并保留可选 displayOrgCodes，并严格拒绝空值、非法值和重复编码', () => {
    const source = structuredClone(validDisplayConfig.presentation);
    source.institutionRules.displayOrgCodes = ['ORG-001', 'ORG-002'];

    expect(normalizeDisplayConfig(source).institutionRules).toEqual({
      allowedOperatingLevels: ['PRIMARY'],
      allowedOrgNatures: ['SECONDARY_BRANCH'],
      displayOrgCodes: ['ORG-001', 'ORG-002']
    });
    expect(validateDisplayConfig(source)).toEqual([]);

    const invalidValues = [
      [],
      [''],
      ['ORG-001', ' org-001 '],
      ['ORG-001', 'org-001'],
      [123],
      ['ORG-001', '<ORG-002>'],
      ['ORG-001', 'A\nB'],
      ['ORG 001'],
      ['机构001']
    ];
    for (const displayOrgCodes of invalidValues) {
      const invalid = structuredClone(validDisplayConfig.presentation);
      invalid.institutionRules.displayOrgCodes = displayOrgCodes;
      expect(validateDisplayConfig(invalid)).toEqual(expect.arrayContaining([
        expect.stringContaining('展示机构编码')
      ]));
    }
  });

  it('缺少规则保持旧编辑器兼容；显式部分规则仍 fail-close', () => {
    const missing = structuredClone(validDisplayConfig.presentation);
    delete missing.institutionRules;
    expect(validateDisplayConfig(missing)).toEqual([]);

    const partial = structuredClone(validDisplayConfig.presentation);
    partial.institutionRules.allowedOrgNatures = [];
    expect(validateDisplayConfig(partial)).toEqual(expect.arrayContaining([
      expect.stringContaining('机构性质白名单')
    ]));
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

  it('接受业务结构的中间收入与营业收入明确字段，并拒绝不完整配置', () => {
    const source = structuredClone(validDisplayConfig.presentation);
    const composition = source.display.components.find(item => item.componentType === 'COMPOSITION_TABS');
    composition.content.incomeRatio = { numeratorField: 'intermediaryIncome', denominatorField: 'operatingIncome', unit: 'HUNDRED_MILLION' };
    expect(validateDisplayConfig(source)).toEqual([]);

    delete composition.content.incomeRatio.denominatorField;
    expect(validateDisplayConfig(source)).toEqual(expect.arrayContaining([
      expect.stringContaining('营业收入字段')
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

  it('比较配置放在display.comparisons并严格引用当前LINE_TREND，根comparisons拒绝', () => {
    const source = structuredClone(validDisplayConfig.presentation);
    const trend = source.display.components.find(item => item.componentType === 'TREND');
    trend.dataRefs[0].blockId = 57;
    const card = source.display.components.find(item => item.componentType === 'METRIC_CARD');
    source.display.comparisons = {
      [card.componentId]: { enabled: true, historyBlockId: 57, valueFields: ['value'], dateField: 'data_date', sourceUnit: 'YUAN' }
    };
    expect(validateDisplayConfig(source)).toEqual([]);
    const root = structuredClone(source);
    root.comparisons = root.display.comparisons;
    delete root.display.comparisons;
    expect(validateDisplayConfig(root)).toEqual(expect.arrayContaining([expect.stringContaining('未知字段')])) ;
    const wrongType = structuredClone(source);
    wrongType.display.comparisons[card.componentId].historyBlockId = 999;
    expect(validateDisplayConfig(wrongType)).toEqual(expect.arrayContaining([expect.stringContaining('LINE_TREND')])) ;
  });

  it('比较字段严格限制长度、首尾空格和trim后重复', () => {
    const baseComparison = {
      enabled: true, historyBlockId: 57, valueFields: ['value'], dateField: 'data_date', sourceUnit: 'YUAN'
    };
    const makeSource = patch => {
      const source = structuredClone(validDisplayConfig.presentation);
      source.display.components.find(item => item.componentType === 'TREND').dataRefs[0].blockId = 57;
      source.display.comparisons = { 'deposit-card': { ...baseComparison, ...patch } };
      return source;
    };

    expect(validateDisplayConfig(makeSource({ valueFields: [' value'] }))).toEqual(expect.arrayContaining([
      expect.stringContaining('valueFields')
    ]));
    expect(validateDisplayConfig(makeSource({ valueFields: ['value', ' value'] }))).toEqual(expect.arrayContaining([
      expect.stringContaining('valueFields')
    ]));
    expect(validateDisplayConfig(makeSource({ valueFields: ['a'.repeat(101)] }))).toEqual(expect.arrayContaining([
      expect.stringContaining('valueFields')
    ]));
    expect(validateDisplayConfig(makeSource({ dateField: ' data_date' }))).toEqual(expect.arrayContaining([
      expect.stringContaining('dateField')
    ]));
    expect(validateDisplayConfig(makeSource({ dateField: 'd'.repeat(101) }))).toEqual(expect.arrayContaining([
      expect.stringContaining('dateField')
    ]));
  });

  it('比较多字段只允许金额总览，普通卡和非金额总览即时拒绝', () => {
    const makeSource = (key, sourceUnit) => {
      const source = structuredClone(validDisplayConfig.presentation);
      source.display.components.find(item => item.componentType === 'TREND').dataRefs[0].blockId = 57;
      source.display.comparisons = {
        [key]: { enabled: true, historyBlockId: 57, valueFields: ['value', 'value2'], dateField: 'data_date', sourceUnit }
      };
      return source;
    };

    expect(validateDisplayConfig(makeSource('deposit-card', 'YUAN'))).toEqual(expect.arrayContaining([
      expect.stringContaining('多个valueFields')
    ]));
    expect(validateDisplayConfig(makeSource('overview-deposit', 'PERCENT'))).toEqual(expect.arrayContaining([
      expect.stringContaining('多个valueFields')
    ]));
    expect(validateDisplayConfig(makeSource('overview-deposit', 'HUNDRED_MILLION'))).not.toEqual(expect.arrayContaining([
      expect.stringContaining('多个valueFields')
    ]));
  });

  it('比较源单位必须匹配主 dataRef 类型，完成率只能使用 ratio', () => {
    const makeSource = (key, sourceUnit) => {
      const source = structuredClone(validDisplayConfig.presentation);
      source.display.components.find(item => item.componentType === 'TREND').dataRefs[0].blockId = 57;
      source.display.comparisons = {
        [key]: { enabled: true, historyBlockId: 57, valueFields: ['value'], dateField: 'data_date', sourceUnit }
      };
      return source;
    };

    expect(validateDisplayConfig(makeSource('deposit-card', 'COUNT'))).toEqual(expect.arrayContaining([
      expect.stringContaining('来源单位类型冲突')
    ]));
    const countCard = makeSource('deposit-card', 'RATIO');
    countCard.display.components.find(item => item.componentId === 'deposit-card').dataRefs[0].unit = 'COUNT';
    expect(validateDisplayConfig(countCard)).toEqual(expect.arrayContaining([
      expect.stringContaining('来源单位类型冲突')
    ]));
    expect(validateDisplayConfig(makeSource('completion-card', 'YUAN'))).toEqual(expect.arrayContaining([
      expect.stringContaining('完成率比较源单位必须是百分数或比例')
    ]));
    expect(validateDisplayConfig(makeSource('completion-card', 'COUNT'))).toEqual(expect.arrayContaining([
      expect.stringContaining('来源单位类型冲突'),
      expect.stringContaining('完成率比较源单位必须是百分数或比例')
    ]));
  });
});
