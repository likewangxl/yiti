<template>
  <div class="dsn2 scr-surface-host">
    <!-- 顶部工具条 -->
    <div class="dsn2-toolbar">
      <el-select v-model="curId" placeholder="选择大屏" size="small" style="width:220px" @change="loadCanvas">
        <el-option v-for="s in screens" :key="s.id" :label="`${s.screenName} (${s.viewLevel})`" :value="s.id" />
      </el-select>
      <el-button size="small" @click="openCreate">新建</el-button>
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
        <!-- 右栏三态:多选=多选工具条(对齐/分布/成组);单选=组件属性面板;未选=画布全局设置 -->
        <MultiSelectBar v-if="store.curComponents.length > 1" />
        <component v-else-if="store.curComponent" :is="attrOf(store.curComponent.component)" :element="store.curComponent" />
        <CanvasAttr v-else />
      </div>
    </div>
    <!-- 新建大屏:id/screenCode 留空走后端新建分支(服务端生成 SCR_XXXXXXXX 编码) -->
    <el-dialog v-model="createVisible" title="新建大屏" width="420px" :close-on-click-modal="false">
      <el-form label-width="72px" size="small" @submit.prevent>
        <el-form-item label="屏名称"><el-input v-model="createForm.screenName" maxlength="50" placeholder="必填,如:网点经营看板" /></el-form-item>
        <el-form-item label="层级">
          <el-select v-model="createForm.viewLevel">
            <el-option v-for="l in VIEW_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="creating" @click="onCreateSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
// 设计器 V2 组装页——三栏(组件/图层 | 画布 | 属性)+ 顶部工具条(屏选择/undo-redo/缩放/预览/放弃/回滚/保存/发布)
// + 全局快捷键。数据流:loadCanvas 拉编辑器快照灌 store → 画布/面板直接读写 store → 保存/发布把 store
// 序列化回 toSavePayload() 打给后端。旧 admin/Designer.vue 与运行时行/块渲染分支已在渲染层切换任务删除。
import { ref, reactive, nextTick, onMounted, onBeforeUnmount, provide } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listScreens, getScreenCanvas, saveScreen, saveScreenCanvas, publishScreenCanvas,
  discardScreenCanvas, rollbackScreenCanvas, listScreenPublishLogs } from '@/api/screen';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { fitScale } from '@/views/screen/designer/utils/scale';
import { cloneComponentForClipboard, pasteFromClipboard } from '@/views/screen/designer/utils/clipboard';
import { findAttr } from '@/views/screen/designer/widgets';
import CanvasCore from './canvas/CanvasCore.vue';
import ComponentPanel from './panels/ComponentPanel.vue';
import LayerPanel from './panels/LayerPanel.vue';
import CanvasAttr from './panels/CanvasAttr.vue';
import MultiSelectBar from './panels/MultiSelectBar.vue';

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

// ===== 新建大屏(复用既有 ScreenConfigAdminController 的整体 upsert 端点,后端零改动) =====
const VIEW_LEVELS = [
  { value: 'PROVINCE', label: '省分行 (PROVINCE)' },
  { value: 'BRANCH', label: '支行 (BRANCH)' },
  { value: 'PERSON', label: '个人 (PERSON)' }
];
const createVisible = ref(false);
const creating = ref(false);
const createForm = reactive({ screenName: '', viewLevel: 'BRANCH' });
function openCreate() { createForm.screenName = ''; createForm.viewLevel = 'BRANCH'; createVisible.value = true; }
async function onCreateSubmit() {
  const screenName = createForm.screenName.trim();
  if (!screenName) { ElMessage.warning('请填写屏名称'); return; }
  creating.value = true;
  try {
    const id = await saveScreen({ screenName, viewLevel: createForm.viewLevel });
    screens.value = await listScreens();
    curId.value = id;
    await loadCanvas(); // 新屏画布字段为空,loadFromEditor 走缺省分支得到空画布草稿
    createVisible.value = false;
    ElMessage.success('已新建大屏,当前为空画布草稿');
  } finally { creating.value = false; }
}
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
  else if (e.key === 'Delete') { store.removeSelected(); } // 批量口径(单选=长度 1 特例)
  else if (e.key === 'Escape') { store.clearSelection(); }
  else if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key) && store.curComponents.length) {
    e.preventDefault();
    const step = e.shiftKey ? 10 : 1;
    const [dx, dy] = { ArrowUp: [0, -step], ArrowDown: [0, step],
      ArrowLeft: [-step, 0], ArrowRight: [step, 0] }[e.key];
    // nudgeSelected 内部逐个 clampRect 兜底禁移出画布(与拖拽/8点缩放/CommonAttr 统一口径),
    // 多选时批量微移,单选行为与原先一致;连续按键走防抖快照。
    store.nudgeSelected(dx, dy);
  }
}
onMounted(async () => {
  window.addEventListener('keydown', onKey);
  await loadScreens();
  // 首屏加载完自动适应窗口:默认 50% 缩放与中栏尺寸无关,首屏观感差(设计器页面整改 #3)
  await nextTick();
  fitWindow();
});
onBeforeUnmount(() => window.removeEventListener('keydown', onKey));
</script>
<style scoped lang="scss">
@use '@/styles/screen-theme' as theme;
// 高度撑满 DefaultLayout 的 .content--full(路由 meta.fullBleed 去 padding 后恰好铺满),
// 不再写死 calc(100vh - Npx) 猜壳层高度——header 52 + 面包屑 40 + padding 32 曾致超高 64px 整页滚动
.dsn2 { display: flex; flex-direction: column; height: 100%; background: #03081c; }
.scr-surface-host { @include theme.scr-theme-vars; } // 供画布内复用 .scr-* 视觉变量
.dsn2-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  border-bottom: 1px solid rgba(0,229,255,.2); }
.dsn2-toolbar .spacer { flex: 1; }
.dsn2-cols { flex: 1; display: flex; min-height: 0; }
.dsn2-left, .dsn2-right { width: 260px; flex: none; overflow: auto; background: #050e2b;
  border-right: 1px solid rgba(0,229,255,.15); }
.dsn2-right { width: 300px; border-right: none; border-left: 1px solid rgba(0,229,255,.15); } // 右栏 260→300:容纳两列数字输入与图表取数表单
.dsn2-center { flex: 1; min-width: 0; }
</style>
