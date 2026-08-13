<template>
  <main
    v-bp-overflow-tooltip
    class="bp-crud timeout-rules-page"
    aria-labelledby="timeout-rules-page-title"
    :aria-busy="loading ? 'true' : 'false'"
  >
    <header class="page-h">
      <PageTitle id="timeout-rules-page-title">
        <span class="sub">管理业务流程节点的预警与超时阈值，时间单位为工作小时。</span>
      </PageTitle>
      <div class="actions action-group" role="group" aria-label="超时规则操作">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增超时规则</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="超时规则筛选">
      <el-form class="filter-form" inline size="default" aria-label="超时规则筛选条件" @submit.prevent="onSearch">
        <el-form-item label="流程定义 Key">
          <el-select
            v-model="filterKey"
            clearable
            filterable
            placeholder="全部流程"
            style="width:280px"
            aria-label="按流程定义 Key 筛选"
            @change="onSearch"
          >
            <el-option v-for="key in processKeyOptions" :key="key" :label="key" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section
      class="card-section data-panel timeout-table-panel"
      aria-label="超时规则列表"
      aria-labelledby="timeout-rules-heading"
      aria-describedby="timeout-rules-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="timeout-rules-heading" class="section-title">节点 SLA 阈值</h2>
          <p class="hint">黄灯阈值必须小于红灯阈值；规则会按流程定义与节点标识精确匹配。</p>
        </div>
        <p id="timeout-rules-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '超时规则加载中' : rows.length ? `共 ${total} 条超时规则` : '暂无超时规则' }}
        </p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">
        {{ loadError }} <el-button link type="primary" @click="reload">重试</el-button>
      </p>
      <el-table
        :data="rows"
        size="default"
        empty-text="暂无超时规则"
        v-loading="loading"
        aria-labelledby="timeout-rules-heading"
        aria-describedby="timeout-rules-state"
      >
        <el-table-column label="流程定义 Key" min-width="220"><template #default="{ row }"><code class="mono">{{ row.processDefinitionKey }}</code></template></el-table-column>
        <el-table-column label="节点 Key" min-width="180"><template #default="{ row }"><code class="mono">{{ row.nodeKey }}</code></template></el-table-column>
        <el-table-column prop="nodeName" label="节点名称" min-width="160"><template #default="{ row }">{{ row.nodeName || '-' }}</template></el-table-column>
        <el-table-column label="黄灯阈值" width="150" align="right"><template #default="{ row }"><el-tag class="tag-warning" effect="plain">{{ row.warningHours }} h</el-tag></template></el-table-column>
        <el-table-column label="红灯阈值" width="150" align="right"><template #default="{ row }"><el-tag class="tag-danger" effect="plain">{{ row.timeoutHours }} h</el-tag></template></el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="180" :formatter="fmtDateTimeCol" />
        <el-table-column label="操作" width="88" fixed="right"><template #default="{ row }"><el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialogVisible"
      class="bp-crud-dialog"
      :title="isEdit ? '编辑超时规则' : '新增超时规则'"
      width="520px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="132px" size="default" aria-label="超时规则表单">
        <el-form-item label="流程定义 Key" prop="processDefinitionKey"><el-input v-model="form.processDefinitionKey" :disabled="isEdit" placeholder="如 LEAD_APPROVAL" clearable /></el-form-item>
        <el-form-item label="节点 Key" prop="nodeKey"><el-input v-model="form.nodeKey" :disabled="isEdit" placeholder="如 manager_review" clearable /></el-form-item>
        <el-form-item label="节点名称" prop="nodeName"><el-input v-model="form.nodeName" placeholder="如 经理审批" clearable /></el-form-item>
        <el-form-item label="黄灯阈值" prop="warningHours">
          <el-input-number v-model="form.warningHours" :min="1" :max="9999" style="width:160px" />
          <span class="hint inline-hint">工作小时</span>
        </el-form-item>
        <el-form-item label="红灯阈值" prop="timeoutHours">
          <el-input-number v-model="form.timeoutHours" :min="1" :max="9999" style="width:160px" />
          <span class="hint inline-hint">工作小时</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTimeCol } from '@/utils/datetime';
import { listTimeoutRules, updateTimeoutRule, createTimeoutRule } from '@/api/system';

const rows = ref([]);
const allRows = ref([]);
const total = computed(() => rows.value.length);
const loading = ref(false);
const loadError = ref('');
const filterKey = ref('');
const processKeyOptions = computed(() => [...new Set(allRows.value.map((row) => row.processDefinitionKey).filter(Boolean))].sort());

async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const data = await listTimeoutRules(filterKey.value || undefined);
    rows.value = Array.isArray(data) ? data : [];
    if (!filterKey.value) allRows.value = rows.value;
  } catch (error) {
    rows.value = [];
    loadError.value = `超时规则加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}
function onSearch() { reload(); }
function resetFilters() {
  if (!filterKey.value) return reload();
  filterKey.value = '';
  reload();
}

const dialogVisible = ref(false);
const isEdit = ref(false);
const saving = ref(false);
const formRef = ref(null);
const editId = ref(null);
const form = ref({ processDefinitionKey: '', nodeKey: '', nodeName: '', warningHours: 24, timeoutHours: 48 });
const rules = {
  processDefinitionKey: [{ required: true, message: '请输入流程定义 Key', trigger: 'blur' }],
  nodeKey: [{ required: true, message: '请输入节点 Key', trigger: 'blur' }],
  nodeName: [{ required: true, message: '请输入节点名称', trigger: 'blur' }],
  warningHours: [{ required: true, message: '请输入黄灯阈值', trigger: 'blur' }, {
    validator: (_rule, value, callback) => (value >= form.value.timeoutHours ? callback(new Error('黄灯阈值必须小于红灯阈值')) : callback()), trigger: 'blur'
  }],
  timeoutHours: [{ required: true, message: '请输入红灯阈值', trigger: 'blur' }, {
    validator: (_rule, value, callback) => (value <= form.value.warningHours ? callback(new Error('红灯阈值必须大于黄灯阈值')) : callback()), trigger: 'blur'
  }]
};

function openCreate() {
  if (saving.value) return;
  isEdit.value = false;
  editId.value = null;
  form.value = { processDefinitionKey: filterKey.value || '', nodeKey: '', nodeName: '', warningHours: 24, timeoutHours: 48 };
  dialogVisible.value = true;
}
function openEdit(row) {
  if (saving.value) return;
  isEdit.value = true;
  editId.value = row.id;
  form.value = {
    processDefinitionKey: row.processDefinitionKey,
    nodeKey: row.nodeKey,
    nodeName: row.nodeName,
    warningHours: row.warningHours,
    timeoutHours: row.timeoutHours
  };
  dialogVisible.value = true;
}
function resetForm() { formRef.value?.resetFields?.(); }

async function onSubmit() {
  if (saving.value) return;
  try {
    await formRef.value?.validate?.();
  } catch {
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      await updateTimeoutRule(editId.value, { warningHours: form.value.warningHours, timeoutHours: form.value.timeoutHours });
      ElMessage.success('超时规则已更新');
    } else {
      await createTimeoutRule({ ...form.value });
      ElMessage.success('超时规则已新增');
    }
    dialogVisible.value = false;
    await reload();
  } catch (error) {
    ElMessage.error(`${isEdit.value ? '更新' : '新增'}失败：${error?.message || '请稍后重试'}`);
  } finally {
    saving.value = false;
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.mono {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  color: var(--color-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  padding: 2px 6px;
}
.inline-hint { margin-left: var(--space-2); }
</style>
