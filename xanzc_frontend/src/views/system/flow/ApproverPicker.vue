<template>
  <!-- 审批人选择器：多行并存（并集）。每个审批人一块，块内每个下拉/输入各占一行竖排，保证窄面板显示完整。
       数据：层级角色 {approverType:'LEVEL_ROLE', orgScope, approverValue=角色码}
            机构角色 {approverType:'ORG_ROLE', approverValue=机构码, roleCode?}
            指定人  {approverType:'USER', approverValue=工号}
            流程变量 {approverType:'VAR', approverValue=变量名} -->
  <div class="approver-picker" role="group" aria-label="审批人规则">
    <div
      v-for="(row, idx) in localList"
      :key="idx"
      class="approver-block"
    >
      <!-- 头部：类型 + 删除 -->
      <div class="ab-head">
        <el-select
          v-model="row.approverType"
          style="flex: 1"
          aria-label="审批人类型"
          placeholder="审批人类型"
          @change="onTypeChange(idx)"
        >
          <el-option label="层级角色" value="LEVEL_ROLE" />
          <el-option label="机构角色" value="ORG_ROLE" />
          <el-option label="指定人" value="USER" />
          <el-option label="流程变量" value="VAR" />
        </el-select>
        <el-button
          type="danger"
          :icon="Delete"
          circle
          size="small"
          aria-label="删除该审批人规则"
          title="删除该审批人规则"
          @click="removeRow(idx)"
        />
      </div>

      <!-- 层级角色：层级（必选，无 label） + 角色，各占一行 -->
      <template v-if="row.approverType === 'LEVEL_ROLE'">
        <el-select
          v-model="row.orgScope"
          class="ab-ctrl"
          aria-label="机构层级"
          placeholder="层级（空=不限机构层级）"
          clearable
          @change="onValueChange"
        >
          <el-option label="发起机构" value="SELF" />
          <el-option label="发起上级机构" value="PARENT" />
          <el-option label="二级机构" value="L2" />
        </el-select>
        <el-select
          v-model="row.approverValue"
          class="ab-ctrl"
          aria-label="角色"
          placeholder="选择角色"
          filterable
          @change="onValueChange"
        >
          <el-option v-for="r in roleOptions" :key="r.roleCode" :label="r.roleChName" :value="r.roleCode" />
        </el-select>
      </template>

      <!-- 机构角色：机构 + 角色（可选），各占一行 -->
      <template v-else-if="row.approverType === 'ORG_ROLE'">
        <el-select
          v-model="row.approverValue"
          class="ab-ctrl"
          aria-label="机构"
          placeholder="选择机构"
          filterable
          @change="onValueChange"
        >
          <el-option
            v-for="o in orgOptions"
            :key="o.code"
            :label="o.deptNo ? `${o.name}（${o.deptNo}）` : o.name"
            :value="o.code"
          />
        </el-select>
        <el-select
          v-model="row.roleCode"
          class="ab-ctrl"
          aria-label="机构角色"
          placeholder="角色（可选，空=该机构任一角色）"
          clearable
          filterable
          @change="onValueChange"
        >
          <el-option v-for="r in roleOptions" :key="r.roleCode" :label="r.roleChName" :value="r.roleCode" />
        </el-select>
      </template>

      <!-- 指定人：远程搜索 -->
      <el-select
        v-else-if="row.approverType === 'USER'"
        v-model="row.approverValue"
        class="ab-ctrl"
        aria-label="指定审批人"
        placeholder="输入姓名/工号搜索"
        filterable
        remote
        :remote-method="(kw) => onUserSearch(kw, idx)"
        :loading="userSearchLoading"
        @change="onValueChange"
      >
        <el-option
          v-for="u in userSearchOptions"
          :key="u.id"
          :label="`${u.name}（${u.id}）` + (u.org ? ' · ' + u.org : '')"
          :value="u.id"
        />
      </el-select>

      <!-- 流程变量：下拉 -->
      <el-select
        v-else-if="row.approverType === 'VAR'"
        v-model="row.approverValue"
        class="ab-ctrl"
        aria-label="流程变量审批人"
        placeholder="选择流程变量"
        filterable
        @change="onValueChange"
      >
        <el-option
          v-for="v in approverVariables"
          :key="v.field"
          :label="`${v.label}（${v.field}）`"
          :value="v.field"
        />
      </el-select>
    </div>

    <!-- 添加一行 -->
    <el-button type="primary" text class="add-approver" @click="addRow">添加审批人</el-button>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Delete } from '@element-plus/icons-vue';
import { listAllRoles } from '@/api/system';
import { getOrgTree } from '@/api/orgs';
import { searchEmployees, getEmployee } from '@/api/userDirectory';

const props = defineProps({
  /** v-model：审批人规则数组，每项 {approverType, approverValue, orgScope?, roleCode?} */
  modelValue: { type: Array, default: () => [] },
  /** VAR 审批人可选的名单类流程变量 [{field,label}] */
  approverVariables: { type: Array, default: () => [] }
});
const emit = defineEmits(['update:modelValue']);

const localList = ref([]);
watch(
  () => props.modelValue,
  (val) => {
    localList.value = (val || []).map(r => ({ ...r }));
    preloadUserLabels();   // 指定人反显：补全已选工号的姓名
  },
  { immediate: true, deep: true }
);

/** 为已选「指定人」审批人预载姓名，使选中工号反显为「姓名（工号）」 */
async function preloadUserLabels() {
  const known = new Set(userSearchOptions.value.map(u => u.id));
  const ids = localList.value
    .filter(r => r.approverType === 'USER' && r.approverValue && !known.has(r.approverValue))
    .map(r => r.approverValue);
  for (const id of [...new Set(ids)]) {
    try {
      const u = await getEmployee(id);
      if (u && u.id && !userSearchOptions.value.some(x => x.id === u.id)) {
        userSearchOptions.value = [...userSearchOptions.value, u];
      }
    } catch { /* 单个失败忽略，不影响其它 */ }
  }
}

const roleOptions = ref([]); // [{roleCode, roleChName}]（状态正常）
const orgOptions = ref([]);  // [{code, name, deptNo}]

function flattenOrgTree(nodes, result = []) {
  for (const node of nodes || []) {
    result.push({ code: node.code, name: node.name, deptNo: node.deptNo });
    if (node.children?.length) flattenOrgTree(node.children, result);
  }
  return result;
}

const userSearchOptions = ref([]);
const userSearchLoading = ref(false);
let userSearchVersion = 0;
async function onUserSearch(keyword) {
  const version = ++userSearchVersion;
  if (!keyword?.trim()) { userSearchOptions.value = []; userSearchLoading.value = false; return; }
  userSearchLoading.value = true;
  try {
    const result = await searchEmployees(keyword.trim());
    if (version === userSearchVersion) userSearchOptions.value = Array.isArray(result) ? result : [];
  } catch {
    if (version === userSearchVersion) {
      ElMessage.warning('员工搜索失败，请重试');
      userSearchOptions.value = [];
    }
  } finally {
    if (version === userSearchVersion) userSearchLoading.value = false;
  }
}

onMounted(async () => {
  try {
    const roles = await listAllRoles({ recordStatus: 0 });
    const arr = Array.isArray(roles) ? roles : (roles?.records || []);
    roleOptions.value = arr
      .filter(r => r.recordStatus == null || r.recordStatus === 0)
      .map(r => ({
        roleCode: r.roleCode || r.code || '',
        roleChName: r.roleChName || r.roleChname || r.name || r.roleCode || ''
      }))
      // 按角色名称排序（中文）
      .sort((a, b) => (a.roleChName || '').localeCompare(b.roleChName || '', 'zh-Hans-CN'));
  } catch {
    ElMessage.warning('角色列表加载失败');
  }
  try {
    orgOptions.value = flattenOrgTree(await getOrgTree());
  } catch {
    ElMessage.warning('机构列表加载失败');
  }
});

/** 添加一行（默认层级角色） */
function addRow() {
  localList.value = [...localList.value, { approverType: 'LEVEL_ROLE', approverValue: '', orgScope: '', roleCode: '' }];
  emitUpdate();
}

function removeRow(idx) {
  localList.value = localList.value.filter((_, i) => i !== idx);
  emitUpdate();
}

/** 类型切换时清空该行其它字段，避免脏值 */
function onTypeChange(idx) {
  const r = localList.value[idx];
  r.approverValue = '';
  r.orgScope = '';
  r.roleCode = '';
  if (r.approverType === 'USER') userSearchOptions.value = [];
  emitUpdate();
}

function onValueChange() { emitUpdate(); }

function emitUpdate() {
  emit('update:modelValue', localList.value.map(r => ({ ...r })));
}
</script>

<style scoped>
.approver-picker { width: 100%; }
/* 每个审批人一块，块内竖排 */
.approver-block {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  margin-bottom: var(--space-3);
  padding: var(--space-2);
}
.ab-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}
/* 块内每个控件独占一行 */
.ab-ctrl {
  display: block;
  width: 100%;
  margin-bottom: var(--space-2);
}
.ab-ctrl:last-child { margin-bottom: 0; }
.add-approver { margin-top: var(--space-2); }
</style>
