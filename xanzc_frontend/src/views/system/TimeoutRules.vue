<template>
  <div>
    <div class="page-h">
      <div>
        <h1>流程超时规则配置</h1>
        <p class="sub">管理所有业务流程节点的红绿灯超时阈值</p>
      </div>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate">+ 新增</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="流程定义Key">
          <el-select
            v-model="filterKey"
            clearable
            filterable
            placeholder="全部"
            style="width: 260px"
            @change="reload"
          >
            <el-option
              v-for="k in processKeyOptions"
              :key="k"
              :label="k"
              :value="k"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default" empty-text="暂无超时规则" v-loading="loading">
        <el-table-column label="流程定义Key" min-width="220">
          <template #default="{row}">
            <code class="mono">{{ row.processDefinitionKey }}</code>
          </template>
        </el-table-column>
        <el-table-column label="节点Key" min-width="180">
          <template #default="{row}">
            <code class="mono">{{ row.nodeKey }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="nodeName" label="节点名称" min-width="160" />
        <el-table-column label="黄灯阈值(工作时)" width="160">
          <template #default="{row}">
            <el-tag class="tag-warning" effect="plain">{{ row.warningHours }} h</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="红灯阈值(工作时)" width="160">
          <template #default="{row}">
            <el-tag class="tag-danger" effect="plain">{{ row.timeoutHours }} h</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="180" :formatter="fmtDateTimeCol" />
        <el-table-column label="操作" width="80">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑超时规则' : '新增超时规则'"
      width="500px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="130px"
        size="default"
      >
        <el-form-item label="流程定义Key" prop="processDefinitionKey">
          <el-input
            v-model="form.processDefinitionKey"
            :disabled="isEdit"
            placeholder="如 LEAD_APPROVAL"
            clearable
          />
        </el-form-item>
        <el-form-item label="节点Key" prop="nodeKey">
          <el-input
            v-model="form.nodeKey"
            :disabled="isEdit"
            placeholder="如 manager_review"
            clearable
          />
        </el-form-item>
        <el-form-item label="节点名称" prop="nodeName">
          <el-input v-model="form.nodeName" placeholder="如 经理审批" clearable />
        </el-form-item>
        <el-form-item label="黄灯阈值" prop="warningHours">
          <el-input-number
            v-model="form.warningHours"
            :min="1"
            :max="9999"
            style="width: 160px"
          />
          <span class="hint">单位：工作小时</span>
        </el-form-item>
        <el-form-item label="红灯阈值" prop="timeoutHours">
          <el-input-number
            v-model="form.timeoutHours"
            :min="1"
            :max="9999"
            style="width: 160px"
          />
          <span class="hint">单位：工作小时</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTimeCol } from '@/utils/datetime';
import { listTimeoutRules, updateTimeoutRule, createTimeoutRule } from '@/api/system';

// ── 列表 ────────────────────────────────────────────────────────────
const rows = ref([]);
const loading = ref(false);
const filterKey = ref('');

/** 从已加载数据去重，生成流程Key下拉选项 */
const allRows = ref([]); // 全量（无过滤）用于生成下拉
const processKeyOptions = computed(() => {
  const keys = allRows.value.map(r => r.processDefinitionKey).filter(Boolean);
  return [...new Set(keys)].sort();
});

async function reload() {
  loading.value = true;
  try {
    const data = await listTimeoutRules(filterKey.value || undefined);
    if (Array.isArray(data)) {
      rows.value = data;
      // 只在无过滤时刷新全量选项列表
      if (!filterKey.value) allRows.value = data;
    }
  } catch {
    // http.js 已统一弹错，无需重复
  } finally {
    loading.value = false;
  }
}

// ── 弹窗表单 ─────────────────────────────────────────────────────────
const dialogVisible = ref(false);
const isEdit = ref(false);
const saving = ref(false);
const formRef = ref(null);
const editId = ref(null);

const form = ref({
  processDefinitionKey: '',
  nodeKey: '',
  nodeName: '',
  warningHours: 24,
  timeoutHours: 48,
});

const rules = {
  processDefinitionKey: [{ required: true, message: '请输入流程定义Key', trigger: 'blur' }],
  nodeKey: [{ required: true, message: '请输入节点Key', trigger: 'blur' }],
  nodeName: [{ required: true, message: '请输入节点名称', trigger: 'blur' }],
  warningHours: [
    { required: true, message: '请输入黄灯阈值', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value >= form.value.timeoutHours) {
          callback(new Error('黄灯阈值必须小于红灯阈值'));
        } else {
          callback();
        }
      },
      trigger: 'blur'
    }
  ],
  timeoutHours: [
    { required: true, message: '请输入红灯阈值', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value <= form.value.warningHours) {
          callback(new Error('红灯阈值必须大于黄灯阈值'));
        } else {
          callback();
        }
      },
      trigger: 'blur'
    }
  ],
};

function openCreate() {
  isEdit.value = false;
  editId.value = null;
  form.value = { processDefinitionKey: '', nodeKey: '', nodeName: '', warningHours: 24, timeoutHours: 48 };
  dialogVisible.value = true;
}

function openEdit(row) {
  isEdit.value = true;
  editId.value = row.id;
  form.value = {
    processDefinitionKey: row.processDefinitionKey,
    nodeKey: row.nodeKey,
    nodeName: row.nodeName,
    warningHours: row.warningHours,
    timeoutHours: row.timeoutHours,
  };
  dialogVisible.value = true;
}

function resetForm() {
  formRef.value?.resetFields();
}

async function onSubmit() {
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      await updateTimeoutRule(editId.value, {
        warningHours: form.value.warningHours,
        timeoutHours: form.value.timeoutHours,
      });
      ElMessage.success('更新成功');
    } else {
      await createTimeoutRule({ ...form.value });
      ElMessage.success('新增成功');
    }
    dialogVisible.value = false;
    reload();
  } catch {
    // http.js 已统一弹错
  } finally {
    saving.value = false;
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.sub {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 2px 0 0;
}
.mono {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  background: $bg-soft;
  padding: 2px 6px;
  border-radius: 3px;
}
.hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
