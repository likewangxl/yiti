<template>
  <main
    v-bp-overflow-tooltip
    class="bp-crud workflow-config-page"
    aria-labelledby="workflow-config-page-title"
    :aria-busy="loading ? 'true' : 'false'"
  >
    <header class="page-h">
      <PageTitle id="workflow-config-page-title" title="流程配置">
        <span class="sub">维护已部署流程的节点候选人、表单字段与 SLA 阈值。</span>
      </PageTitle>
    </header>

    <section class="card-section filter-bar" aria-label="流程定义筛选">
      <el-form class="filter-form" inline size="default" aria-label="流程定义筛选条件" @submit.prevent="reload">
        <el-form-item label="流程定义">
          <el-select
            v-model="selectedPd"
            filterable
            placeholder="选择流程"
            style="width:320px"
            aria-label="选择需要配置的流程定义"
            @change="reload"
          >
            <el-option
              v-for="p in processDefs"
              :key="p.processDefinitionKey"
              :value="p.processDefinitionKey"
              :label="`${p.processDefinitionName || p.processDefinitionKey}（${p.processDefinitionKey}）`"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!selectedPd" @click="reload">查询</el-button>
          <el-button @click="loadProcessDefs">刷新流程定义</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section
      class="card-section data-panel config-panel"
      aria-label="流程节点配置"
      aria-labelledby="workflow-config-heading"
      aria-describedby="workflow-config-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="workflow-config-heading" class="section-title">{{ activeMeta.title }}</h2>
          <p class="hint">{{ activeMeta.hint }}</p>
        </div>
        <p id="workflow-config-state" class="table-state" role="status" aria-live="polite">
          {{ stateText }}
        </p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">
        {{ loadError }} <el-button link type="primary" @click="reload">重试</el-button>
      </p>

      <el-tabs v-model="activeTab" class="config-tabs" @tab-change="reload">
        <el-tab-pane label="节点候选人" name="candidates">
          <div class="toolbar tab-toolbar">
            <p class="hint">候选规则按流程定义与节点标识精确匹配，修改后仅影响后续任务的候选范围。</p>
            <el-button type="primary" :disabled="!selectedPd" @click="openCandidateDlg()">新增候选人</el-button>
          </div>
          <el-table
            :data="candidates"
            size="default"
            v-loading="loading"
            empty-text="暂无节点候选人配置"
            aria-labelledby="workflow-config-heading"
            aria-describedby="workflow-config-state"
          >
            <el-table-column prop="nodeKey" label="节点 Key" min-width="190"><template #default="{ row }"><code class="mono">{{ row.nodeKey }}</code></template></el-table-column>
            <el-table-column prop="candidateType" label="候选类型" width="120"><template #default="{ row }"><el-tag class="tag-info" effect="plain" size="small">{{ row.candidateType }}</el-tag></template></el-table-column>
            <el-table-column prop="candidateValue" label="候选值" min-width="260" show-overflow-tooltip />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }"><el-button link type="primary" size="small" @click="openCandidateDlg(row)">编辑</el-button></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="节点表单" name="forms">
          <div class="toolbar tab-toolbar">
            <p class="hint">字段定义使用后端约定的 JSON 格式；保存前请确认必填字段与只读字段的边界。</p>
            <el-button type="primary" :disabled="!selectedPd" @click="openFormDlg()">新增节点表单</el-button>
          </div>
          <el-table
            :data="forms"
            size="default"
            v-loading="loading"
            empty-text="暂无节点表单配置"
            aria-labelledby="workflow-config-heading"
            aria-describedby="workflow-config-state"
          >
            <el-table-column prop="nodeKey" label="节点 Key" min-width="190"><template #default="{ row }"><code class="mono">{{ row.nodeKey }}</code></template></el-table-column>
            <el-table-column prop="formFields" label="表单字段定义" min-width="300" show-overflow-tooltip />
            <el-table-column prop="readableFields" label="只读字段" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }"><el-button link type="primary" size="small" @click="openFormDlg(row)">编辑</el-button></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="超时规则（SLA）" name="timeout">
          <div class="toolbar tab-toolbar">
            <p class="hint">黄灯用于预警，红灯表示已超时；阈值单位为工作小时。</p>
            <el-button type="primary" :disabled="!selectedPd" @click="openTimeoutDlg()">新增超时规则</el-button>
          </div>
          <el-table
            :data="timeoutRules"
            size="default"
            v-loading="loading"
            empty-text="暂无超时规则"
            aria-labelledby="workflow-config-heading"
            aria-describedby="workflow-config-state"
          >
            <el-table-column prop="nodeKey" label="节点 Key" min-width="190"><template #default="{ row }"><code class="mono">{{ row.nodeKey }}</code></template></el-table-column>
            <el-table-column prop="warningHours" label="黄灯（工作小时）" width="150" align="right"><template #default="{ row }"><el-tag class="tag-warning" effect="plain" size="small">{{ row.warningHours }} h</el-tag></template></el-table-column>
            <el-table-column prop="timeoutHours" label="红灯（工作小时）" width="150" align="right"><template #default="{ row }"><el-tag class="tag-danger" effect="plain" size="small">{{ row.timeoutHours }} h</el-tag></template></el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }"><el-button link type="primary" size="small" @click="openTimeoutDlg(row)">编辑</el-button></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="candidateDlg.show" class="bp-crud-dialog" :title="candidateDlg.editing ? '编辑节点候选人' : '新增节点候选人'" width="560px" :close-on-click-modal="false">
      <el-form label-width="100px" aria-label="节点候选人表单">
        <el-form-item label="节点 Key"><el-input v-model="candidateDlg.form.nodeKey" placeholder="如 branch_approve" /></el-form-item>
        <el-form-item label="候选类型">
          <el-select v-model="candidateDlg.form.candidateType" style="width:100%">
            <el-option value="ROLE" label="ROLE - 角色" />
            <el-option value="USER" label="USER - 指定用户" />
            <el-option value="ORG" label="ORG - 机构" />
          </el-select>
        </el-form-item>
        <el-form-item label="候选值"><el-input v-model="candidateDlg.form.candidateValue" type="textarea" :rows="3" placeholder='JSON 数组，如 ["BRANCH_HEAD","CORP_DEPT"]' /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="candidateDlg.saving" @click="candidateDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="candidateDlg.saving" :disabled="candidateDlg.saving" @click="saveCandidate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="formDlg.show" class="bp-crud-dialog" :title="formDlg.editing ? '编辑节点表单' : '新增节点表单'" width="640px" :close-on-click-modal="false">
      <el-form label-width="100px" aria-label="节点表单配置">
        <el-form-item label="节点 Key"><el-input v-model="formDlg.form.nodeKey" placeholder="如 biz_dept_review" /></el-form-item>
        <el-form-item label="表单字段"><el-input v-model="formDlg.form.formFields" type="textarea" :rows="4" placeholder='JSON 数组，如 [{"key":"opinion","label":"审批意见","type":"TEXTAREA"}]' /></el-form-item>
        <el-form-item label="必填字段"><el-input v-model="formDlg.form.requiredFields" placeholder='JSON 数组，如 ["opinion"]' /></el-form-item>
        <el-form-item label="只读字段"><el-input v-model="formDlg.form.readableFields" placeholder='JSON 数组，如 ["opinion"]' /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="formDlg.saving" @click="formDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="formDlg.saving" :disabled="formDlg.saving" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="timeoutDlg.show" class="bp-crud-dialog" :title="timeoutDlg.editing ? '编辑超时规则' : '新增超时规则'" width="500px" :close-on-click-modal="false">
      <el-form label-width="120px" aria-label="超时规则表单">
        <el-form-item label="节点 Key"><el-input v-model="timeoutDlg.form.nodeKey" placeholder="如 branch_approve" /></el-form-item>
        <el-form-item label="黄灯（工作小时）"><el-input-number v-model="timeoutDlg.form.warningHours" :min="1" :step="1" /></el-form-item>
        <el-form-item label="红灯（工作小时）"><el-input-number v-model="timeoutDlg.form.timeoutHours" :min="1" :step="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="timeoutDlg.saving" @click="timeoutDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="timeoutDlg.saving" :disabled="timeoutDlg.saving" @click="saveTimeout">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  listProcessDefinitions,
  listNodeCandidates, createNodeCandidate, updateNodeCandidate,
  listNodeForms, createNodeForm, updateNodeForm,
  listTimeoutRules, createTimeoutRule, updateTimeoutRule
} from '@/api/system';

const TAB_META = {
  candidates: { title: '节点候选人', hint: '定义每个审批节点可处理任务的角色、用户或机构范围。' },
  forms: { title: '节点表单', hint: '定义审批环节可填写、必填和只读的业务字段。' },
  timeout: { title: '超时规则（SLA）', hint: '定义节点预警与超时阈值，单位为工作小时。' }
};

const processDefs = ref([]);
const selectedPd = ref('');
const activeTab = ref('candidates');
const loading = ref(false);
const loadError = ref('');

const candidates = ref([]);
const forms = ref([]);
const timeoutRules = ref([]);

const activeMeta = computed(() => TAB_META[activeTab.value] || TAB_META.candidates);
const activeRows = computed(() => ({ candidates: candidates.value, forms: forms.value, timeout: timeoutRules.value }[activeTab.value] || []));
const stateText = computed(() => {
  if (loading.value) return `${activeMeta.value.title}加载中`;
  if (!selectedPd.value) return '请选择流程定义';
  return activeRows.value.length ? `当前流程共 ${activeRows.value.length} 条配置` : `当前流程暂无${activeMeta.value.title}配置`;
});

async function loadProcessDefs() {
  try {
    const result = await listProcessDefinitions();
    const latestByKey = new Map();
    for (const item of (Array.isArray(result) ? result : [])) {
      const key = item?.processDefinitionKey;
      if (key && (!latestByKey.has(key) || (item.version || 0) > (latestByKey.get(key).version || 0))) latestByKey.set(key, item);
    }
    processDefs.value = [...latestByKey.values()];
    if (processDefs.value.length && !selectedPd.value) selectedPd.value = processDefs.value[0].processDefinitionKey;
  } catch (error) {
    processDefs.value = [];
    loadError.value = `流程定义加载失败：${error?.message || '请稍后重试'}`;
  }
}

async function reload() {
  if (!selectedPd.value) return;
  loading.value = true;
  loadError.value = '';
  try {
    if (activeTab.value === 'candidates') {
      const result = await listNodeCandidates(selectedPd.value);
      candidates.value = Array.isArray(result) ? result : [];
    } else if (activeTab.value === 'forms') {
      const result = await listNodeForms(selectedPd.value);
      forms.value = Array.isArray(result) ? result : [];
    } else {
      const result = await listTimeoutRules(selectedPd.value);
      timeoutRules.value = Array.isArray(result) ? result : [];
    }
  } catch (error) {
    loadError.value = `${activeMeta.value.title}加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}

function requireProcessDefinition() {
  if (selectedPd.value) return true;
  ElMessage.warning('请先选择流程定义');
  return false;
}

const candidateDlg = reactive({ show: false, editing: null, saving: false, form: {} });
function openCandidateDlg(row) {
  if (!requireProcessDefinition()) return;
  candidateDlg.editing = row?.id || null;
  candidateDlg.form = { nodeKey: row?.nodeKey || '', candidateType: row?.candidateType || 'ROLE', candidateValue: row?.candidateValue || '[]' };
  candidateDlg.show = true;
}
async function saveCandidate() {
  if (candidateDlg.saving || !requireProcessDefinition()) return;
  candidateDlg.saving = true;
  try {
    const payload = { processDefinitionKey: selectedPd.value, ...candidateDlg.form };
    if (candidateDlg.editing) await updateNodeCandidate(candidateDlg.editing, payload);
    else await createNodeCandidate(payload);
    ElMessage.success('节点候选人已保存');
    candidateDlg.show = false;
    await reload();
  } catch (error) {
    ElMessage.error(error?.message || '保存失败');
  } finally {
    candidateDlg.saving = false;
  }
}

const formDlg = reactive({ show: false, editing: null, saving: false, form: {} });
function openFormDlg(row) {
  if (!requireProcessDefinition()) return;
  formDlg.editing = row?.id || null;
  formDlg.form = {
    nodeKey: row?.nodeKey || '', formFields: row?.formFields || '[]',
    requiredFields: row?.requiredFields || '[]', readableFields: row?.readableFields || '[]'
  };
  formDlg.show = true;
}
async function saveForm() {
  if (formDlg.saving || !requireProcessDefinition()) return;
  formDlg.saving = true;
  try {
    const payload = { processDefinitionKey: selectedPd.value, ...formDlg.form };
    if (formDlg.editing) await updateNodeForm(formDlg.editing, payload);
    else await createNodeForm(payload);
    ElMessage.success('节点表单已保存');
    formDlg.show = false;
    await reload();
  } catch (error) {
    ElMessage.error(error?.message || '保存失败');
  } finally {
    formDlg.saving = false;
  }
}

const timeoutDlg = reactive({ show: false, editing: null, saving: false, form: {} });
function openTimeoutDlg(row) {
  if (!requireProcessDefinition()) return;
  timeoutDlg.editing = row?.id || null;
  timeoutDlg.form = { nodeKey: row?.nodeKey || '', warningHours: row?.warningHours ?? 24, timeoutHours: row?.timeoutHours ?? 48 };
  timeoutDlg.show = true;
}
async function saveTimeout() {
  if (timeoutDlg.saving || !requireProcessDefinition()) return;
  if (Number(timeoutDlg.form.warningHours) >= Number(timeoutDlg.form.timeoutHours)) {
    ElMessage.warning('黄灯阈值必须小于红灯阈值');
    return;
  }
  timeoutDlg.saving = true;
  try {
    const payload = { processDefinitionKey: selectedPd.value, ...timeoutDlg.form };
    if (timeoutDlg.editing) await updateTimeoutRule(timeoutDlg.editing, payload);
    else await createTimeoutRule(payload);
    ElMessage.success('超时规则已保存');
    timeoutDlg.show = false;
    await reload();
  } catch (error) {
    ElMessage.error(error?.message || '保存失败');
  } finally {
    timeoutDlg.saving = false;
  }
}

onMounted(async () => {
  await loadProcessDefs();
  if (selectedPd.value) await reload();
});
</script>

<style lang="scss" scoped>
.config-panel { min-width: 0; }
.config-tabs :deep(.el-tabs__header) { margin-bottom: var(--space-4); }
.config-tabs :deep(.el-tabs__nav-wrap)::after { background: var(--color-border); }
.tab-toolbar { align-items: center; margin-bottom: var(--space-3); }
.tab-toolbar .hint { margin: 0; }
.mono {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  color: var(--color-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  padding: 2px 6px;
}
</style>
