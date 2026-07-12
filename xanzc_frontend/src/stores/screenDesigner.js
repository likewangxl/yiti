import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import {
  createSnapshotStack, record as snapRecord, undo as snapUndo,
  redo as snapRedo, canUndo as snapCanUndo, canRedo as snapCanRedo, deepClone
} from '@/views/screen/designer/utils/snapshotStack';

/**
 * 大屏设计器 store(setup-store 写法,对齐 stores/user.js)。
 * - componentData:组件树(图层顺序=数组顺序,无 zIndex,照搬 DataEase)
 * - curComponent:选中组件的同一响应式引用(属性面板直接 mutate,画布自动联动)
 * - 坐标恒 1920×1080 设计基准
 */
export const useScreenDesignerStore = defineStore('screenDesigner', () => {
  const screenId = ref(null);
  const screenCode = ref('');
  const viewLevel = ref('BRANCH');
  const canvasVersion = ref(0);
  const publishStatus = ref(0);
  const canvasStyle = ref({ schemaVersion: 1, designWidth: 1920, designHeight: 1080,
    background: '#050e2b', adaptor: 'keepProportion', themeOverride: {} });
  // CANVAS_DRAFT_JSON 契约的顶层 schemaVersion(与 canvasStyle.schemaVersion 是两套独立版本号,
  // 分别对应 draft/style 两份 JSON 契约);loadFromEditor 装载、toSavePayload 原样带回(评审 Important-2)。
  const draftSchemaVersion = ref(1);
  const componentData = ref([]);
  const curComponent = ref(null);
  const curIndex = ref(-1);
  const blocks = ref([]);           // 区块行(ChartWidget 按 blockId 关联)
  const scale = ref(0.5);           // 编辑器画布缩放(50%~150% + 适应窗口)
  const dirty = ref(false);

  // 快照栈 + 3 秒防抖(防抖属交互层,故放 store)
  let stack = createSnapshotStack(60);
  let snapshotDisableUntil = 0;
  let debounceTimer = null;
  // stack 是普通闭包变量,Vue 响应式系统追踪不到其内部读写;canUndo/canRedo 的 computed
  // 若只读 stack 本身,首次求值后既无依赖可触发重算,会永久缓存首次结果(评审 Important-1)。
  // 用这个版本号 ref 作为显式响应式依赖:每次栈发生实际变化就自增,逼 computed 重新求值。
  const stackVersion = ref(0);

  const canUndo = computed(() => { void stackVersion.value; return snapCanUndo(stack); });
  const canRedo = computed(() => { void stackVersion.value; return snapCanRedo(stack); });

  function snapshotBody() {
    return { componentData: deepClone(componentData.value), canvasStyle: deepClone(canvasStyle.value) };
  }
  function applySnapshot(snap) {
    if (!snap) return;
    componentData.value = deepClone(snap.componentData);
    canvasStyle.value = deepClone(snap.canvasStyle);
    curComponent.value = null;
    curIndex.value = -1;
  }
  /**
   * 立即记快照(3 秒防抖窗口内跳过,避免连续拖拽污染栈——对齐 snapshot.ts snapshotDisableTime)。
   * 与栈顶快照内容相同则跳过(去重):离散操作(addComponent 等)已各自记过一次,
   * 调用方在同一状态上重复调用不应再压栈,否则 undo 会先弹出一份"无变化"的冗余快照。
   */
  function recordSnapshot() {
    if (Date.now() < snapshotDisableUntil) return;
    const body = snapshotBody();
    const top = stack.data[stack.index];
    if (top && JSON.stringify(top) === JSON.stringify(body)) return;
    stack = snapRecord(stack, body);
    stackVersion.value++;
    dirty.value = true;
  }
  /** 防抖记快照(拖拽/属性连续修改用) */
  function pushSnapshotDebounced() {
    dirty.value = true;
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => recordSnapshot(), 300);
  }
  function undo() {
    applySnapshot(snapUndo(stack));
    stackVersion.value++;
    snapshotDisableUntil = Date.now() + 3000;
  }
  function redo() {
    applySnapshot(snapRedo(stack));
    stackVersion.value++;
    snapshotDisableUntil = Date.now() + 3000;
  }

  // ===== 装载 / 序列化 =====
  function loadFromEditor(resp) {
    screenId.value = resp.screenId;
    screenCode.value = resp.screenCode || '';
    viewLevel.value = resp.viewLevel || 'BRANCH';
    canvasVersion.value = resp.canvasVersion ?? 0;
    publishStatus.value = resp.publishStatus ?? 0;
    blocks.value = resp.blocks || [];
    canvasStyle.value = parse(resp.canvasStyleJson, canvasStyle.value);
    const draft = parse(resp.canvasDraftJson, { components: [] });
    draftSchemaVersion.value = draft.schemaVersion ?? 1;
    componentData.value = Array.isArray(draft.components) ? draft.components : [];
    curComponent.value = null;
    curIndex.value = -1;
    stack = createSnapshotStack(60);
    recordSnapshot();
    dirty.value = false;
  }
  function toSavePayload() {
    return {
      screenId: screenId.value,
      expectedVersion: canvasVersion.value,
      schemaVersion: draftSchemaVersion.value,
      canvasStyle: deepClone(canvasStyle.value),
      // ChartWidget 携带 bind/style/drill(供后端 upsert block);素材组件带 propValue
      components: componentData.value.map(c => deepClone(c))
    };
  }
  function adoptSaveResult(resp) {
    canvasVersion.value = resp.canvasVersion;
    const draft = parse(resp.canvasDraftJson, { components: [] });
    draftSchemaVersion.value = draft.schemaVersion ?? draftSchemaVersion.value;
    componentData.value = Array.isArray(draft.components) ? draft.components : componentData.value;
    dirty.value = false;
  }

  // ===== 组件增删选中 =====
  function addComponent(node) {
    componentData.value.push(node);
    selectComponent(node.id);
    recordSnapshot();
  }
  function removeCurrent() {
    if (curIndex.value < 0) return;
    componentData.value.splice(curIndex.value, 1);
    curComponent.value = null;
    curIndex.value = -1;
    recordSnapshot();
  }
  function selectComponent(id) {
    const i = componentData.value.findIndex(c => c.id === id);
    curIndex.value = i;
    curComponent.value = i >= 0 ? componentData.value[i] : null;
  }
  function setShapeStyle(patch) {
    if (!curComponent.value) return;
    curComponent.value.style = { ...curComponent.value.style, ...patch };
  }

  // ===== 图层(参照 layer.ts:数组内 swap / splice) =====
  function swap(i, j) {
    const arr = componentData.value;
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  function upComponent() {
    if (curIndex.value >= 0 && curIndex.value < componentData.value.length - 1) {
      swap(curIndex.value, curIndex.value + 1); curIndex.value++; recordSnapshot();
    }
  }
  function downComponent() {
    if (curIndex.value > 0) { swap(curIndex.value, curIndex.value - 1); curIndex.value--; recordSnapshot(); }
  }
  function topComponent() {
    if (curIndex.value >= 0 && curIndex.value < componentData.value.length - 1) {
      const [c] = componentData.value.splice(curIndex.value, 1);
      componentData.value.push(c);
      curIndex.value = componentData.value.length - 1; recordSnapshot();
    }
  }
  function bottomComponent() {
    if (curIndex.value > 0) {
      const [c] = componentData.value.splice(curIndex.value, 1);
      componentData.value.unshift(c);
      curIndex.value = 0; recordSnapshot();
    }
  }
  function toggleLock(id) {
    const c = componentData.value.find(x => x.id === id);
    if (c) { c.isLock = !c.isLock; recordSnapshot(); }
  }
  function toggleShow(id) {
    const c = componentData.value.find(x => x.id === id);
    if (c) { c.isShow = c.isShow === false ? true : false; recordSnapshot(); }
  }

  function parse(json, fallback) {
    try { return json ? JSON.parse(json) : fallback; } catch { return fallback; }
  }

  return {
    screenId, screenCode, viewLevel, canvasVersion, publishStatus,
    canvasStyle, draftSchemaVersion, componentData, curComponent, curIndex, blocks, scale, dirty,
    canUndo, canRedo,
    loadFromEditor, toSavePayload, adoptSaveResult,
    addComponent, removeCurrent, selectComponent, setShapeStyle,
    upComponent, downComponent, topComponent, bottomComponent, toggleLock, toggleShow,
    recordSnapshot, pushSnapshotDebounced, undo, redo
  };
});
