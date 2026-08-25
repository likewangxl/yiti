<template>
  <div class="dsn2 scr-surface-host">
    <!-- 顶部工具条：按屏上下文、编辑历史、草稿和发布动作分组，保留既有按钮与事件。 -->
    <header class="dsn2-toolbar" aria-label="大屏设计器工具栏">
      <div class="dsn2-toolbar__brand" data-testid="dsn2-product">
        <span class="dsn2-brand-mark" aria-hidden="true">▦</span>
        <div class="dsn2-brand-copy">
          <strong>大屏设计器</strong>
          <span>经营分析工作台</span>
        </div>
      </div>

      <div class="dsn2-toolbar__cluster dsn2-toolbar__screen" data-testid="dsn2-screen-context">
        <div class="dsn2-toolbar__context-label">当前大屏</div>
        <el-select v-model="curId" class="dsn2-screen-select" placeholder="选择大屏" size="small"
                   aria-label="选择大屏" @change="loadCanvas">
          <el-option v-for="s in screens" :key="s.id" :label="`${s.screenName}（${viewLevelLabel(s.viewLevel)}）`" :value="s.id" />
        </el-select>
        <span class="dsn2-screen-code">{{ activeScreen?.screenCode || store.screenCode || (curId ? `屏幕 #${curId}` : '未选择屏幕') }}</span>
      </div>

      <div class="dsn2-toolbar__cluster" data-testid="dsn2-screen-actions" aria-label="屏幕范围操作">
        <el-button size="small" @click="openCreate">新建</el-button>
        <el-button size="small" :disabled="!curId" @click="openEditScope">编辑范围</el-button>
        <el-button size="small" :disabled="!curId" @click="openAccessRoleDialog">管理查看角色</el-button>
      </div>

      <div class="dsn2-toolbar__cluster dsn2-toolbar__history" data-testid="dsn2-history-actions" aria-label="编辑历史与缩放">
        <el-button-group size="small">
          <el-button :disabled="!store.canUndo" @click="store.undo()">撤销</el-button>
          <el-button :disabled="!store.canRedo" @click="store.redo()">重做</el-button>
        </el-button-group>
        <div class="dsn2-zoom-control">
          <span class="dsn2-zoom-label">{{ scalePct }}%</span>
          <el-slider v-model="scalePct" :min="50" :max="150" :step="10" aria-label="画布缩放" @input="onScale" />
        </div>
        <el-button size="small" @click="fitWindow">适应窗口</el-button>
      </div>

      <div class="dsn2-toolbar__cluster" data-testid="dsn2-draft-actions" aria-label="草稿操作">
        <el-button size="small" :disabled="!curId" @click="onPreview">预览草稿</el-button>
        <el-button size="small" :disabled="!curId" @click="onDiscard">放弃草稿</el-button>
        <el-button size="small" @click="onRollback">回滚</el-button>
      </div>

      <div class="dsn2-toolbar__cluster dsn2-toolbar__publish" data-testid="dsn2-publish-actions" aria-label="保存发布操作">
        <span class="dirty-state" :class="isDirty ? 'is-dirty' : 'is-saved'" role="status" aria-live="polite"
              data-testid="dsn2-save-state">
          <span class="dirty-state__dot" aria-hidden="true"></span>{{ isDirty ? '有未保存修改' : '已保存' }}
        </span>
        <el-button size="small" type="primary" :loading="saving" @click="onSave">保存</el-button>
        <el-button size="small" type="danger" :loading="publishing" @click="onPublish">发布</el-button>
        <el-button ref="returnButton" size="small" aria-label="返回工作区"
                   :disabled="saving || publishing || exitDialog.saving" @click="onReturn">返回</el-button>
      </div>
    </header>
    <!-- 三栏 -->
    <div class="dsn2-cols">
      <div class="dsn2-left">
        <header class="dsn2-panel-heading" data-testid="dsn2-left-heading">
          <div>
            <span class="dsn2-panel-kicker">工作区</span>
            <h2>组件与图层</h2>
            <p data-testid="dsn2-left-subtitle">拖入组件，组织画布层级</p>
          </div>
          <span class="dsn2-panel-indicator" aria-hidden="true">●</span>
        </header>
        <el-tabs v-model="leftTab">
          <el-tab-pane label="组件" name="comp"><ComponentPanel /></el-tab-pane>
          <el-tab-pane label="图层" name="layer"><LayerPanel /></el-tab-pane>
        </el-tabs>
      </div>
      <CanvasCore class="dsn2-center" />
      <div class="dsn2-right dsn2-inspector">
        <header class="dsn2-panel-heading dsn2-inspector-heading" data-testid="dsn2-right-heading">
          <div>
            <span class="dsn2-panel-kicker">检查器</span>
            <h2>{{ inspectorMeta.title }}</h2>
            <p data-testid="dsn2-right-subtitle">{{ inspectorMeta.subtitle }}</p>
          </div>
          <span class="dsn2-panel-indicator" aria-hidden="true">●</span>
        </header>
        <!-- 右栏三态:多选=多选工具条(对齐/分布/成组);单选=组件属性面板;未选=画布全局设置 -->
        <MultiSelectBar v-if="store.curComponents.length > 1" />
        <component v-else-if="store.curComponent" :key="attrKey(store.curComponent)"
                   :is="attrOf(store.curComponent.component)" :element="store.curComponent" />
        <CanvasAttr v-else />
      </div>
    </div>
    <!-- 新建大屏:id/screenCode 留空走后端新建分支(服务端生成 SCR_XXXXXXXX 编码) -->
    <el-dialog v-model="createVisible" :title="editingScopeId ? '编辑大屏范围' : '新建大屏'" width="420px" :close-on-click-modal="false">
      <el-form label-width="72px" size="small" @submit.prevent>
        <el-form-item label="屏名称"><el-input v-model="createForm.screenName" maxlength="50" placeholder="必填,如:网点经营看板" /></el-form-item>
        <el-form-item label="查看视角">
          <el-select v-model="createForm.viewLevel">
            <el-option v-for="l in VIEW_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务条线" required>
          <el-select v-model="createForm.bizLine">
            <el-option v-for="line in BIZ_LINES" :key="line.value" :label="line.label" :value="line.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="机构范围模式" required>
          <el-select v-model="createForm.orgScopeMode">
            <el-option v-for="mode in ORG_SCOPE_MODES" :key="mode.value" :label="mode.label" :value="mode.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createForm.orgScopeMode === 'NAMED_GROUP'" label="命名机构组" required>
          <el-select v-model="createForm.orgGroupCode" filterable placeholder="选择已启用机构组">
            <el-option v-for="g in orgGroups" :key="g.groupCode" :label="`${g.groupName} (${g.groupCode})`" :value="g.groupCode" />
          </el-select>
          <div class="scope-hint">机构组成员由权限管理员显式维护，前端下拉不是安全边界。</div>
        </el-form-item>
        <el-form-item v-if="editingScopeId" label="当前版本">
          <span>版本 {{ metadataEdit.expectedVersion ?? '-' }}</span>
        </el-form-item>
        <el-form-item v-if="editingScopeId" label="变更原因" required>
          <el-input v-model="metadataEdit.reason" maxlength="500" type="textarea"
                    placeholder="元数据/机构范围变更必须填写原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="creating" @click="onCreateSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色白名单是独立高危操作：不能夹带在屏元数据或画布保存请求里。 -->
    <el-dialog v-model="accessRoleDialog.show" title="管理大屏查看角色" width="520px" :close-on-click-modal="false">
      <el-form label-width="96px" size="small">
        <el-form-item label="允许查看角色" required>
          <el-select v-model="accessRoleDialog.roleCodes" multiple filterable collapse-tags style="width:100%"
                     placeholder="选择屏级查看角色">
            <el-option v-for="role in screenRoles" :key="role.roleCode || role.roleId"
                       :label="`${role.roleChName || role.name || role.roleCode} (${role.roleCode || role.roleId})`"
                       :value="role.roleCode || role.roleId" />
          </el-select>
          <div class="scope-hint">屏级角色与机构组角色必须由同一角色同时命中；前端仅展示配置，不代替后端校验。</div>
        </el-form-item>
        <el-form-item label="变更预览">
          <div class="role-change-preview">
            <span>版本 {{ accessRoleDialog.expectedVersion ?? '-' }}</span>
            <span>新增 {{ accessRoleDiff.added.length }}{{ accessRoleDiff.added.length ? `：${accessRoleDiff.added.join('、')}` : '' }}</span>
            <span>移除 {{ accessRoleDiff.removed.length }}{{ accessRoleDiff.removed.length ? `：${accessRoleDiff.removed.join('、')}` : '' }}</span>
          </div>
        </el-form-item>
        <el-form-item label="变更原因" required>
          <el-input v-model="accessRoleDialog.reason" maxlength="500" type="textarea"
                    placeholder="权限变更必须填写原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="accessRoleDialog.show = false">取消</el-button>
        <el-button size="small" type="danger" :loading="accessRoleDialog.saving" @click="saveAccessRoles">保存角色变更</el-button>
      </template>
    </el-dialog>

    <!-- 放弃草稿同样是独立 CAS 写操作：不以确认框替代审计原因。 -->
    <el-dialog v-model="discardDialog.show" title="放弃草稿" width="420px" :close-on-click-modal="false">
      <el-form label-width="84px" size="small">
        <el-form-item label="当前版本"><span>版本 {{ discardDialog.expectedVersion ?? '-' }}</span></el-form-item>
        <el-form-item label="放弃原因" required>
          <el-input v-model="discardDialog.reason" maxlength="500" type="textarea"
                    placeholder="放弃草稿必须填写原因" />
        </el-form-item>
        <div class="scope-hint">提交后若发生 CAS 冲突，会重新加载服务端最新草稿，不会用旧版本覆盖。</div>
      </el-form>
      <template #footer>
        <el-button size="small" @click="discardDialog.show = false">取消</el-button>
        <el-button size="small" type="danger" :loading="discardDialog.saving" @click="confirmDiscard">确认放弃草稿</el-button>
      </template>
    </el-dialog>

    <!-- 发布与回滚均为独立高危动作：原因不可由普通确认框替代。 -->
    <el-dialog v-model="publishDialog.show" title="发布大屏" width="420px" :close-on-click-modal="false">
      <el-form label-width="84px" size="small">
        <el-form-item label="当前版本"><span>版本 {{ publishDialog.expectedVersion ?? '-' }}</span></el-form-item>
        <el-form-item label="发布原因" required>
          <el-input v-model="publishDialog.reason" maxlength="500" type="textarea"
                    data-testid="screen-publish-reason" placeholder="发布大屏必须填写原因" />
        </el-form-item>
        <div class="scope-hint">确认后先保存当前草稿，再以最新版本提交发布；CAS 冲突会重载，不会用旧版本重试。</div>
      </el-form>
      <template #footer>
        <el-button size="small" @click="publishDialog.show = false">取消</el-button>
        <el-button size="small" type="danger" :loading="publishDialog.saving" @click="confirmPublish">确认发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rollbackDialog.show" title="回滚大屏" width="420px" :close-on-click-modal="false">
      <el-form label-width="84px" size="small">
        <el-form-item label="目标归档" required>
          <el-select v-model="rollbackDialog.publishLogId" filterable clearable style="width:100%"
                     placeholder="请选择要回滚的发布归档" aria-label="目标发布归档"
                     @change="onRollbackArchiveChange">
            <el-option v-for="archive in rollbackDialog.archives" :key="archive.id" :value="archive.id"
                       :label="rollbackArchiveLabel(archive)" />
          </el-select>
          <div class="scope-hint">最多展示最近 10 条归档。必须显式选择目标版本，系统不会默认回滚到最新版本。</div>
        </el-form-item>
        <el-form-item label="归档时间"><span>{{ rollbackDialog.publishedAt || '-' }}</span></el-form-item>
        <el-form-item label="当前版本"><span>版本 {{ rollbackDialog.expectedVersion ?? '-' }}</span></el-form-item>
        <el-form-item label="回滚原因" required>
          <el-input v-model="rollbackDialog.reason" maxlength="500" type="textarea"
                    data-testid="screen-rollback-reason" placeholder="回滚大屏必须填写原因" />
        </el-form-item>
        <div class="scope-hint">提交发生 CAS 冲突时会重新加载服务端最新草稿，不会使用旧版本重试。</div>
      </el-form>
      <template #footer>
        <el-button size="small" @click="rollbackDialog.show = false">取消</el-button>
        <el-button size="small" type="danger" :loading="rollbackDialog.saving" @click="confirmRollback">确认回滚</el-button>
      </template>
    </el-dialog>

    <!-- 独立窗口返回的三分支保护：三个选择都是真实按钮，不把关闭图标暗当第三分支。 -->
    <el-dialog v-model="exitDialog.show" title="未保存修改" width="460px"
               :close-on-click-modal="false" :close-on-press-escape="!exitDialog.saving"
               :show-close="!exitDialog.saving" @opened="focusExitCancel">
      <p class="exit-dialog-copy">当前画布有未保存修改。你可以保存后关闭，或放弃这些修改后关闭。</p>
      <template #footer>
        <el-button ref="exitCancelButton" size="small" :disabled="exitDialog.saving" @click="cancelReturn">取消</el-button>
        <el-button size="small" :disabled="exitDialog.saving" @click="discardAndClose">放弃并关闭</el-button>
        <el-button size="small" type="primary" :loading="exitDialog.saving"
                   :disabled="exitDialog.saving" @click="saveAndClose">保存并关闭</el-button>
      </template>
    </el-dialog>

    <div class="screen-security-hint">草稿预览仍须通过屏白名单、机构组、同一角色及画布读取门禁；SYS_ADMIN 不存在大屏业务旁路。</div>
  </div>
</template>
<script setup>
// 设计器 V2 组装页——三栏(组件/图层 | 画布 | 属性)+ 顶部工具条(屏选择/undo-redo/缩放/预览/放弃/回滚/保存/发布)
// + 全局快捷键。数据流:loadCanvas 拉编辑器快照灌 store → 画布/面板直接读写 store → 保存/发布把 store
// 序列化回 toSavePayload() 打给后端。旧 admin/Designer.vue 与运行时行/块渲染分支已在渲染层切换任务删除。
import { computed, ref, reactive, nextTick, onMounted, onBeforeUnmount, provide, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listScreens, getScreenCanvas, saveScreenMetadata, saveScreenCanvas, publishScreenCanvas,
  discardScreenCanvas, rollbackScreenCanvas, listScreenPublishLogs, listOrgGroups, listScreenRoles,
  listOrgProfiles, listScreenAccessRoles, saveScreenAccessRoles, listScreenMapRegionMetrics } from '@/api/screen';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { fitScale } from '@/views/screen/designer/utils/scale';
import { cloneComponentForClipboard, pasteFromClipboard } from '@/views/screen/designer/utils/clipboard';
import { closeWindowOrFallback } from '@/views/screen/designer/utils/closeWindow';
import { chartMetas, findAttr } from '@/views/screen/designer/widgets';
import {
  BIZ_LINES, ORG_SCOPE_MODES, VIEW_LEVELS, normalizeScreenScope,
  validateScreenScope, viewLevelLabel, filterActiveOrgGroups, filterReportScreenOrgGroups, diffCodes
} from '@/utils/screenScope';
import CanvasCore from './canvas/CanvasCore.vue';
import ComponentPanel from './panels/ComponentPanel.vue';
import LayerPanel from './panels/LayerPanel.vue';
import CanvasAttr from './panels/CanvasAttr.vue';
import MultiSelectBar from './panels/MultiSelectBar.vue';

const store = useScreenDesignerStore();
const router = useRouter();
const designProfiles = ref([]);
const allDesignProfiles = ref([]);
const designRegionMetrics = ref([]);
// CanvasCore 下的 MapCenter 通过同一响应式引用读取已配置画像；运行时不会继承此注入。
provide('screenProfiles', designProfiles);
provide('screenRegionMetrics', designRegionMetrics);
const screens = ref([]);
const curId = ref(null);
const leftTab = ref('comp');
const saving = ref(false);
const publishing = ref(false);
const isDirty = ref(false);
const dirtyBaseline = ref(null);
const returnButton = ref(null);
const exitCancelButton = ref(null);
const scalePct = ref(50);
const clipboard = ref(null); // Ctrl+C/V 本地剪贴板,与 ContextMenu.vue 右键复制粘贴各自独立持有
// 设计态按草稿绑定 dsId 查询，明确使用历史 v1 协议；screenCode 随当前加载的屏响应式更新。
const previewContext = reactive({ schemaVersion: 1, screenCode: '', orgCode: '', empId: '' });
provide('previewContext', previewContext);
watch(() => store.screenCode, screenCode => {
  previewContext.screenCode = screenCode || '';
}, { immediate: true });

const activeScreen = computed(() => screens.value.find(screen => screen.id === curId.value) || null);
const inspectorMeta = computed(() => {
  if (store.curComponents.length > 1) {
    return { title: '批量编辑', subtitle: `已选 ${store.curComponents.length} 个组件` };
  }
  if (store.curComponent) {
    if (store.curComponent.component === 'ChartWidget') {
      const innerType = store.curComponent.innerType || 'UNKNOWN';
      const chartMeta = chartMetas.find(meta => meta?.innerType === innerType);
      return { title: '组件属性', subtitle: `${chartMeta?.label || '图表'}（${innerType}）` };
    }
    return { title: '组件属性', subtitle: store.curComponent.component || '当前选中组件' };
  }
  return { title: '画布设置', subtitle: '配置画布尺寸与主题' };
});

function attrOf(component) { return findAttr(component); }

// 同类型属性面板需要随选中节点切换重建；无 id 的历史节点用对象身份兜底，避免复用旧面板状态。
const anonymousAttrKeys = new WeakMap();
let anonymousAttrKeySeq = 0;
function attrKey(element) {
  if (!element || typeof element !== 'object') return 'attr:none';
  if (element.id !== undefined && element.id !== null && element.id !== '') {
    return `attr:${element.component || ''}:${element.id}`;
  }
  if (!anonymousAttrKeys.has(element)) {
    anonymousAttrKeys.set(element, `attr:anonymous:${++anonymousAttrKeySeq}`);
  }
  return anonymousAttrKeys.get(element);
}
function onScale(v) { store.scale = v / 100; }
function fitWindow() {
  const wrap = document.querySelector('.dsn2-center');
  if (wrap) { const s = fitScale(wrap.clientWidth - 48, wrap.clientHeight - 48); store.scale = s; scalePct.value = Math.round(s * 100); }
}
async function loadScreens() { screens.value = await listScreens(); if (screens.value[0]) { curId.value = screens.value[0].id; await loadCanvas(); } }

// ===== 屏元数据/范围（创建与既有屏更新走独立端点；这里绝不夹带 blocks/角色） =====
const createVisible = ref(false);
const creating = ref(false);
const editingScopeId = ref(null);
const orgGroups = ref([]);
const screenRoles = ref([]);
const createForm = reactive({ screenName: '', viewLevel: 'BRANCH', bizLine: '',
  orgScopeMode: '', orgGroupCode: '' });
const metadataEdit = reactive({ expectedVersion: null, reason: '' });
const discardDialog = reactive({ show: false, saving: false, expectedVersion: null, reason: '' });
const publishDialog = reactive({ show: false, saving: false, expectedVersion: null, reason: '' });
const rollbackDialog = reactive({
  show: false, saving: false, publishLogId: null, publishedAt: '', expectedVersion: null, reason: '', archives: []
});
const accessRoleDialog = reactive({
  show: false, saving: false, roleCodes: [], initialRoleCodes: [], reason: '', expectedVersion: null
});
const exitDialog = reactive({ show: false, saving: false });
const accessRoleDiff = computed(() => diffCodes(accessRoleDialog.initialRoleCodes, accessRoleDialog.roleCodes));

/**
 * dirty 只以画布保存契约为准，不把选区、缩放、面板 tab 等纯视图状态误判为未保存。
 * expectedVersion 是 CAS 快照而非用户编辑内容，同样不进基线。
 */
function currentDraftSignature() {
  const payload = store.toSavePayload();
  return JSON.stringify({
    screenId: payload.screenId,
    canvasStyle: payload.canvasStyle,
    components: payload.components
  });
}
function resetDirtyBaseline() {
  dirtyBaseline.value = currentDraftSignature();
  isDirty.value = false;
}
watch(
  [() => store.canvasStyle, () => store.componentData],
  () => {
    isDirty.value = dirtyBaseline.value !== null && currentDraftSignature() !== dirtyBaseline.value;
  },
  { deep: true, flush: 'sync' }
);

function groupMembers(group) {
  return group?.memberOrgCodes || group?.orgCodes || (group?.members || []).map(item => item.orgCode || item);
}
function refreshDesignProfiles() {
  const screen = screens.value.find(item => item.id === curId.value) || {};
  const scope = normalizeScreenScope(screen);
  if (scope.orgScopeMode !== 'NAMED_GROUP') {
    designProfiles.value = [...allDesignProfiles.value];
    return;
  }
  const group = orgGroups.value.find(item => item.groupCode === scope.orgGroupCode);
  const members = groupMembers(group);
  // 设计态也 Fail Close：无法确认屏绑定组成员时不展示其他机构的假点位。
  designProfiles.value = group && Array.isArray(members)
    ? allDesignProfiles.value.filter(profile => members.map(String).includes(String(profile.orgCode)))
    : [];
}

async function loadScopeChoices() {
  // 兼容旧后端/既有冒烟测试的 mock：候选列表失败不阻断弹框，保存仍交由后端校验。
  try {
    if (typeof listOrgGroups === 'function') {
      const groups = await listOrgGroups({ status: 'ACTIVE' });
      const rows = Array.isArray(groups) ? groups : (groups?.records || []);
      orgGroups.value = filterActiveOrgGroups(filterReportScreenOrgGroups(rows));
    }
  } catch { orgGroups.value = []; }
  try {
    if (typeof listScreenRoles === 'function') {
      const roles = await listScreenRoles({ recordStatus: 0 });
      screenRoles.value = Array.isArray(roles) ? roles : (roles?.records || []);
    }
  } catch { screenRoles.value = []; }
  try {
    if (typeof listOrgProfiles === 'function') {
      const profiles = await listOrgProfiles({});
      allDesignProfiles.value = Array.isArray(profiles) ? profiles : (profiles?.records || []);
      refreshDesignProfiles();
    }
  } catch {
    allDesignProfiles.value = [];
    designProfiles.value = [];
  }
}
function fillCreateForm(value = {}) {
  metadataEdit.expectedVersion = null;
  metadataEdit.reason = '';
  if (!value?.id) {
    createForm.screenName = '';
    createForm.viewLevel = 'BRANCH';
    createForm.bizLine = '';
    createForm.orgScopeMode = '';
    createForm.orgGroupCode = '';
    return;
  }
  const scope = normalizeScreenScope(value);
  createForm.screenName = value.screenName || '';
  createForm.viewLevel = scope.viewLevel || 'BRANCH';
  createForm.bizLine = scope.bizLine;
  createForm.orgScopeMode = scope.orgScopeMode;
  createForm.orgGroupCode = scope.orgGroupCode;
}
function openCreate() { editingScopeId.value = null; fillCreateForm(); createVisible.value = true; loadScopeChoices(); }
function openEditScope() {
  const current = screens.value.find(s => s.id === curId.value) || {};
  editingScopeId.value = curId.value;
  fillCreateForm(current);
  // 列表 DTO 有版本时优先使用；否则以已加载画布的版本作为同一屏 CAS 快照。
  const expectedVersion = current.canvasVersion ?? store.canvasVersion;
  metadataEdit.expectedVersion = Number.isInteger(expectedVersion) ? expectedVersion : null;
  createVisible.value = true;
  loadScopeChoices();
}
async function onCreateSubmit() {
  const screenName = createForm.screenName.trim();
  if (!screenName) { ElMessage.warning('请填写屏名称'); return; }
  const errors = validateScreenScope(createForm);
  if (errors.length) { ElMessage.warning(errors[0]); return; }
  const editing = Boolean(editingScopeId.value);
  if (editing && !String(metadataEdit.reason || '').trim()) {
    ElMessage.warning('元数据/机构范围变更必须填写原因');
    return;
  }
  if (editing && !Number.isInteger(metadataEdit.expectedVersion)) {
    ElMessage.warning('未能读取当前版本，不能覆盖保存范围');
    return;
  }
  creating.value = true;
  try {
    // 新建/编辑均显式提交范围契约；角色通过独立 PERMISSION_CHANGE 接口保存。
    const body = {
      screenName,
      viewLevel: createForm.viewLevel,
      bizLine: createForm.bizLine,
      orgScopeMode: createForm.orgScopeMode,
      // 更新已有屏切离 NAMED_GROUP 时必须发送空串清组；新建传统范围仍使用 null。
      orgGroupCode: createForm.orgScopeMode === 'NAMED_GROUP'
        ? createForm.orgGroupCode : (editing ? '' : null)
    };
    if (editing) {
      const current = screens.value.find(s => s.id === editingScopeId.value) || {};
      body.id = editingScopeId.value;
      // 已有屏固定走独立 metadata PUT；画布仅由 canvas/save 管理。
      // 因此范围编辑只带元数据，避免前端用陈旧区块误覆盖最新画布。
      body.screenCode = current.screenCode;
      body.themeJson = current.themeJson;
      // screen/status 契约只允许 ACTIVE/DISABLED；历史 DRAFT/ENABLED 等未知值不能被原样回写。
      if (['ACTIVE', 'DISABLED'].includes(current.status)) body.status = current.status;
      body.expectedVersion = metadataEdit.expectedVersion;
      body.reason = String(metadataEdit.reason || '').trim();
    }
    const createdId = await saveScreenMetadata(body);
    screens.value = await listScreens();
    // metadata PUT 响应为 Void，编辑时保留当前 screenId；只有新建才采用返回 ID。
    curId.value = editing ? editingScopeId.value : createdId;
    await loadCanvas(); // 新屏画布字段为空,loadFromEditor 走缺省分支得到空画布草稿
    createVisible.value = false;
    ElMessage.success(editing ? '大屏范围已保存' : '已新建大屏，当前为空画布草稿');
  } catch (error) {
    if (error?.code === 'RPT-43012') {
      await loadScreens();
      ElMessage.warning('范围保存发生版本冲突，已重新加载最新草稿');
      return;
    }
    throw error;
  } finally { creating.value = false; }
}

/** 加载角色快照和与其共用的 canvasVersion，供高危覆盖保存 CAS 使用。 */
async function openAccessRoleDialog() {
  if (!curId.value) return;
  accessRoleDialog.show = true;
  accessRoleDialog.saving = false;
  accessRoleDialog.reason = '';
  try {
    await loadScopeChoices();
    const [roleCodes, canvas] = await Promise.all([listScreenAccessRoles(curId.value), getScreenCanvas(curId.value)]);
    const normalized = (Array.isArray(roleCodes) ? roleCodes : []).filter(Boolean).map(String);
    accessRoleDialog.roleCodes = [...normalized];
    accessRoleDialog.initialRoleCodes = [...normalized];
    accessRoleDialog.expectedVersion = canvas?.canvasVersion ?? store.canvasVersion;
  } catch {
    // 读取白名单或版本失败时不保留旧快照，避免在未知版本上覆盖保存。
    accessRoleDialog.roleCodes = [];
    accessRoleDialog.initialRoleCodes = [];
    accessRoleDialog.expectedVersion = null;
  }
}

/** 独立保存屏级角色白名单，前端只据实际快照显示 added/removed。 */
async function saveAccessRoles() {
  const reason = String(accessRoleDialog.reason || '').trim();
  if (!reason) { ElMessage.warning('权限变更必须填写原因'); return; }
  if (!Number.isInteger(accessRoleDialog.expectedVersion)) {
    ElMessage.warning('未能读取当前版本，不能覆盖保存角色'); return;
  }
  const added = [...accessRoleDiff.value.added];
  const removed = [...accessRoleDiff.value.removed];
  accessRoleDialog.saving = true;
  try {
    await saveScreenAccessRoles(curId.value, {
      roleCodes: [...accessRoleDialog.roleCodes], reason, expectedVersion: accessRoleDialog.expectedVersion
    });
    // 成功后必须重新取 canvas 版本，后续角色/画布保存才不会拿旧 CAS 版本。
    await loadCanvas();
    accessRoleDialog.initialRoleCodes = [...accessRoleDialog.roleCodes];
    accessRoleDialog.expectedVersion = store.canvasVersion;
    accessRoleDialog.show = false;
    ElMessage.success(`查看角色已保存（新增 ${added.length}，移除 ${removed.length}）`);
  } finally { accessRoleDialog.saving = false; }
}
async function loadCanvas() {
  const [resp, regionMetrics] = await Promise.all([
    getScreenCanvas(curId.value),
    typeof listScreenMapRegionMetrics === 'function'
      ? listScreenMapRegionMetrics(curId.value).catch(() => []) : Promise.resolve([])
  ]);
  designRegionMetrics.value = Array.isArray(regionMetrics) ? regionMetrics : [];
  // 画布编辑接口为历史独立契约，新增屏范围字段来自屏列表/详情；合并后再灌 store，
  // 避免编辑命名机构组屏时因 canvas DTO 缺字段而静默回退 LEGACY_CONTEXT。
  const screenMeta = screens.value.find(s => s.id === curId.value) || {};
  store.loadFromEditor({ ...screenMeta, ...resp });
  resetDirtyBaseline();
  refreshDesignProfiles();
}
const SAVE_OUTCOME = Object.freeze({
  saved: Object.freeze({ saved: true, conflict: false }),
  failed: Object.freeze({ saved: false, conflict: false }),
  conflict: Object.freeze({ saved: false, conflict: true })
});
const SAVE_OPTIONS = Object.freeze({
  toolbar: Object.freeze({ resolveConflict: true }),
  exit: Object.freeze({ resolveConflict: false })
});

/**
 * 执行一次画布保存并返回结构化结果。
 * 工具栏允许进入既有 CAS 强制覆盖/重载流程；退出保存在首个 CAS 立即停止，
 * 避免“保存并关闭”暗中发出第二次覆盖写或丢弃本地修改。
 */
async function saveDraft(options = SAVE_OPTIONS.toolbar) {
  if (saving.value) return SAVE_OUTCOME.failed;
  saving.value = true;
  try {
    const resp = await saveScreenCanvas(store.toSavePayload());
    store.adoptSaveResult(resp);
    resetDirtyBaseline();
    ElMessage.success('已保存草稿');
    return SAVE_OUTCOME.saved;
  } catch (e) {
    if (e?.code === 'RPT-43012') {
      if (!options.resolveConflict) return SAVE_OUTCOME.conflict;
      // 二次失败(如强制覆盖重发时又撞上新的并发保存)已由 http 拦截器统一 toast,
      // 这里只吞掉避免冒泡成未捕获 rejection,不重复弹错。
      try { await handleSaveConflict(); } catch { /* 已 toast,吞掉 */ }
      return SAVE_OUTCOME.conflict;
    }
    return SAVE_OUTCOME.failed;
  } finally { saving.value = false; }
}
async function onSave() { return saveDraft(SAVE_OPTIONS.toolbar); }
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
    resetDirtyBaseline();
    ElMessage.success('已强制覆盖保存');
  } else if (action === 'reload') {
    await loadCanvas();
    ElMessage.info('已重新加载最新版本');
  }
  // dismiss:用户仅关闭弹框,不做任何操作
}
async function onPublish() {
  const expectedVersion = store.canvasVersion;
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
    ElMessage.warning('未能读取当前版本，不能发布大屏');
    return;
  }
  publishDialog.show = true;
  publishDialog.saving = false;
  publishDialog.expectedVersion = expectedVersion;
  publishDialog.reason = '';
}
async function confirmPublish() {
  const reason = String(publishDialog.reason || '').trim();
  if (!reason) { ElMessage.warning('发布大屏必须填写原因'); return; }
  if (!Number.isSafeInteger(publishDialog.expectedVersion) || publishDialog.expectedVersion < 0) {
    ElMessage.warning('未能读取当前版本，不能发布大屏'); return;
  }
  publishing.value = true;
  publishDialog.saving = true;
  try {
    // 先存后发，发布 CAS 必须使用保存成功后服务端返回的最新版本，不能继续使用弹框打开时的旧版本。
    const saved = await saveScreenCanvas(store.toSavePayload());
    store.adoptSaveResult(saved);
    resetDirtyBaseline();
    if (!Number.isSafeInteger(store.canvasVersion) || store.canvasVersion < 0) {
      throw new Error('保存后未返回有效版本，已拒绝发布');
    }
    await publishScreenCanvas({ screenId: store.screenId, expectedVersion: store.canvasVersion, reason });
    await loadCanvas();
    publishDialog.show = false;
    ElMessage.success('已发布');
  } catch (error) {
    if (error?.code === 'RPT-43012') {
      await loadCanvas();
      publishDialog.show = false;
      ElMessage.warning('发布发生版本冲突，已重新加载最新草稿');
      return;
    }
    throw error;
  } finally {
    publishDialog.saving = false;
    publishing.value = false;
  }
}
function onDiscard() {
  const expectedVersion = store.canvasVersion;
  if (!Number.isInteger(expectedVersion)) {
    ElMessage.warning('未能读取当前版本，不能放弃草稿');
    return;
  }
  discardDialog.show = true;
  discardDialog.saving = false;
  discardDialog.expectedVersion = expectedVersion;
  discardDialog.reason = '';
}
async function confirmDiscard() {
  const reason = String(discardDialog.reason || '').trim();
  if (!reason) { ElMessage.warning('放弃草稿必须填写原因'); return; }
  if (!Number.isInteger(discardDialog.expectedVersion)) {
    ElMessage.warning('未能读取当前版本，不能放弃草稿');
    return;
  }
  discardDialog.saving = true;
  try {
    await discardScreenCanvas(store.screenId, { expectedVersion: discardDialog.expectedVersion, reason });
    await loadCanvas();
    discardDialog.show = false;
    ElMessage.success('已放弃草稿');
  } catch (error) {
    if (error?.code === 'RPT-43012') {
      await loadCanvas();
      discardDialog.show = false;
      ElMessage.warning('放弃草稿发生版本冲突，已重新加载最新草稿');
      return;
    }
    throw error;
  } finally { discardDialog.saving = false; }
}
async function onRollback() {
  if (!Number.isSafeInteger(store.canvasVersion) || store.canvasVersion < 0) {
    ElMessage.warning('未能读取当前版本，不能回滚大屏');
    return;
  }
  const logs = await listScreenPublishLogs(store.screenId);
  const archives = Array.isArray(logs)
    ? logs.filter(item => Number.isSafeInteger(item?.id) && item.id > 0).slice(0, 10)
    : [];
  if (!archives.length) { ElMessage.info('暂无发布归档'); return; }
  rollbackDialog.show = true;
  rollbackDialog.saving = false;
  rollbackDialog.archives = archives;
  // 不预选任何版本，避免用户在没有确认目标归档的情况下误回滚。
  rollbackDialog.publishLogId = null;
  rollbackDialog.publishedAt = '';
  rollbackDialog.expectedVersion = store.canvasVersion;
  rollbackDialog.reason = '';
}
function rollbackArchiveLabel(archive) {
  const id = Number.isSafeInteger(archive?.id) ? `归档 #${archive.id}` : '无效归档';
  const time = archive?.publishedAt || '时间未知';
  const by = archive?.publishedBy ? ` · ${archive.publishedBy}` : '';
  return `${id} · ${time}${by}`;
}
function onRollbackArchiveChange(value) {
  const archive = rollbackDialog.archives.find(item => item.id === value);
  rollbackDialog.publishedAt = archive?.publishedAt || '';
}
async function confirmRollback() {
  const reason = String(rollbackDialog.reason || '').trim();
  if (!reason) { ElMessage.warning('回滚大屏必须填写原因'); return; }
  if (!Number.isSafeInteger(rollbackDialog.expectedVersion) || rollbackDialog.expectedVersion < 0
    || !Number.isSafeInteger(rollbackDialog.publishLogId) || rollbackDialog.publishLogId <= 0) {
    ElMessage.warning('回滚版本或发布归档无效，不能提交'); return;
  }
  rollbackDialog.saving = true;
  try {
    await rollbackScreenCanvas({
      screenId: store.screenId,
      publishLogId: rollbackDialog.publishLogId,
      expectedVersion: rollbackDialog.expectedVersion,
      reason
    });
    await loadCanvas();
    rollbackDialog.show = false;
    ElMessage.success('已回滚');
  } catch (error) {
    if (error?.code === 'RPT-43012') {
      await loadCanvas();
      rollbackDialog.show = false;
      ElMessage.warning('回滚发生版本冲突，已重新加载最新草稿');
      return;
    }
    throw error;
  } finally { rollbackDialog.saving = false; }
}
function onPreview() {
  // 前端只提供入口；发布态四门与 draft 额外 R_RPT_SCR_CV_GET 均由服务端 Fail Close，SYS_ADMIN 无旁路。
  window.open(`#/screen/${store.screenCode}?preview=draft`, '_blank');
}

function focusElement(componentRef) {
  const element = componentRef?.$el || componentRef;
  element?.focus?.();
}
function focusExitCancel() { focusElement(exitCancelButton.value); }
async function closeDesigner() {
  await closeWindowOrFallback(window, () => router.replace('/workspace'));
}
async function onReturn() {
  if (saving.value || publishing.value || exitDialog.saving) return;
  if (!isDirty.value) {
    await closeDesigner();
    return;
  }
  exitDialog.show = true;
  await nextTick();
  focusExitCancel();
}
async function cancelReturn() {
  if (exitDialog.saving) return;
  exitDialog.show = false;
  await nextTick();
  focusElement(returnButton.value);
}
async function discardAndClose() {
  if (exitDialog.saving) return;
  exitDialog.saving = true;
  // “放弃”是放弃本地未保存修改，不调用后端“放弃服务端草稿”的高危审计端点。
  isDirty.value = false;
  exitDialog.show = false;
  try {
    await closeDesigner();
  } finally {
    exitDialog.saving = false;
  }
}
async function saveAndClose() {
  if (exitDialog.saving) return;
  exitDialog.saving = true;
  try {
    const result = await saveDraft(SAVE_OPTIONS.exit);
    // 任何保存失败或 CAS 冲突都留在设计器，由用户检查后再决定。
    if (!result.saved) return;
    exitDialog.show = false;
    await closeDesigner();
  } finally {
    exitDialog.saving = false;
  }
}
function onBeforeUnload(event) {
  if (!isDirty.value) return;
  event.preventDefault();
  event.returnValue = '';
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
  window.addEventListener('beforeunload', onBeforeUnload);
  await loadScreens();
  await loadScopeChoices();
  // 首屏加载完自动适应窗口:默认 50% 缩放与中栏尺寸无关,首屏观感差(设计器页面整改 #3)
  await nextTick();
  fitWindow();
});
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey);
  window.removeEventListener('beforeunload', onBeforeUnload);
});
</script>
<style scoped lang="scss">
@use '@/styles/screen-theme' as theme;
// 顶层独立路由直接撑满 #app，不再依赖 DefaultLayout 的壳层高度。
// 令牌只挂在 .dsn2 内，避免把设计器工作台的深色视觉泄漏到普通平台页面。
.dsn2 {
  --dsn2-bg: #0a1020;
  --dsn2-bg-deep: #070c17;
  --dsn2-bg-panel: #0d1628;
  --dsn2-bg-elevated: #111c31;
  --dsn2-border: #263751;
  --dsn2-border-soft: rgba(124, 154, 190, .2);
  --dsn2-text: #e8eef8;
  --dsn2-muted: #8797ae;
  --dsn2-accent: #52c7c3;
  --dsn2-accent-strong: #79e0d5;
  --dsn2-danger: #e08a8a;
  --dsn2-radius: 10px;
  --dsn2-shadow: 0 14px 36px rgba(0, 0, 0, .2);
  display: flex;
  min-width: 0;
  height: 100%;
  flex-direction: column;
  background: radial-gradient(circle at 50% -20%, #152541 0, var(--dsn2-bg) 46%, var(--dsn2-bg-deep) 100%);
  color: var(--dsn2-text);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.scr-surface-host { @include theme.scr-theme-vars; } // 供画布内复用 .scr-* 视觉变量
.scr-surface-host :deep(.scr-block-h) {
  color: var(--scr-text, #f5fbff);
  font-weight: 600;
  text-shadow: 0 0 10px rgba(0, 229, 255, .35);
}
.dsn2-toolbar {
  z-index: 2;
  display: flex;
  min-width: 0;
  min-height: 64px;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--dsn2-border);
  background: rgba(10, 16, 32, .88);
  box-shadow: 0 8px 24px rgba(0, 0, 0, .14);
}
.dsn2-toolbar__brand {
  display: flex;
  min-width: 154px;
  align-items: center;
  gap: 9px;
  margin-right: 4px;
}
.dsn2-brand-mark {
  display: inline-flex;
  width: 31px;
  height: 31px;
  align-items: center;
  justify-content: center;
  border: 1px solid color-mix(in srgb, var(--dsn2-accent) 46%, transparent);
  border-radius: 9px;
  background: color-mix(in srgb, var(--dsn2-accent) 10%, transparent);
  color: var(--dsn2-accent-strong);
  font-size: 18px;
  box-shadow: inset 0 0 12px color-mix(in srgb, var(--dsn2-accent) 8%, transparent);
}
.dsn2-brand-copy { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.dsn2-brand-copy strong { color: var(--dsn2-text); font-size: 14px; font-weight: 650; letter-spacing: .02em; white-space: nowrap; }
.dsn2-brand-copy span { color: var(--dsn2-muted); font-size: 10px; white-space: nowrap; }
.dsn2-toolbar__cluster {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  padding: 4px 7px;
  border: 1px solid var(--dsn2-border-soft);
  border-radius: var(--dsn2-radius);
  background: rgba(17, 28, 49, .54);
}
.dsn2-toolbar__screen { min-width: 218px; flex: 1 1 218px; flex-direction: column; align-items: stretch; gap: 2px; }
.dsn2-toolbar__context-label { color: var(--dsn2-muted); font-size: 10px; line-height: 1.1; }
.dsn2-screen-select { width: 100%; }
.dsn2-screen-code { overflow: hidden; color: var(--dsn2-muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.dsn2-toolbar__history { flex: 0 1 auto; }
.dsn2-toolbar__publish { margin-left: auto; }
.dsn2-zoom-control { display: flex; min-width: 102px; align-items: center; gap: 7px; }
.dsn2-zoom-label { min-width: 32px; color: var(--dsn2-muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.dsn2-zoom-control :deep(.el-slider) { width: 70px; }
.dsn2-toolbar :deep(.el-button) {
  --el-button-text-color: var(--dsn2-text);
  --el-button-bg-color: transparent;
  --el-button-border-color: var(--dsn2-border);
  --el-button-hover-text-color: var(--dsn2-accent-strong);
  --el-button-hover-bg-color: rgba(82, 199, 195, .1);
  --el-button-hover-border-color: color-mix(in srgb, var(--dsn2-accent) 58%, var(--dsn2-border));
  --el-button-active-bg-color: rgba(82, 199, 195, .14);
  border-radius: 7px;
  font-size: 12px;
}
.dsn2-toolbar :deep(.el-button--primary) {
  --el-button-bg-color: var(--dsn2-accent);
  --el-button-border-color: var(--dsn2-accent);
  --el-button-hover-bg-color: var(--dsn2-accent-strong);
  --el-button-hover-border-color: var(--dsn2-accent-strong);
  --el-button-text-color: #081516;
}
.dsn2-toolbar :deep(.el-button--danger) {
  --el-button-bg-color: rgba(224, 138, 138, .12);
  --el-button-border-color: rgba(224, 138, 138, .48);
  --el-button-text-color: #f0b6b6;
  --el-button-hover-bg-color: rgba(224, 138, 138, .2);
}
.dsn2-toolbar :deep(.el-select__wrapper), .dsn2-toolbar :deep(.el-input__wrapper) {
  min-height: 29px;
  background: var(--dsn2-bg-elevated);
  box-shadow: 0 0 0 1px var(--dsn2-border) inset;
  color: var(--dsn2-text);
}
.dsn2-toolbar :deep(.el-select__placeholder), .dsn2-toolbar :deep(.el-select__selected-item) { color: var(--dsn2-text); font-size: 12px; }
.dirty-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 27px;
  padding: 0 8px;
  border: 1px solid var(--dsn2-border-soft);
  border-radius: 99px;
  color: var(--dsn2-muted);
  font-size: 11px;
  line-height: 20px;
  white-space: nowrap;
}
.dirty-state__dot { width: 6px; height: 6px; border-radius: 50%; background: var(--dsn2-muted); }
.dirty-state.is-saved { border-color: rgba(82, 199, 195, .3); color: #9dd5cf; }
.dirty-state.is-saved .dirty-state__dot { background: var(--dsn2-accent); }
.dirty-state.is-dirty { border-color: rgba(224, 181, 112, .4); color: #e6c58f; }
.dirty-state.is-dirty .dirty-state__dot { background: #e0b570; }
.dsn2-cols { display: flex; min-height: 0; flex: 1; }
.dsn2-left, .dsn2-right {
  width: 264px;
  min-width: 168px;
  flex: 0 1 264px;
  overflow: auto;
  background: rgba(13, 22, 40, .94);
  border-right: 1px solid var(--dsn2-border);
}
.dsn2-right { width: 308px; flex-basis: 308px; border-right: none; border-left: 1px solid var(--dsn2-border); } // 右栏容纳两列数字输入与图表取数表单
.dsn2-panel-heading {
  display: flex;
  min-height: 76px;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 14px 12px;
  border-bottom: 1px solid var(--dsn2-border-soft);
  background: linear-gradient(145deg, rgba(17, 28, 49, .9), rgba(13, 22, 40, .72));
}
.dsn2-panel-heading h2 { margin: 4px 0 3px; color: var(--dsn2-text); font-size: 14px; font-weight: 650; letter-spacing: .01em; }
.dsn2-panel-heading p { margin: 0; color: var(--dsn2-muted); font-size: 11px; line-height: 1.4; }
.dsn2-panel-kicker { color: var(--dsn2-accent); font-size: 10px; font-weight: 650; letter-spacing: .1em; }
.dsn2-panel-indicator { color: var(--dsn2-accent); font-size: 9px; opacity: .75; }
.dsn2-left :deep(.el-tabs__header) { margin: 0; padding: 0 12px; background: rgba(10, 16, 32, .35); }
.dsn2-left :deep(.el-tabs__nav-wrap::after) { background: var(--dsn2-border-soft); }
.dsn2-left :deep(.el-tabs__item) { height: 38px; color: var(--dsn2-muted); font-size: 12px; }
.dsn2-left :deep(.el-tabs__item.is-active) { color: var(--dsn2-accent-strong); }
.dsn2-left :deep(.el-tabs__active-bar) { background: var(--dsn2-accent); }
.dsn2-center { min-width: 280px; flex: 1 1 280px; }
// 属性栏统一接管 Element Plus 的浅色默认值，使标题、标签、输入区和左侧素材栏属于同一套深色视觉系统。
.dsn2-inspector {
  --dsn-inspector-font-size: 12px;
  --el-bg-color: #071735;
  --el-bg-color-overlay: #0a1d40;
  --el-fill-color-blank: #0a1d40;
  --el-fill-color-light: #0d2851;
  --el-fill-color: #0b2348;
  --el-border-color: rgba(55, 160, 211, .34);
  --el-border-color-light: rgba(55, 160, 211, .24);
  --el-border-color-lighter: rgba(55, 160, 211, .16);
  --el-text-color-primary: #d7e8ff;
  --el-text-color-regular: #b2c8e7;
  --el-text-color-secondary: #86a7cf;
  color: var(--el-text-color-regular);
  font-size: var(--dsn-inspector-font-size);
}
.dsn2-inspector :deep(.el-collapse) { border: 0; background: transparent; }
.dsn2-inspector :deep(.el-collapse-item__header) {
  height: 42px; padding: 0 12px; border-bottom: 1px solid rgba(0, 229, 255, .14);
  background: #071735; color: #d7e8ff; font-size: 13px; font-weight: 600;
}
.dsn2-inspector :deep(.el-collapse-item__wrap) {
  border-bottom: 1px solid rgba(0, 229, 255, .14); background: #050e2b;
}
.dsn2-inspector :deep(.el-collapse-item__content) {
  padding: 12px 4px 14px; color: #b2c8e7; font-size: var(--dsn-inspector-font-size);
}
.dsn2-inspector :deep(.el-form-item) { margin-bottom: 12px; }
.dsn2-inspector :deep(.el-form-item__label) {
  color: #9bb8dc; font-size: var(--dsn-inspector-font-size); line-height: 28px;
}
.dsn2-inspector :deep(.el-input__wrapper),
.dsn2-inspector :deep(.el-select__wrapper),
.dsn2-inspector :deep(.el-textarea__inner) {
  background: #0a1d40; box-shadow: 0 0 0 1px rgba(55, 160, 211, .34) inset;
  color: #d7e8ff; font-size: var(--dsn-inspector-font-size);
}
.dsn2-inspector :deep(.el-input__inner),
.dsn2-inspector :deep(.el-textarea__inner) { color: #d7e8ff; }
.dsn2-inspector :deep(.el-input__inner::placeholder),
.dsn2-inspector :deep(.el-textarea__inner::placeholder) { color: #6686af; }
.dsn2-inspector :deep(.el-input-number__increase),
.dsn2-inspector :deep(.el-input-number__decrease) {
  border-color: rgba(55, 160, 211, .28); background: #0d2851; color: #91b9e4;
}
.dsn2-inspector :deep(.el-radio-button__inner) {
  border-color: rgba(55, 160, 211, .28); background: #0a1d40; color: #a9c3e4;
  font-size: var(--dsn-inspector-font-size); box-shadow: none;
}
.dsn2-inspector :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: #1c8bd1; background: #0b4f8d; color: #f2f8ff; box-shadow: -1px 0 0 0 #1c8bd1;
}
.dsn2-inspector :deep(.el-slider__runway) { background: #18365e; }
.dsn2-inspector :deep(.el-slider__bar) { background: #168fcd; }
.dsn2-inspector :deep(.el-slider__button) { border-color: #30bde9; background: #dff7ff; }
.dsn2-inspector :deep(.el-button) { font-size: var(--dsn-inspector-font-size); }
.dsn2-inspector { background: var(--dsn2-bg-panel); }
.dsn2-inspector :deep(.el-collapse-item__header) { background: var(--dsn2-bg-elevated); border-color: var(--dsn2-border-soft); color: var(--dsn2-text); }
.dsn2-inspector :deep(.el-collapse-item__wrap) { background: var(--dsn2-bg-panel); border-color: var(--dsn2-border-soft); }
.dsn2-inspector :deep(.el-input__wrapper),
.dsn2-inspector :deep(.el-select__wrapper),
.dsn2-inspector :deep(.el-textarea__inner) {
  background: var(--dsn2-bg-elevated);
  box-shadow: 0 0 0 1px var(--dsn2-border) inset;
  color: var(--dsn2-text);
}
.dsn2-inspector :deep(.el-button) {
  --el-button-text-color: var(--dsn2-text);
  --el-button-bg-color: transparent;
  --el-button-border-color: var(--dsn2-border);
  --el-button-hover-text-color: var(--dsn2-accent-strong);
  --el-button-hover-bg-color: rgba(82, 199, 195, .1);
  --el-button-hover-border-color: var(--dsn2-accent);
  border-radius: 7px;
}
.dsn2 :deep(button:focus-visible),
.dsn2 :deep(input:focus-visible),
.dsn2 :deep(textarea:focus-visible),
.dsn2 :deep(.el-button:focus-visible),
.dsn2 :deep(.el-select:focus-within),
.dsn2 :deep(.el-slider:focus-within) {
  outline: 2px solid var(--dsn2-accent-strong);
  outline-offset: 2px;
}
.dsn2 :deep(.el-button:focus:not(:focus-visible)) { outline: none; }
.scope-hint { color: var(--dsn2-muted, #7d9bc9); font-size: 12px; line-height: 1.5; margin-top: 4px; }
.role-change-preview { display: flex; flex-direction: column; gap: 3px; color: var(--dsn2-muted, #7d9bc9); font-size: 12px; line-height: 1.5; }
.screen-security-hint { padding: 4px 12px 8px; color: var(--dsn2-muted, #7d9bc9); font-size: 12px; line-height: 1.5; }
.exit-dialog-copy { margin: 0; color: #334155; font-size: 14px; line-height: 1.7; }
@media (max-width: 1280px) {
  .dsn2-toolbar { flex-wrap: wrap; }
  .dsn2-toolbar__brand { min-width: 142px; }
  .dsn2-toolbar__screen { flex: 1 1 190px; }
  .dsn2-toolbar__publish { margin-left: 0; }
  .dsn2-left { width: 220px; flex-basis: 220px; }
  .dsn2-right { width: 270px; flex-basis: 270px; }
}
@media (max-width: 900px) {
  .dsn2-toolbar { align-items: stretch; padding: 8px 10px; }
  .dsn2-toolbar__brand { min-width: 130px; }
  .dsn2-brand-copy span { display: none; }
  .dsn2-toolbar__cluster { flex: 1 1 auto; }
  .dsn2-toolbar__screen { min-width: 180px; }
  .dsn2-left { width: 190px; flex-basis: 190px; }
  .dsn2-right { width: 246px; flex-basis: 246px; }
}
@media (max-width: 680px) {
  .dsn2-toolbar { max-height: 132px; overflow: auto; }
  .dsn2-toolbar__brand { min-width: 124px; }
  .dsn2-toolbar__cluster { padding: 3px 5px; }
  .dsn2-toolbar__screen { min-width: 160px; }
  .dsn2-left { width: 168px; flex-basis: 168px; }
  .dsn2-right { width: 214px; flex-basis: 214px; }
  .dsn2-center { min-width: 280px; }
}
@media (prefers-reduced-motion: reduce) {
  .dsn2 *, .dsn2 *::before, .dsn2 *::after {
    animation-duration: .01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: .01ms !important;
  }
}
</style>
