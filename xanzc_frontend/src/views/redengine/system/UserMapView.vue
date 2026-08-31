<template>
  <div class="user-map-container">
    <h1 class="page-title">用户党组织映射</h1>

    <el-card class="list-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span class="card-title">映射列表</span>
          <el-button type="primary" size="small" @click="handleAdd">
            + 新增映射
          </el-button>
        </div>
      </template>

      <el-form class="query-form" :model="queryForm" label-position="top" @keyup.enter="handleSearch">
        <el-form-item label="用户工号">
          <el-input v-model="queryForm.username" placeholder="请输入用户工号" clearable />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="queryForm.displayName" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item label="党组织">
          <el-select v-model="queryForm.partyOrgId" placeholder="请选择党组织" clearable filterable>
            <el-option v-for="opt in orgOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="党内角色">
          <el-select v-model="queryForm.partyRole" placeholder="请选择党内角色" clearable>
            <el-option v-for="opt in partyRoleOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <div class="query-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </el-form>

      <el-table :data="mapList" stripe style="width: 100%">
        <el-table-column prop="username" label="用户工号" width="140" />
        <el-table-column prop="displayName" label="姓名" width="140" />
        <el-table-column label="党组织" min-width="180">
          <template #default="{ row }">
            {{ orgNameOf(row.partyOrgId) }}
          </template>
        </el-table-column>
        <el-table-column label="党内角色" width="140">
          <template #default="{ row }">
            {{ roleLabelOf(row.partyRole) }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && mapList.length === 0" description="暂无映射数据" />

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑（绑定）对话框：后端只有 bind()（POST /api/re/user-party-maps，按 userId upsert），
         无独立更新/删除端点，编辑时禁用 userId 输入以避免误改绑定到别的工号 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑映射' : '新增映射'" width="480px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-form-item label="用户工号" prop="userId">
          <el-select
            v-model="formData.userId"
            placeholder="请选择平台用户工号"
            filterable
            remote
            reserve-keyword
            :remote-method="loadUserOptions"
            :loading="userLoading"
            :disabled="isEdit"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.userId"
              :label="userOptionLabel(user)"
              :value="user.userId"
            >
              <span class="user-option-code">{{ user.username || '-' }}</span>
              <span class="user-option-name">{{ user.userchnname || '姓名未维护' }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="党组织" prop="partyOrgId">
          <el-select v-model="formData.partyOrgId" placeholder="请选择党组织" filterable style="width: 100%">
            <el-option v-for="opt in orgOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="党内角色" prop="partyRole">
          <el-select v-model="formData.partyRole" placeholder="请选择党内角色" style="width: 100%">
            <el-option v-for="opt in partyRoleOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 红色引擎（党建）用户党组织映射管理：Task 15 新写（源系统 redengine 无对应页面，
// 平台 RE_USER_PARTY_MAP 表 + ReUserPartyMapController 是本任务后端契约的唯一来源）。
//
// 接口：api/redengine.js（Task 14 已建）listUserMaps()/bindUserMap()，均已在 red-engine-center
// 落地（ReUserPartyMapController：GET/POST /api/re/user-party-maps，对应种子资源
// P_RE_MAP_LIST/P_RE_MAP_BIND）。bind() 是按 userId 的 upsert（uk_user 唯一约束），后端未提供
// 独立的更新/删除端点——本页"编辑"复用同一个 bind 调用，且编辑态下禁用 userId 输入框防止误改到
// 别的工号；"删除映射"能力本任务未实现（后端无 DELETE 端点，已记档于 task-15-report.md）。
//
// 党内角色下拉取值来源：种子 SQL(docs/superpowers/sql/2026-07-18-redengine-seed.sql) 的
// SYS_DICT 未登记 partyRole 相关字典（RE_ 前缀字典只有 RE_ORG_TYPE/RE_DIMENSION/
// RE_SUBMIT_STATUS/RE_ITEM_CODE 四类，无角色字典），故按红色引擎当前角色模型提供
// ORG_REVIEWER/SECRETARY/REPORTER 三项。支部书记已承接原支部审核职责，前端不再提供
// BRANCH_REVIEWER 选项；partyRole 仍只是映射描述字段，实际接口授权以平台角色为准。
import { ref, onMounted, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { listUserMaps, bindUserMap, getOrgTree } from '@/api/redengine';
import { listUsers } from '@/api/users';

const loading = ref(false);
const submitting = ref(false);
const userLoading = ref(false);
const mapList = ref([]);
const orgTree = ref([]);
const userOptions = ref([]);
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);
const dialogVisible = ref(false);
const isEdit = ref(false);
const formRef = ref(null);

const partyRoleOptions = [
  { value: 'ORG_REVIEWER', label: '组织审核员' },
  { value: 'SECRETARY', label: '支部书记' },
  { value: 'REPORTER', label: '报送员' }
];

function emptyForm() {
  return { userId: '', partyOrgId: null, partyRole: '' };
}
const formData = ref(emptyForm());

function emptyQuery() {
  return { username: '', displayName: '', partyOrgId: null, partyRole: '' };
}
const queryForm = ref(emptyQuery());

const rules = {
  userId: [{ required: true, message: '请选择用户工号', trigger: 'change' }],
  partyOrgId: [{ required: true, message: '请选择党组织', trigger: 'change' }],
  partyRole: [{ required: true, message: '请选择党内角色', trigger: 'change' }]
};

// 党组织树拍平：{ value: id, label: 带层级缩进的 orgName }，供下拉展示层级关系；
// 同时保留 id → orgName 的原始映射供表格列展示
const orgFlat = computed(() => {
  const flat = [];
  const walk = (nodes, depth) => {
    for (const n of nodes || []) {
      flat.push({ id: n.id, orgName: n.orgName, depth });
      walk(n.children, depth + 1);
    }
  };
  walk(orgTree.value, 0);
  return flat;
});

const orgOptions = computed(() =>
  orgFlat.value.map((o) => ({ value: o.id, label: '　'.repeat(o.depth) + o.orgName }))
);

function orgNameOf(id) {
  const hit = orgFlat.value.find((o) => o.id === id);
  return hit ? hit.orgName : (id ?? '-');
}

function roleLabelOf(code) {
  const hit = partyRoleOptions.find((o) => o.value === code);
  return hit ? hit.label : (code || '-');
}

function buildMapQuery() {
  const params = { pageNo: pageNo.value, pageSize: pageSize.value };
  const username = String(queryForm.value.username || '').trim();
  const displayName = String(queryForm.value.displayName || '').trim();
  if (username) params.username = username;
  if (displayName) params.displayName = displayName;
  if (queryForm.value.partyOrgId !== null && queryForm.value.partyOrgId !== '') {
    params.partyOrgId = Number(queryForm.value.partyOrgId);
  }
  if (queryForm.value.partyRole) params.partyRole = queryForm.value.partyRole;
  return params;
}

async function loadMappings() {
  loading.value = true;
  try {
    const result = await listUserMaps(buildMapQuery());
    if (Array.isArray(result)) {
      mapList.value = result;
      total.value = result.length;
      return;
    }
    mapList.value = result?.records || [];
    total.value = Number(result?.total || 0);
    if (Number.isFinite(Number(result?.pageNo))) pageNo.value = Number(result.pageNo);
  } finally {
    loading.value = false;
  }
}

async function loadAll() {
  const [, tree] = await Promise.all([loadMappings(), getOrgTree()]);
  orgTree.value = tree || [];
}
onMounted(loadAll);

async function handleSearch() {
  pageNo.value = 1;
  await loadMappings();
}

async function handleReset() {
  queryForm.value = emptyQuery();
  pageNo.value = 1;
  await loadMappings();
}

async function handleCurrentChange(nextPage) {
  pageNo.value = nextPage;
  await loadMappings();
}

async function handleSizeChange(nextPageSize) {
  pageSize.value = nextPageSize;
  pageNo.value = 1;
  await loadMappings();
}

let userSearchSequence = 0;
async function loadUserOptions(keyword = '') {
  const sequence = ++userSearchSequence;
  userLoading.value = true;
  try {
    const params = { pageNo: 1, pageSize: 100 };
    const normalizedKeyword = String(keyword || '').trim();
    if (normalizedKeyword) params.username = normalizedKeyword;
    const result = await listUsers(params);
    if (sequence !== userSearchSequence) return;
    userOptions.value = Array.isArray(result) ? result : (result?.records || []);
  } finally {
    if (sequence === userSearchSequence) userLoading.value = false;
  }
}

function userOptionLabel(user) {
  const username = user?.username || '-';
  const displayName = user?.userchnname || '姓名未维护';
  return `${username} · ${displayName}`;
}

const handleAdd = () => {
  isEdit.value = false;
  formData.value = emptyForm();
  void loadUserOptions();
  dialogVisible.value = true;
};

const handleEdit = (row) => {
  isEdit.value = true;
  if (!userOptions.value.some((user) => user.userId === row.userId)) {
    userOptions.value = [
      { userId: row.userId, username: row.username, userchnname: row.displayName },
      ...userOptions.value
    ];
  }
  formData.value = { userId: row.userId, partyOrgId: row.partyOrgId, partyRole: row.partyRole };
  dialogVisible.value = true;
};

const handleDialogClose = () => {
  formRef.value?.resetFields();
};

const handleFormSubmit = () => {
  formRef.value?.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
      await bindUserMap({ ...formData.value });
      ElMessage.success(isEdit.value ? '映射已更新' : '映射已绑定');
      dialogVisible.value = false;
      await loadMappings();
    } catch (e) {
      // http.js 响应拦截器已对业务失败弹出错误提示，这里不重复
    } finally {
      submitting.value = false;
    }
  });
};
</script>

<style scoped lang="scss">
.user-map-container {
  .page-title {
    font-size: 24px;
    margin-bottom: 20px;
    color: #2c3e50;
  }

  .list-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .card-title {
        font-size: 16px;
        font-weight: 600;
      }
    }

    .query-form {
      display: grid;
      grid-template-columns: repeat(4, minmax(150px, 1fr)) auto;
      gap: 12px;
      align-items: end;
      margin-bottom: 18px;

      :deep(.el-form-item) {
        margin-bottom: 0;
      }

      :deep(.el-select) {
        width: 100%;
      }
    }

    .query-actions {
      display: flex;
      align-items: center;
      padding-bottom: 1px;
      white-space: nowrap;
    }

    .pagination-wrap {
      display: flex;
      justify-content: flex-end;
      margin-top: 18px;

      :deep(.el-pagination) {
        flex-wrap: wrap;
        justify-content: flex-end;
        row-gap: 8px;
      }
    }
  }

  .user-option-code {
    font-weight: 600;
  }

  .user-option-name {
    margin-left: 12px;
    color: var(--el-text-color-secondary);
  }
}

@media (max-width: 1100px) {
  .user-map-container .list-card .query-form {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
}

@media (max-width: 640px) {
  .user-map-container .list-card .query-form {
    grid-template-columns: 1fr;
  }

  .user-map-container .list-card .query-actions {
    justify-content: flex-end;
  }
}
</style>
