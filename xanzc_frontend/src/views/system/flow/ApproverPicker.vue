<template>
  <!-- 审批人选择器：多行并存（并集），每行 = {approverType, approverValue} -->
  <div class="approver-picker">
    <div
      v-for="(row, idx) in localList"
      :key="idx"
      class="approver-row"
    >
      <!-- 审批人类型下拉 -->
      <el-select
        v-model="row.approverType"
        style="width: 110px; flex-shrink: 0"
        placeholder="类型"
        @change="onTypeChange(idx)"
      >
        <el-option label="角色" value="ROLE" />
        <el-option label="机构" value="ORG" />
        <el-option label="指定人" value="USER" />
      </el-select>

      <!-- ROLE：一次性加载角色列表 -->
      <el-select
        v-if="row.approverType === 'ROLE'"
        v-model="row.approverValue"
        style="flex: 1"
        placeholder="请选择角色"
        filterable
        @change="onValueChange"
      >
        <el-option
          v-for="r in roleOptions"
          :key="r.roleCode"
          :label="r.roleChName"
          :value="r.roleCode"
        />
      </el-select>

      <!-- ORG：一次性加载机构平铺列表（树拍平） -->
      <el-select
        v-else-if="row.approverType === 'ORG'"
        v-model="row.approverValue"
        style="flex: 1"
        placeholder="请选择机构"
        filterable
        @change="onValueChange"
      >
        <el-option
          v-for="o in orgOptions"
          :key="o.code"
          :label="o.name"
          :value="o.code"
        />
      </el-select>

      <!-- USER：关键字远程搜索员工 -->
      <el-select
        v-else-if="row.approverType === 'USER'"
        v-model="row.approverValue"
        style="flex: 1"
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
          :label="u.name + (u.org ? ' · ' + u.org : '')"
          :value="u.id"
        />
      </el-select>

      <!-- 占位：类型未选时 -->
      <el-input
        v-else
        disabled
        placeholder="请先选择类型"
        style="flex: 1"
      />

      <!-- 删除按钮 -->
      <el-button
        type="danger"
        text
        style="flex-shrink: 0; margin-left: 4px"
        @click="removeRow(idx)"
      >删除</el-button>
    </div>

    <!-- 添加一行 -->
    <el-button
      type="primary"
      text
      style="margin-top: 6px"
      @click="addRow"
    >+ 添加审批人</el-button>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listRoles } from '@/api/system';
import { getOrgTree } from '@/api/orgs';
import { searchEmployees } from '@/api/employees';

// ---- props / emits ----
const props = defineProps({
  /** v-model 绑定的审批人规则数组，每项 {approverType: 'ROLE'|'ORG'|'USER', approverValue: string} */
  modelValue: { type: Array, default: () => [] }
});
const emit = defineEmits(['update:modelValue']);

// ---- 本地副本（深拷贝，避免直接改 props） ----
const localList = ref([]);

// 初始化 & 外部变更同步
watch(
  () => props.modelValue,
  (val) => {
    localList.value = (val || []).map(r => ({ ...r }));
  },
  { immediate: true, deep: true }
);

// ---- 角色选项（onMounted 拉取一次） ----
const roleOptions = ref([]); // [{roleCode, roleChName}]

// ---- 机构选项（树拍平，onMounted 拉取一次） ----
const orgOptions = ref([]); // [{code, name}]

/** 将机构树递归拍平为数组 */
function flattenOrgTree(nodes, result = []) {
  for (const node of nodes || []) {
    result.push({ code: node.code, name: node.name });
    if (node.children?.length) flattenOrgTree(node.children, result);
  }
  return result;
}

// ---- 人员搜索（远程，按输入关键字） ----
const userSearchOptions = ref([]); // [{id, name, org}]
const userSearchLoading = ref(false);

/** 远程搜索员工（防空关键字） */
async function onUserSearch(keyword) {
  if (!keyword?.trim()) {
    userSearchOptions.value = [];
    return;
  }
  userSearchLoading.value = true;
  try {
    userSearchOptions.value = await searchEmployees(keyword.trim());
  } catch {
    ElMessage.warning('员工搜索失败，请重试');
    userSearchOptions.value = [];
  } finally {
    userSearchLoading.value = false;
  }
}

// ---- 初始加载角色 + 机构 ----
onMounted(async () => {
  // 角色列表
  try {
    const roles = await listRoles({ pageSize: 200 });
    roleOptions.value = (Array.isArray(roles) ? roles : []).map(r => ({
      roleCode: r.roleCode || r.code || '',
      roleChName: r.roleChName || r.roleChname || r.name || r.roleCode || ''
    }));
  } catch {
    ElMessage.warning('角色列表加载失败');
  }

  // 机构树拍平
  try {
    const tree = await getOrgTree();
    orgOptions.value = flattenOrgTree(tree);
  } catch {
    ElMessage.warning('机构列表加载失败');
  }
});

// ---- 行操作 ----

/** 添加一行（默认 ROLE 类型） */
function addRow() {
  localList.value = [...localList.value, { approverType: 'ROLE', approverValue: '' }];
  emitUpdate();
}

/** 删除指定行 */
function removeRow(idx) {
  const arr = localList.value.filter((_, i) => i !== idx);
  localList.value = arr;
  emitUpdate();
}

/** 类型切换时清空 approverValue */
function onTypeChange(idx) {
  localList.value[idx].approverValue = '';
  // 切到 USER 时同时清空搜索结果
  if (localList.value[idx].approverType === 'USER') {
    userSearchOptions.value = [];
  }
  emitUpdate();
}

/** 值变更时向父组件 emit */
function onValueChange() {
  emitUpdate();
}

/** 深拷贝后 emit，确保父组件收到新引用 */
function emitUpdate() {
  emit('update:modelValue', localList.value.map(r => ({ ...r })));
}
</script>

<style scoped>
.approver-picker {
  width: 100%;
}
.approver-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
