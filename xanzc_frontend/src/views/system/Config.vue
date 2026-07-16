<template>
  <div>
    <div class="page-h">
      <PageTitle />
      <span class="sub">节点候选人 · 节点表单 · 超时规则（SLA）</span>
    </div>

    <div class="card-section">
      <el-form inline size="default">
        <el-form-item label="流程定义">
          <el-select v-model="selectedPd" filterable placeholder="选择流程" style="width:300px" @change="reload">
            <el-option v-for="p in processDefs" :key="p.processDefinitionKey" :value="p.processDefinitionKey"
                       :label="`${p.processDefinitionName || p.processDefinitionKey} (${p.processDefinitionKey})`" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <el-tabs v-model="activeTab" @tab-change="reload" class="config-tabs">
      <!-- 候选人配置 -->
      <el-tab-pane label="节点候选人" name="candidates">
        <div class="card-section table">
          <div class="table-actions">
            <el-button type="primary" size="small" @click="openCandidateDlg()">+ 新增</el-button>
          </div>
          <el-table :data="candidates" size="default" v-loading="loading" empty-text="暂无配置" stripe>
            <el-table-column prop="nodeKey" label="节点 Key" width="200" />
            <el-table-column prop="candidateType" label="候选类型" width="100">
              <template #default="{row}">
                <el-tag effect="plain" size="small">{{ row.candidateType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="candidateValue" label="候选值" min-width="240" show-overflow-tooltip />
            <el-table-column label="操作" width="100">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openCandidateDlg(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 表单配置 -->
      <el-tab-pane label="节点表单" name="forms">
        <div class="card-section table">
          <div class="table-actions">
            <el-button type="primary" size="small" @click="openFormDlg()">+ 新增</el-button>
          </div>
          <el-table :data="forms" size="default" v-loading="loading" empty-text="暂无配置" stripe>
            <el-table-column prop="nodeKey" label="节点 Key" width="200" />
            <el-table-column prop="formFields" label="表单字段定义" min-width="300" show-overflow-tooltip />
            <el-table-column prop="readableFields" label="只读字段" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="100">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openFormDlg(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 超时规则 -->
      <el-tab-pane label="超时规则 (SLA)" name="timeout">
        <div class="card-section table">
          <div class="table-actions">
            <el-button type="primary" size="small" @click="openTimeoutDlg()">+ 新增</el-button>
          </div>
          <el-table :data="timeoutRules" size="default" v-loading="loading" empty-text="暂无配置" stripe>
            <el-table-column prop="nodeKey" label="节点 Key" width="200" />
            <el-table-column prop="warningHours" label="黄灯(小时)" width="120" align="right" />
            <el-table-column prop="timeoutHours" label="红灯(小时)" width="120" align="right" />
            <el-table-column label="操作" width="100">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openTimeoutDlg(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 候选人编辑弹窗 -->
    <el-dialog v-model="candidateDlg.show" :title="candidateDlg.editing ? '编辑候选人' : '新增候选人'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="节点 Key">
          <el-input v-model="candidateDlg.form.nodeKey" placeholder="如 branch_approve" />
        </el-form-item>
        <el-form-item label="候选类型">
          <el-select v-model="candidateDlg.form.candidateType" style="width:100%">
            <el-option value="ROLE" label="ROLE - 角色" />
            <el-option value="USER" label="USER - 指定用户" />
            <el-option value="ORG" label="ORG - 机构" />
          </el-select>
        </el-form-item>
        <el-form-item label="候选值">
          <el-input v-model="candidateDlg.form.candidateValue" type="textarea" :rows="3"
                    placeholder='JSON 数组，如 ["BRANCH_HEAD","CORP_DEPT"]' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="candidateDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="candidateDlg.saving" @click="saveCandidate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 表单编辑弹窗 -->
    <el-dialog v-model="formDlg.show" :title="formDlg.editing ? '编辑表单' : '新增表单'" width="600px">
      <el-form label-width="90px">
        <el-form-item label="节点 Key">
          <el-input v-model="formDlg.form.nodeKey" placeholder="如 biz_dept_review" />
        </el-form-item>
        <el-form-item label="表单字段">
          <el-input v-model="formDlg.form.formFields" type="textarea" :rows="4"
                    placeholder='JSON 数组，如 [{"key":"opinion","label":"审批意见","type":"TEXTAREA"}]' />
        </el-form-item>
        <el-form-item label="必填字段">
          <el-input v-model="formDlg.form.requiredFields" placeholder='JSON 数组，如 ["opinion"]' />
        </el-form-item>
        <el-form-item label="只读字段">
          <el-input v-model="formDlg.form.readableFields" placeholder='JSON 数组，如 ["opinion"]' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="formDlg.saving" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 超时规则编辑弹窗 -->
    <el-dialog v-model="timeoutDlg.show" :title="timeoutDlg.editing ? '编辑超时规则' : '新增超时规则'" width="480px">
      <el-form label-width="100px">
        <el-form-item label="节点 Key">
          <el-input v-model="timeoutDlg.form.nodeKey" placeholder="如 branch_approve" />
        </el-form-item>
        <el-form-item label="黄灯(小时)">
          <el-input-number v-model="timeoutDlg.form.warningHours" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="红灯(小时)">
          <el-input-number v-model="timeoutDlg.form.timeoutHours" :min="1" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="timeoutDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="timeoutDlg.saving" @click="saveTimeout">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  listProcessDefinitions,
  listNodeCandidates, createNodeCandidate, updateNodeCandidate,
  listNodeForms, createNodeForm, updateNodeForm,
  listTimeoutRules, createTimeoutRule, updateTimeoutRule
} from '@/api/system';

const processDefs = ref([]);
const selectedPd = ref('');
const activeTab = ref('candidates');
const loading = ref(false);

const candidates = ref([]);
const forms = ref([]);
const timeoutRules = ref([]);

async function loadProcessDefs() {
  try {
    const r = await listProcessDefinitions();
    const all = Array.isArray(r) ? r : [];
    const map = new Map();
    for (const p of all) {
      const k = p.processDefinitionKey;
      if (!map.has(k) || (p.version || 0) > (map.get(k).version || 0)) map.set(k, p);
    }
    processDefs.value = [...map.values()];
    if (processDefs.value.length && !selectedPd.value) {
      selectedPd.value = processDefs.value[0].processDefinitionKey;
    }
  } catch { processDefs.value = []; }
}

async function reload() {
  if (!selectedPd.value) return;
  loading.value = true;
  try {
    if (activeTab.value === 'candidates') {
      const r = await listNodeCandidates(selectedPd.value);
      candidates.value = Array.isArray(r) ? r : [];
    } else if (activeTab.value === 'forms') {
      const r = await listNodeForms(selectedPd.value);
      forms.value = Array.isArray(r) ? r : [];
    } else {
      const r = await listTimeoutRules(selectedPd.value);
      timeoutRules.value = Array.isArray(r) ? r : [];
    }
  } catch {} finally { loading.value = false; }
}

const candidateDlg = reactive({ show: false, editing: null, saving: false, form: {} });
function openCandidateDlg(row) {
  candidateDlg.editing = row?.id || null;
  candidateDlg.form = {
    nodeKey: row?.nodeKey || '',
    candidateType: row?.candidateType || 'ROLE',
    candidateValue: row?.candidateValue || '[]'
  };
  candidateDlg.show = true;
}
async function saveCandidate() {
  candidateDlg.saving = true;
  try {
    const payload = { processDefinitionKey: selectedPd.value, ...candidateDlg.form };
    if (candidateDlg.editing) await updateNodeCandidate(candidateDlg.editing, payload);
    else await createNodeCandidate(payload);
    ElMessage.success('已保存');
    candidateDlg.show = false;
    reload();
  } catch (e) { ElMessage.error(e?.message || '保存失败'); }
  finally { candidateDlg.saving = false; }
}

const formDlg = reactive({ show: false, editing: null, saving: false, form: {} });
function openFormDlg(row) {
  formDlg.editing = row?.id || null;
  formDlg.form = {
    nodeKey: row?.nodeKey || '',
    formFields: row?.formFields || '[]',
    requiredFields: row?.requiredFields || '[]',
    readableFields: row?.readableFields || '[]'
  };
  formDlg.show = true;
}
async function saveForm() {
  formDlg.saving = true;
  try {
    const payload = { processDefinitionKey: selectedPd.value, ...formDlg.form };
    if (formDlg.editing) await updateNodeForm(formDlg.editing, payload);
    else await createNodeForm(payload);
    ElMessage.success('已保存');
    formDlg.show = false;
    reload();
  } catch (e) { ElMessage.error(e?.message || '保存失败'); }
  finally { formDlg.saving = false; }
}

const timeoutDlg = reactive({ show: false, editing: null, saving: false, form: {} });
function openTimeoutDlg(row) {
  timeoutDlg.editing = row?.id || null;
  timeoutDlg.form = {
    nodeKey: row?.nodeKey || '',
    warningHours: row?.warningHours ?? 24,
    timeoutHours: row?.timeoutHours ?? 48
  };
  timeoutDlg.show = true;
}
async function saveTimeout() {
  timeoutDlg.saving = true;
  try {
    const payload = { processDefinitionKey: selectedPd.value, ...timeoutDlg.form };
    if (timeoutDlg.editing) await updateTimeoutRule(timeoutDlg.editing, payload);
    else await createTimeoutRule(payload);
    ElMessage.success('已保存');
    timeoutDlg.show = false;
    reload();
  } catch (e) { ElMessage.error(e?.message || '保存失败'); }
  finally { timeoutDlg.saving = false; }
}

onMounted(async () => {
  await loadProcessDefs();
  if (selectedPd.value) reload();
});
</script>

<style lang="scss" scoped>
.page-h {
  display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px;
  h1 { font-size: 18px; font-weight: 600; }
  .sub { color: #999; font-size: 12px; }
}
.config-tabs { :deep(.el-tabs__nav-wrap)::after { background: var(--el-border-color-light); } }
.table { padding: 0; padding-bottom: 12px; }
.table-actions { padding: 8px 0; display: flex; justify-content: flex-end; }
</style>
