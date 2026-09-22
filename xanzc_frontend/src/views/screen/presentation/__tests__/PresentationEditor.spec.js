// @vitest-environment happy-dom
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PresentationEditor from '../editor/PresentationEditor.vue';
import { createPresentationEditorSession, setComponentVisibility } from '../editor/presentationEditorModel';
import { validDisplayConfig } from '../contract/fixtures/goldenDisplayConfigs';

function mountEditor(session = createPresentationEditorSession(validDisplayConfig.presentation)) {
  return mount(PresentationEditor, {
    props: {
      session,
      blockOptions: [{ blockId: 99, label: '存款余额 · block 99', role: 'PRIMARY',
        metricCode: 'M_DEP', metricName: '存款余额', unit: 'YUAN', dimension: 'ORG' }]
    }
  });
}

describe('PresentationEditor 三栏界面', () => {
  it('同时展示组件列表、即时预览和属性区', () => {
    const wrapper = mountEditor();
    expect(wrapper.find('.presentation-editor__list').exists()).toBe(true);
    expect(wrapper.find('.presentation-editor__preview').text()).toContain('即时预览');
    expect(wrapper.find('.presentation-editor__properties').exists()).toBe(true);
    expect(wrapper.findAll('.presentation-editor__item')).toHaveLength(7);
  });

  it('新增组件产生未绑定状态且通过v-model返回新会话', async () => {
    const wrapper = mountEditor(createPresentationEditorSession({ type: 'CODE', template: 'branch-overview-v1' }));
    await wrapper.find('.presentation-editor__add button').trigger('click');
    const next = wrapper.emitted('update:session')[0][0];
    expect(next.components).toHaveLength(1);
    expect(next.components[0].editorStatus).toBe('DRAFT_UNBOUND');
    expect(next.dirty).toBe(true);
  });

  it('选择组件后修改标题会同步到预览模型', async () => {
    const wrapper = mountEditor();
    await wrapper.find('[data-component-id="completion-card"]').trigger('click');
    const selected = wrapper.emitted('update:session').at(-1)[0];
    await wrapper.setProps({ session: selected });
    const title = wrapper.find('input[placeholder="留空恢复自动标题"]');
    await title.setValue('新的完成率标题');
    const changes = wrapper.emitted('update:session').map(args => args[0]);
    expect(changes.some(changed => changed.components
      .find(item => item.componentId === 'completion-card')?.text?.title === '新的完成率标题')).toBe(true);
  });

  it('绑定已有block后清除DRAFT_UNBOUND，不创建或删除数据源', async () => {
    let session = createPresentationEditorSession({ type: 'CODE', template: 'branch-overview-v1' });
    const wrapper = mountEditor(session);
    await wrapper.find('.presentation-editor__add button').trigger('click');
    session = wrapper.emitted('update:session').at(-1)[0];
    await wrapper.setProps({ session });
    await wrapper.find('.presentation-editor__item').trigger('click');
    session = wrapper.emitted('update:session').at(-1)[0];
    await wrapper.setProps({ session });
    const selects = wrapper.findAll('.presentation-editor__properties select');
    await selects[1].setValue('99');
    const bound = wrapper.emitted('update:session').at(-1)[0];
    expect(bound.components[0].dataRefs[0]).toMatchObject({ blockId: 99, metricCode: 'M_DEP' });
    expect(bound.components[0].editorStatus).toBeUndefined();
    expect(wrapper.emitted('deleteDatasource')).toBeUndefined();
  });

  it('取消按钮只发出cancel事件', async () => {
    const session = createPresentationEditorSession(validDisplayConfig.presentation);
    const dirty = setComponentVisibility(session, 'deposit-card', true);
    const wrapper = mountEditor(dirty);
    await wrapper.find('.presentation-editor__properties footer button').trigger('click');
    expect(wrapper.emitted('cancel')).toHaveLength(1);
  });
});
