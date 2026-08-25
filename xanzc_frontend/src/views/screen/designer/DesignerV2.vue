<template>
  <div class="dsn2 scr-surface-host">
    <!-- 顶部工具条 -->
    <div class="dsn2-toolbar">
      <el-select v-model="curId" placeholder="选择大屏" size="small" style="width:260px" @change="loadCanvas">
        <el-option v-for="s in screens" :key="s.id" :label="`${s.screenName}（${viewLevelLabel(s.viewLevel)}）`" :value="s.id" />
      </el-select>
      <el-button size="small" @click="openCreate">新建</el-button>
      <el-button size="small" :disabled="!curId" @click="openEditScope">编辑范围</el-button>
      <el-button size="small" :disabled="!curId" @click="openAccessRoleDialog">管理查看角色</el-button>
      <span class="spacer" />
      <el-button-group size="small">
        <el-button :disabled="!store.canUndo" @click="store.undo()">撤销</el-button>
        <el-button :disabled="!store.canRedo" @click="store.redo()">重做</el-button>
      </el-button-group>
      <el-slider v-model="scalePct" :min="50" :max="150" :step="10" style="width:120px" @input="onScale" />
      <el-button size="small" @click="fitWindow">适应窗口</el-button>
      <el-button size="small" :disabled="!curId" @click="onPreview">预览草稿</el-button>
      <el-button size="small" :disabled="!curId" @click="onDiscard">放弃草稿</el-button>
      <el-button size="small" @click="onRollback">回滚</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="onSave">保存</el-button>
      <el-button size="small" type="danger" :loading="publishing" @click="onPublish">发布</el-button>
      <span class="dirty-state" role="status" aria-live="polite">{{ isDirty ? '有未保存修改' : '已保存' }}</span>
      <el-button ref="returnButton" size="small" aria-label="返回工作区"
                 :disabled="saving || publishing || exitDialog.saving" @click="onReturn">返回</el-button>
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
        <el-form-item label="目标归档"><span>{{ rollbackDialog.publishedAt || '-' }}</span></el-form-item>
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
  listOrgProfiles, listScreenAccessRoles, saveScreenAccessRoles } from '@/api/screen';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { fitScale } from '@/views/screen/designer/utils/scale';
import { cloneComponentForClipboard, pasteFromClipboard } from '@/views/screen/designer/utils/clipboard';
import { closeWindowOrFallback } from '@/views/screen/designer/utils/closeWindow';
import { findAttr } from '@/views/screen/designer/widgets';
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
// CanvasCore 下的 MapCenter 通过同一响应式引用读取已配置画像；运行时不会继承此注入。
provide('screenProfiles', designProfiles);
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

function attrOf(component) { return findAttr(component); }
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
  show: false, saving: false, publishLogId: null, publishedAt: '', expectedVersion: null, reason: ''
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
  const resp = await getScreenCanvas(curId.value);
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
  if (!logs.length) { ElMessage.info('暂无发布归档'); return; }
  // 简化:回滚到最近一次归档(完整版可弹选择列表)
  const latest = logs[0];
  if (!Number.isSafeInteger(latest?.id) || latest.id <= 0) {
    ElMessage.warning('发布归档标识无效，不能回滚');
    return;
  }
  rollbackDialog.show = true;
  rollbackDialog.saving = false;
  rollbackDialog.publishLogId = latest.id;
  rollbackDialog.publishedAt = latest.publishedAt || '';
  rollbackDialog.expectedVersion = store.canvasVersion;
  rollbackDialog.reason = '';
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
.dsn2 { display: flex; flex-direction: column; height: 100%; background: #03081c; }
.scr-surface-host { @include theme.scr-theme-vars; } // 供画布内复用 .scr-* 视觉变量
.scr-surface-host :deep(.scr-block-h) {
  color: var(--scr-text, #f5fbff);
  font-weight: 600;
  text-shadow: 0 0 10px rgba(0, 229, 255, .35);
}
.dsn2-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  border-bottom: 1px solid rgba(0,229,255,.2); }
.dsn2-toolbar .spacer { flex: 1; }
.dirty-state { color: #9bb3d8; font-size: 12px; line-height: 20px; white-space: nowrap; }
.dsn2-cols { flex: 1; display: flex; min-height: 0; }
.dsn2-left, .dsn2-right { width: 260px; flex: none; overflow: auto; background: #050e2b;
  border-right: 1px solid rgba(0,229,255,.15); }
.dsn2-right { width: 300px; border-right: none; border-left: 1px solid rgba(0,229,255,.15); } // 右栏 260→300:容纳两列数字输入与图表取数表单
.dsn2-center { flex: 1; min-width: 0; }
.scope-hint { color: #7d9bc9; font-size: 12px; line-height: 1.5; margin-top: 4px; }
.role-change-preview { display: flex; flex-direction: column; gap: 3px; color: #7d9bc9; font-size: 12px; line-height: 1.5; }
.screen-security-hint { padding: 4px 12px 8px; color: #7d9bc9; font-size: 12px; line-height: 1.5; }
.exit-dialog-copy { margin: 0; color: #334155; font-size: 14px; line-height: 1.7; }
</style>
