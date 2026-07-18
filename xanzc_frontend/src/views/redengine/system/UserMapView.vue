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

      <el-table :data="mapList" stripe style="width: 100%">
        <el-table-column prop="userId" label="用户工号" width="140" />
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
    </el-card>

    <!-- 新增/编辑（绑定）对话框：后端只有 bind()（POST /api/re/user-party-maps，按 userId upsert），
         无独立更新/删除端点，编辑时禁用 userId 输入以避免误改绑定到别的工号 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑映射' : '新增映射'" width="480px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-form-item label="用户工号" prop="userId">
          <el-input v-model="formData.userId" placeholder="请输入平台用户工号" :disabled="isEdit" />
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
// RE_SUBMIT_STATUS/RE_ITEM_CODE 四类，无角色字典），故改核实后端权威口径：
// entity/ReUserPartyMap.java、api/dto/ReUserPartyMapDTO.java 的字段注释与 Schema 描述明文列出
// ORG_REVIEWER/BRANCH_REVIEWER/SECRETARY/REPORTER 四个字符串字面量（无更严格的后端枚举校验，
// 仅 @NotBlank）；ReUserPartyMapServiceTest.java 测试夹具实际只覆盖了 SECRETARY/REPORTER 两值
// （ORG_REVIEWER/BRANCH_REVIEWER 未见于任何测试断言，如实记录不夸大覆盖范围），故本页 4 项取值以
// entity/DTO 注释为准，硬编码这 4 项 + 中文标签，不虚构字典来源。
import { ref, onMounted, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { listUserMaps, bindUserMap, getOrgTree } from '@/api/redengine';

const loading = ref(false);
const submitting = ref(false);
const mapList = ref([]);
const orgTree = ref([]);
const dialogVisible = ref(false);
const isEdit = ref(false);
const formRef = ref(null);

const partyRoleOptions = [
  { value: 'ORG_REVIEWER', label: '组织审核员' },
  { value: 'BRANCH_REVIEWER', label: '支部审核员' },
  { value: 'SECRETARY', label: '支部书记' },
  { value: 'REPORTER', label: '报送员' }
];

function emptyForm() {
  return { userId: '', partyOrgId: null, partyRole: '' };
}
const formData = ref(emptyForm());

const rules = {
  userId: [{ required: true, message: '请输入用户工号', trigger: 'blur' }],
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

async function loadAll() {
  loading.value = true;
  try {
    const [maps, tree] = await Promise.all([listUserMaps(), getOrgTree()]);
    mapList.value = maps || [];
    orgTree.value = tree || [];
  } finally {
    loading.value = false;
  }
}
onMounted(loadAll);

const handleAdd = () => {
  isEdit.value = false;
  formData.value = emptyForm();
  dialogVisible.value = true;
};

const handleEdit = (row) => {
  isEdit.value = true;
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
      await loadAll();
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
  }
}
</style>
