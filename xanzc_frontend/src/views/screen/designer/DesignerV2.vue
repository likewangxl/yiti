<template>
  <div class="dsn2 scr-surface-host">
    <!-- 顶部工具条 -->
    <div class="dsn2-toolbar">
      <el-select v-model="curId" placeholder="选择大屏" size="small" style="width:220px" @change="loadCanvas">
        <el-option v-for="s in screens" :key="s.id" :label="`${s.screenName} (${s.viewLevel})`" :value="s.id" />
      </el-select>
      <span class="spacer" />
      <el-button-group size="small">
        <el-button :disabled="!store.canUndo" @click="store.undo()">撤销</el-button>
        <el-button :disabled="!store.canRedo" @click="store.redo()">重做</el-button>
      </el-button-group>
      <el-slider v-model="scalePct" :min="50" :max="150" :step="10" style="width:120px" @input="onScale" />
      <el-button size="small" @click="fitWindow">适应窗口</el-button>
      <el-button size="small" @click="onPreview">预览草稿</el-button>
      <el-button size="small" @click="onDiscard">放弃草稿</el-button>
      <el-button size="small" @click="onRollback">回滚</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="onSave">保存</el-button>
      <el-button size="small" type="danger" :loading="publishing" @click="onPublish">发布</el-button>
    </div>
    <!-- 三栏 -->
    <div class="dsn2-cols">
      <div class="dsn2-left">
        <el-tabs v-model="leftTab">
          <el-tab-pane label="组件" name="comp"><ComponentPanel /></el-tab-pane>
          <el-tab-pane label="图层" name="layer"><LayerPanel /></el-tab-pane>
        </el-tabs>
      </div>
      <CanvasCore class="dsn2-center" />
      <div class="dsn2-right">
        <component v-if="store.curComponent" :is="attrOf(store.curComponent.component)" :element="store.curComponent" />
        <CanvasAttr v-else />
      </div>
    </div>
  </div>
</template>
<script setup>
// 设计器 V2 组装页——三栏(组件/图层 | 画布 | 属性)+ 顶部工具条(屏选择/undo-redo/缩放/预览/放弃/回滚/保存/发布)
// + 全局快捷键。数据流:loadCanvas 拉编辑器快照灌 store → 画布/面板直接读写 store → 保存/发布把 store
// 序列化回 toSavePayload() 打给后端。旧 admin/Designer.vue 与运行时行/块渲染分支已在渲染层切换任务删除。
import { ref, onMounted, onBeforeUnmount, provide } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listScreens, getScreenCanvas, saveScreenCanvas, publishScreenCanvas,
  discardScreenCanvas, rollbackScreenCanvas, listScreenPublishLogs } from '@/api/screen';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { fitScale, clampRect } from '@/views/screen/designer/utils/scale';
import { cloneComponentForClipboard, pasteFromClipboard } from '@/views/screen/designer/utils/clipboard';
import { findAttr } from '@/views/screen/designer/widgets';
import CanvasCore from './canvas/CanvasCore.vue';
import ComponentPanel from './panels/ComponentPanel.vue';
import LayerPanel from './panels/LayerPanel.vue';
import CanvasAttr from './panels/CanvasAttr.vue';

const store = useScreenDesignerStore();
const screens = ref([]);
const curId = ref(null);
const leftTab = ref('comp');
const saving = ref(false);
const publishing = ref(false);
const scalePct = ref(50);
const clipboard = ref(null); // Ctrl+C/V 本地剪贴板,与 ContextMenu.vue 右键复制粘贴各自独立持有
provide('previewContext', { orgCode: '', empId: '' }); // 设计态预览上下文(空→43010 引导态)

function attrOf(component) { return findAttr(component); }
function onScale(v) { store.scale = v / 100; }
function fitWindow() {
  const wrap = document.querySelector('.dsn2-center');
  if (wrap) { const s = fitScale(wrap.clientWidth - 48, wrap.clientHeight - 48); store.scale = s; scalePct.value = Math.round(s * 100); }
}
async function loadScreens() { screens.value = await listScreens(); if (screens.value[0]) { curId.value = screens.value[0].id; await loadCanvas(); } }
async function loadCanvas() {
  const resp = await getScreenCanvas(curId.value);
  store.loadFromEditor(resp);
}
async function onSave() {
  saving.value = true;
  try {
    const resp = await saveScreenCanvas(store.toSavePayload());
    store.adoptSaveResult(resp);
    ElMessage.success('已保存草稿');
  } catch (e) {
    if (e?.code === 'RPT-43012') {
      // 二次失败(如强制覆盖重发时又撞上新的并发保存)已由 http 拦截器统一 toast,
      // 这里只吞掉避免冒泡成未捕获 rejection,不重复弹错。
      try { await handleSaveConflict(); } catch { /* 已 toast,吞掉 */ }
    }
  } finally { saving.value = false; }
}
/**
 * 43012 乐观锁冲突处理(规格 §9):二次确认给两个选择——
 * 「强制覆盖」:只取服务器最新 canvasVersion,本地组件树/样式原样重发,即以本地改动覆盖服务器
 * (对方修改会丢失,文案需明确警示);「放弃本地并重载」:丢弃本地编辑,拉取服务器最新草稿。
 * 用 ElMessageBox 的 confirm/cancel/close 三态区分(distinguishCancelAndClose),
 * 仅关闭弹框(close)不做任何操作,保留当前草稿编辑态。
 */
async function handleSaveConflict() {
  let action;
  try {
    await ElMessageBox.confirm(
      '画布已被他处保存。“强制覆盖”会用你当前的本地改动覆盖服务器最新版本(对方的修改将丢失);'
      + '“放弃本地并重载”会丢弃你的本地改动,加载服务器最新草稿。',
      '保存冲突',
      { type: 'warning', confirmButtonText: '强制覆盖', cancelButtonText: '放弃本地并重载', distinguishCancelAndClose: true }
    );
    action = 'overwrite';
  } catch (reason) {
    action = reason === 'cancel' ? 'reload' : 'dismiss';
  }
  if (action === 'overwrite') {
    const latest = await getScreenCanvas(store.screenId);
    const resp = await saveScreenCanvas({ ...store.toSavePayload(), expectedVersion: latest.canvasVersion });
    store.adoptSaveResult(resp);
    ElMessage.success('已强制覆盖保存');
  } else if (action === 'reload') {
    await loadCanvas();
    ElMessage.info('已重新加载最新版本');
  }
  // dismiss:用户仅关闭弹框,不做任何操作
}
async function onPublish() {
  try {
    await ElMessageBox.confirm('发布后大屏线上立即生效,确认发布当前草稿?', '发布确认', { type: 'warning' });
  } catch { return; }
  publishing.value = true;
  try {
    await saveScreenCanvas(store.toSavePayload()).then(store.adoptSaveResult); // 先存后发,保证发布最新
    await publishScreenCanvas({ screenId: store.screenId, expectedVersion: store.canvasVersion });
    ElMessage.success('已发布');
  } finally { publishing.value = false; }
}
async function onDiscard() {
  try {
    await ElMessageBox.confirm('放弃当前草稿,恢复到已发布版本?', '放弃草稿', { type: 'warning' });
  } catch { return; }
  await discardScreenCanvas(store.screenId);
  await loadCanvas();
  ElMessage.success('已放弃草稿');
}
async function onRollback() {
  const logs = await listScreenPublishLogs(store.screenId);
  if (!logs.length) { ElMessage.info('暂无发布归档'); return; }
  // 简化:回滚到最近一次归档(完整版可弹选择列表)
  try {
    await ElMessageBox.confirm(`回滚到 ${logs[0].publishedAt} 的发布版本?`, '回滚', { type: 'warning' });
  } catch { return; }
  await rollbackScreenCanvas({ screenId: store.screenId, publishLogId: logs[0].id });
  ElMessage.success('已回滚');
}
function onPreview() {
  window.open(`#/screen/${store.screenCode}?preview=draft`, '_blank');
}

// 快捷键(弹框打开时禁用,对齐 DeShortcutKey.checkDialog)
function onKey(e) {
  if (document.querySelector('.el-overlay')) return; // 有弹框则禁用
  const meta = e.ctrlKey || e.metaKey;
  if (meta && e.key.toLowerCase() === 'z') { e.preventDefault(); store.undo(); }
  else if (meta && e.key.toLowerCase() === 'y') { e.preventDefault(); store.redo(); }
  else if (meta && e.key.toLowerCase() === 's') { e.preventDefault(); onSave(); }
  else if (meta && e.key.toLowerCase() === 'c') {
    // 复制:与 ContextMenu.vue 右键"复制"同一口径(纯函数 cloneComponentForClipboard),
    // 各自持有独立剪贴板变量,不跨入口共享状态。
    if (store.curComponent) { e.preventDefault(); clipboard.value = cloneComponentForClipboard(store.curComponent); }
  }
  else if (meta && e.key.toLowerCase() === 'v') {
    const node = pasteFromClipboard(clipboard.value);
    if (node) { e.preventDefault(); store.addComponent(node); } // addComponent 内部已选中新节点
  }
  else if (e.key === 'Delete') { store.removeCurrent(); }
  else if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key) && store.curComponent) {
    e.preventDefault();
    const step = e.shiftKey ? 10 : 1;
    const s = store.curComponent.style;
    const patch = { ArrowUp: { top: s.top - step }, ArrowDown: { top: s.top + step },
      ArrowLeft: { left: s.left - step }, ArrowRight: { left: s.left + step } }[e.key];
    // clampRect 兜底禁拖出画布——与拖拽/8点缩放/CommonAttr 数值输入统一口径,
    // 否则连续按方向键可把组件顶出 1920×1080 设计基准之外(唯一遗漏的移动路径)。
    store.setShapeStyle(clampRect({ ...s, ...patch }));
    store.pushSnapshotDebounced();
  }
}
onMounted(() => { loadScreens(); window.addEventListener('keydown', onKey); });
onBeforeUnmount(() => window.removeEventListener('keydown', onKey));
</script>
<style scoped lang="scss">
@use '@/styles/screen-theme' as theme;
.dsn2 { display: flex; flex-direction: column; height: calc(100vh - 60px); background: #03081c; }
.scr-surface-host { @include theme.scr-theme-vars; } // 供画布内复用 .scr-* 视觉变量
.dsn2-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  border-bottom: 1px solid rgba(0,229,255,.2); }
.dsn2-toolbar .spacer { flex: 1; }
.dsn2-cols { flex: 1; display: flex; min-height: 0; }
.dsn2-left, .dsn2-right { width: 260px; flex: none; overflow: auto; background: #050e2b;
  border-right: 1px solid rgba(0,229,255,.15); }
.dsn2-right { border-right: none; border-left: 1px solid rgba(0,229,255,.15); }
.dsn2-center { flex: 1; min-width: 0; }
</style>
