// @vitest-environment happy-dom
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PresentationEditor from '../editor/PresentationEditor.vue';
import { createPresentationEditorSession } from '../editor/presentationEditorModel';

const source = {
  canvasStyle: {
    presentation: { type: 'CODE', template: 'branch-overview-v1' },
    metricLabels: { deposit: '迁移存款' }
  },
  components: [
    { id: 'old-deposit', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 41,
      propValue: { bindingKey: 'deposit' } },
    { id: 'old-unknown', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 42,
      propValue: { bindingKey: 'unknown-slot' } }
  ],
  bindSnapshots: { 41: { bind: { metricCode: 'M_DEP', fields: { value: 'deposit_raw' }, units: { value: 'YUAN' } } } }
};

function mountEditor() {
  return mount(PresentationEditor, {
    props: {
      session: createPresentationEditorSession({ type: 'CODE', template: 'branch-overview-v1' }),
      migrationSource: source,
      blockOptions: []
    }
  });
}

describe('PresentationEditor 旧配置迁移入口', () => {
  it('显示只读迁移预览并提供已迁移/缺失/待确认/无法确定状态', () => {
    const wrapper = mountEditor();
    expect(wrapper.find('[data-testid="legacy-migration-preview"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="migration-status-migrated"]').text()).toContain('1');
    expect(wrapper.text()).toContain('不能声明无损完成');
  });

  it('应用只更新 v-model 草稿，不调用保存；取消只发取消事件', async () => {
    const wrapper = mountEditor();
    await wrapper.find('[data-testid="migration-apply"]').trigger('click');
    const applied = wrapper.emitted('update:session')?.at(-1)?.[0];
    expect(applied.presentation.display.components).toHaveLength(1);
    expect(applied.dirty).toBe(true);
    expect(wrapper.emitted('save')).toBeUndefined();

    await wrapper.find('[data-testid="migration-cancel"]').trigger('click');
    expect(wrapper.emitted('migration-cancel')).toHaveLength(1);
    expect(wrapper.emitted('cancel')).toBeUndefined();
    expect(wrapper.emitted('update:session')).toHaveLength(1);
  });
});
