import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useScreenDesignerStore } from '../screenDesigner';

describe('screenDesigner store', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('loadFromEditor 装载 draft/style/blocks 并可 toSavePayload 往返', () => {
    const store = useScreenDesignerStore();
    store.loadFromEditor({
      screenId: 7, canvasVersion: 3, publishStatus: 0,
      canvasStyleJson: JSON.stringify({ schemaVersion: 1, adaptor: 'keepProportion' }),
      canvasDraftJson: JSON.stringify({ schemaVersion: 1, components: [
        { id: 'w-1', component: 'TextLabel', style: { top: 10, left: 10, width: 100, height: 40 }, propValue: { text: 'A' } }
      ] }),
      blocks: []
    });
    expect(store.componentData.length).toBe(1);
    expect(store.canvasVersion).toBe(3);
    const payload = store.toSavePayload();
    expect(payload.screenId).toBe(7);
    expect(payload.expectedVersion).toBe(3);
    expect(payload.components[0].component).toBe('TextLabel');
  });

  it('addComponent 后可选中并 setShapeStyle', () => {
    const store = useScreenDesignerStore();
    store.addComponent({ id: 'w-2', component: 'RectShape', style: { top: 0, left: 0, width: 50, height: 50 } });
    store.selectComponent('w-2');
    expect(store.curComponent.id).toBe('w-2');
    store.setShapeStyle({ left: 120, top: 60 });
    expect(store.curComponent.style.left).toBe(120);
  });

  it('图层 topComponent 把选中移到数组末尾(zIndex=顺序)', () => {
    const store = useScreenDesignerStore();
    store.addComponent({ id: 'a', component: 'RectShape', style: {} });
    store.addComponent({ id: 'b', component: 'RectShape', style: {} });
    store.selectComponent('a');
    store.topComponent();
    expect(store.componentData[store.componentData.length - 1].id).toBe('a');
  });

  it('undo/redo 还原 componentData', () => {
    const store = useScreenDesignerStore();
    store.recordSnapshot();                       // 空态
    store.addComponent({ id: 'x', component: 'RectShape', style: {} });
    store.recordSnapshot();                       // 有 x 态
    store.undo();
    expect(store.componentData.length).toBe(0);
    store.redo();
    expect(store.componentData.length).toBe(1);
  });

  // Important-1(评审):stack 是普通闭包变量,canUndo/canRedo 若不显式追踪响应式依赖,
  // computed 只会在首次访问时求值一次并永久缓存——即使栈内容已经变化也不会重算。
  it('canUndo/canRedo 随快照栈变化响应式更新(Important-1)', () => {
    const store = useScreenDesignerStore();
    expect(store.canUndo).toBe(false);
    expect(store.canRedo).toBe(false);
    store.recordSnapshot();                       // 第 1 条快照:空态
    store.addComponent({ id: 'z', component: 'RectShape', style: {} }); // 状态变化 + 内部第 2 条快照
    expect(store.canUndo).toBe(true);
    store.undo();
    expect(store.canUndo).toBe(false);
    expect(store.canRedo).toBe(true);
  });

  // Important-2(评审):toSavePayload 缺 CANVAS_DRAFT_JSON 契约的顶层 schemaVersion,
  // loadFromEditor 把 draft.schemaVersion 丢弃,导致往返序列化丢字段。
  it('toSavePayload 输出顶层 schemaVersion,与 loadFromEditor 装载的 draft.schemaVersion 对齐(Important-2)', () => {
    const store = useScreenDesignerStore();
    store.loadFromEditor({
      screenId: 9, canvasVersion: 1, publishStatus: 0,
      canvasStyleJson: JSON.stringify({ schemaVersion: 1, adaptor: 'keep' }),
      canvasDraftJson: JSON.stringify({ schemaVersion: 1, components: [] }),
      blocks: []
    });
    const payload = store.toSavePayload();
    expect(payload.schemaVersion).toBe(1);
  });
});
