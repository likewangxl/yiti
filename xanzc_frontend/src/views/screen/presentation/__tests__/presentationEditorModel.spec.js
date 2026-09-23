import { describe, expect, it } from 'vitest';
import {
  COMPONENT_TYPES,
  addComponent,
  bindComponent,
  cancelEditorSession,
  commitSnapshot,
  createPresentationEditorSession,
  deleteComponent,
  duplicateComponent,
  getComponentEditorStatus,
  moveComponent,
  reorderComponents,
  selectComponent,
  serializeEditorSession,
  setComponentTitle,
  setComponentVisibility,
  updateComponentContent,
  updateComponentFormat,
  updateComponentInteraction,
  updateComponentText
} from '../editor/presentationEditorModel';
import {
  legacyPresentation,
  validDisplayConfig
} from '../contract/fixtures/goldenDisplayConfigs';

function sessionFromValidConfig() {
  return createPresentationEditorSession(validDisplayConfig.presentation);
}

describe('经营大屏展示编辑器状态模型', () => {
  it('从旧 presentation 创建空 display 编辑会话，不修改旧输入', () => {
    const source = structuredClone(legacyPresentation);
    const session = createPresentationEditorSession(source);

    expect(session.presentation).toEqual({
      ...legacyPresentation,
      institutionRules: {
        allowedOperatingLevels: ['PRIMARY', 'SUBORDINATE'],
        allowedOrgNatures: ['LOCAL_BRANCH', 'SECONDARY_BRANCH', 'OUTLET'],
        excludedOrgNameKeywords: ['小微支行', '社区支行']
      },
      displaySchemaVersion: 1,
      display: { components: [] }
    });
    expect(session.presentation).not.toBe(source);
    expect(session.dirty).toBe(false);
    expect(session.selectedComponentId).toBeNull();
    expect(source).toEqual(legacyPresentation);
  });

  it('按模板选择机构展示默认规则，保留已有明确规则', () => {
    const branch = createPresentationEditorSession({ type: 'CODE', template: 'branch-overview-v1' });
    expect(branch.presentation.institutionRules).toEqual({
      allowedOperatingLevels: ['PRIMARY', 'SUBORDINATE'],
      allowedOrgNatures: ['LOCAL_BRANCH', 'SECONDARY_BRANCH', 'OUTLET'],
      excludedOrgNameKeywords: ['小微支行', '社区支行']
    });

    const corporate = createPresentationEditorSession({ type: 'CODE', template: 'corporate-overview-v1' });
    expect(corporate.presentation.institutionRules).toEqual({
      allowedOperatingLevels: ['PRIMARY'],
      allowedOrgNatures: ['SECONDARY_BRANCH']
    });

    const explicitRules = {
      allowedOperatingLevels: ['CUSTOM_LEVEL'],
      allowedOrgNatures: ['CUSTOM_NATURE']
    };
    const existing = createPresentationEditorSession({
      type: 'CODE',
      template: 'branch-overview-v1',
      institutionRules: explicitRules
    });
    expect(existing.presentation.institutionRules).toEqual(explicitRules);
  });

  it('加载 displaySchemaVersion=1 时深拷贝，保留 0、false 和空字符串', () => {
    const source = structuredClone(validDisplayConfig.presentation);
    source.display.components[0].format.decimals = 0;
    source.display.components[0].format.thousandsSeparator = false;
    source.display.components[0].text.subtitle = '';
    const session = createPresentationEditorSession(source);

    expect(session.presentation).toEqual(source);
    expect(session.presentation).not.toBe(source);
    expect(session.presentation.display).not.toBe(source.display);
    expect(session.presentation.display.components[0]).not.toBe(source.display.components[0]);

    const changed = updateComponentFormat(session, 'deposit-card', { decimals: 0, thousandsSeparator: false });
    changed.presentation.display.components[0].text.subtitle = '本地编辑';
    expect(source.display.components[0].text.subtitle).toBe('');
  });

  it('为七类受控组件创建合法初始结构；无 blockId 标记 DRAFT_UNBOUND 且禁止序列化', () => {
    let session = createPresentationEditorSession(legacyPresentation);
    for (const componentType of COMPONENT_TYPES) {
      session = addComponent(session, componentType);
    }

    expect(session.presentation.display.components).toHaveLength(7);
    expect(session.presentation.display.components.map(item => item.componentType))
      .toEqual(COMPONENT_TYPES);
    for (const component of session.presentation.display.components) {
      expect(component.editorStatus).toBe('DRAFT_UNBOUND');
      expect(getComponentEditorStatus(session, component.componentId)).toBe('DRAFT_UNBOUND');
    }
    expect(() => serializeEditorSession(session)).toThrow(/DRAFT_UNBOUND|未绑定/);

    for (const [index, component] of session.presentation.display.components.entries()) {
      session = bindComponent(session, component.componentId, {
        blockId: 800 + index,
        metricCode: `M_${index}`,
        metricName: `指标${index}`,
        unit: 'YUAN',
        role: 'PRIMARY',
        dimension: 'COMMON'
      });
    }
    expect(serializeEditorSession(session).display.components).toHaveLength(7);
  });

  it('创建业务结构编辑默认值时保留存款、贷款页签和中间收入配置字段', () => {
    const session = createPresentationEditorSession(legacyPresentation);
    const next = addComponent(session, 'COMPOSITION_TABS');
    const component = next.presentation.display.components.find(item => item.componentType === 'COMPOSITION_TABS');
    expect(component.content.tabs.map(item => item.tabKey)).toEqual(['deposit', 'loan']);
    expect(component.content.incomeRatio).toEqual({ numeratorField: '', denominatorField: '', unit: 'YUAN' });
  });

  it('选中、复制生成稳定不冲突 ID 并保留内容，删除展示实例不删除共享数据引用', () => {
    let session = sessionFromValidConfig();
    session = selectComponent(session, 'deposit-card');
    expect(session.selectedComponentId).toBe('deposit-card');

    const copied = duplicateComponent(session, 'deposit-card');
    const copy = copied.presentation.display.components.find(item => item.componentId !== 'deposit-card'
      && item.text.title === '自定义存款');
    expect(copy.componentId).toBe('deposit-card-copy-1');
    expect(copy.dataRefs).toEqual(session.presentation.display.components[0].dataRefs);
    expect(copy.visible).toBe(false);

    const deleted = deleteComponent(copied, 'deposit-card');
    expect(deleted.presentation.display.components.some(item => item.componentId === 'deposit-card')).toBe(false);
    expect(deleted.presentation.display.components.find(item => item.componentId === copy.componentId).dataRefs[0].blockId)
      .toBe(11);
  });

  it('显隐、同区域上下移动和显式顺序重排只影响展示组件顺序', () => {
    let session = sessionFromValidConfig();
    session = setComponentVisibility(session, 'deposit-card', false);
    expect(session.presentation.display.components.find(item => item.componentId === 'deposit-card').visible).toBe(false);

    session = moveComponent(session, 'completion-card', 'UP');
    const leftIds = session.presentation.display.components
      .filter(item => item.layoutRegion === 'LEFT').map(item => item.componentId);
    expect(leftIds.slice(0, 2)).toEqual(['completion-card', 'deposit-card']);

    session = reorderComponents(session, 'LEFT', ['deposit-card', 'completion-card', 'trend-main', 'composition-main', 'ranking-main', 'detail-main']);
    const leftComponents = session.presentation.display.components.filter(item => item.layoutRegion === 'LEFT');
    expect(leftComponents.map(item => item.componentId)).toEqual([
      'deposit-card', 'completion-card', 'trend-main', 'composition-main', 'ranking-main', 'detail-main'
    ]);
    expect(leftComponents.map(item => item.order)).toEqual([0, 1, 2, 3, 4, 5]);
    expect(session.presentation.display.components.find(item => item.componentId === 'province-map').order).toBe(0);
  });

  it('更新标题、清除覆盖、正文/格式以及白名单交互，保留合法空值和 decimals=0', () => {
    let session = sessionFromValidConfig();
    session = setComponentTitle(session, 'deposit-card', '经营存款');
    expect(session.presentation.display.components[0].text).toMatchObject({ titleMode: 'CUSTOM', title: '经营存款' });

    session = setComponentTitle(session, 'deposit-card', '');
    expect(session.presentation.display.components[0].text).toMatchObject({ titleMode: 'AUTO', title: '' });
    session = updateComponentText(session, 'deposit-card', { subtitle: '', description: '' });
    session = updateComponentFormat(session, 'deposit-card', { decimals: 0, thousandsSeparator: false });
    session = updateComponentInteraction(session, 'deposit-card', { action: 'OPEN_CITY', target: '' });
    expect(session.presentation.display.components[0]).toMatchObject({
      text: { subtitle: '', description: '' },
      format: { decimals: 0, thousandsSeparator: false },
      interaction: { action: 'OPEN_CITY', target: '' }
    });
    expect(() => updateComponentInteraction(session, 'deposit-card', { action: 'OPEN_URL' }))
      .toThrow(/白名单|不受支持|交互/);
  });

  it('换数据引用时 AUTO 标题随指标名，CUSTOM 标题保留并要求复核', () => {
    let session = sessionFromValidConfig();
    session = bindComponent(session, 'completion-card', {
      blockId: 120,
      metricCode: 'RATE_NEW',
      metricName: '新完成率',
      unit: 'PERCENT',
      role: 'PRIMARY',
      dimension: 'ORG'
    });
    expect(session.presentation.display.components.find(item => item.componentId === 'completion-card').text.title)
      .toBe('新完成率');
    expect(session.reviewRequired).toBe(false);

    session = bindComponent(session, 'deposit-card', {
      blockId: 121,
      metricCode: 'AMOUNT_NEW',
      metricName: '新存款',
      unit: 'YUAN',
      role: 'PRIMARY',
      dimension: 'ORG'
    });
    expect(session.presentation.display.components.find(item => item.componentId === 'deposit-card').text.title)
      .toBe('自定义存款');
    expect(session.reviewRequired).toBe(true);
    expect(session.reviewRequiredComponentIds).toContain('deposit-card');
  });

  it('dirty 跟踪、cancel 恢复加载快照、commitSnapshot 清 dirty', () => {
    const original = sessionFromValidConfig();
    const changed = setComponentVisibility(original, 'deposit-card', true);
    expect(changed.dirty).toBe(true);

    const cancelled = cancelEditorSession(changed);
    expect(cancelled.dirty).toBe(false);
    expect(cancelled.presentation).toEqual(original.presentation);

    const edited = setComponentTitle(cancelled, 'deposit-card', '新的标题');
    expect(edited.dirty).toBe(true);
    const committed = commitSnapshot(edited);
    expect(committed.dirty).toBe(false);
    expect(committed.loadedSnapshot).toEqual(committed.presentation);
  });

  it('序列化前调用 displayContract，非法结构明确失败且合法配置可提交', () => {
    let session = sessionFromValidConfig();
    const serialized = serializeEditorSession(session);
    expect(serialized).toEqual(session.presentation);
    expect(serialized).not.toBe(session.presentation);

    session = updateComponentContent(session, 'deposit-card', { mainField: '' });
    expect(() => serializeEditorSession(session)).toThrow(/无法序列化|mainField/);
  });
});
