// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import ChartWidget from '../Component.vue';

const BlockContainerStub = {
  name: 'BlockContainer',
  props: ['block', 'context', 'propValue'],
  template: '<div data-testid="block-render">{{ block.id }}|{{ context.screenCode }}|{{ context.previewState }}|{{ context.schemaVersion }}</div>'
};

function mountChart(element, mode = 'design') {
  return mount(ChartWidget, {
    props: { element, mode },
    global: { stubs: { BlockContainer: BlockContainerStub } }
  });
}

describe('ChartWidget 设计态数据绑定反馈', () => {
  let store;

  beforeEach(() => {
    setActivePinia(createPinia());
    store = useScreenDesignerStore();
    store.screenCode = 'SCR_DRAFT';
    store.draftSchemaVersion = 1;
    store.blocks = [];
  });

  it('没有 dsId 时显示未绑定数据源', () => {
    const wrapper = mountChart({
      component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: null,
      bindJson: '{}', propValue: {}
    });

    expect(wrapper.text()).toContain('图表(未绑定数据源)');
  });

  it('已选择 dsId 但尚无 blockId 时显示保存后预览提示，不误报未绑定', () => {
    const wrapper = mountChart({
      component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: null,
      bindJson: JSON.stringify({ dsId: 9002 }), propValue: {}
    });

    expect(wrapper.text()).toContain('已选择数据源，保存后预览');
    expect(wrapper.text()).not.toContain('图表(未绑定数据源)');
  });

  it('有效 blockId 且 block 绑定一致时复用 BlockContainer，并传入草稿取数上下文', () => {
    const block = {
      id: 77, componentType: 'METRIC_CARD',
      bindJson: JSON.stringify({ dsId: 9002 }), styleJson: '{}', drillJson: '{}'
    };
    store.blocks = [block];
    const wrapper = mountChart({
      component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 77,
      bindJson: JSON.stringify({ dsId: 9002 }), propValue: { valueField: 'score' }
    });

    const rendered = wrapper.findComponent(BlockContainerStub);
    expect(rendered.exists()).toBe(true);
    expect(rendered.props('block')).toEqual(expect.objectContaining({ id: 77 }));
    expect(rendered.props('context')).toMatchObject({
      screenCode: 'SCR_DRAFT', previewState: 'draft', schemaVersion: 1
    });
  });

  it('NAMED_GROUP 即使草稿根版本为1也使用运行时协议版本2', () => {
    store.orgScopeMode = 'NAMED_GROUP';
    store.draftSchemaVersion = 1;
    const block = {
      id: 78, componentType: 'METRIC_CARD',
      bindJson: JSON.stringify({ dsId: 9002 }), styleJson: '{}', drillJson: '{}'
    };
    store.blocks = [block];
    const wrapper = mountChart({
      component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 78,
      bindJson: JSON.stringify({ dsId: 9002 }), propValue: {}
    });

    expect(wrapper.findComponent(BlockContainerStub).props('context')).toMatchObject({
      screenCode: 'SCR_DRAFT', previewState: 'draft', schemaVersion: 2
    });
  });

  it('LEGACY_CONTEXT 始终使用运行时协议版本1，不受草稿根版本2影响', () => {
    store.orgScopeMode = 'LEGACY_CONTEXT';
    store.draftSchemaVersion = 2;
    const block = {
      id: 79, componentType: 'METRIC_CARD',
      bindJson: JSON.stringify({ dsId: 9002 }), styleJson: '{}', drillJson: '{}'
    };
    store.blocks = [block];
    const wrapper = mountChart({
      component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 79,
      bindJson: JSON.stringify({ dsId: 9002 }), propValue: {}
    });

    expect(wrapper.findComponent(BlockContainerStub).props('context')).toMatchObject({
      screenCode: 'SCR_DRAFT', previewState: 'draft', schemaVersion: 1
    });
  });

  it('现有 block 与组件 bindJson 不一致时按未保存绑定处理，不显示旧数据', () => {
    store.blocks = [{
      id: 77, componentType: 'METRIC_CARD',
      bindJson: JSON.stringify({ dsId: 9002 }), styleJson: '{}', drillJson: '{}'
    }];
    const wrapper = mountChart({
      component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 77,
      bindJson: JSON.stringify({ dsId: 9011 }), propValue: {}
    });

    expect(wrapper.findComponent(BlockContainerStub).exists()).toBe(false);
    expect(wrapper.text()).toContain('已选择数据源，保存后预览');
  });

  it('运行态保留 __block 直传行为，即使组件树没有设计态 blockId', () => {
    const runtimeBlock = {
      id: 88, componentType: 'METRIC_CARD', bindJson: '{}', styleJson: '{}', drillJson: '{}'
    };
    const wrapper = mountChart({
      component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: null,
      bindJson: '{}', __block: runtimeBlock, propValue: {}
    }, 'runtime');

    expect(wrapper.findComponent(BlockContainerStub).props('block')).toEqual(runtimeBlock);
  });
});
