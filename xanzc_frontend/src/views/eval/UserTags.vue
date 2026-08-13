<template>
  <main class="bp-crud eval-user-tags-page" aria-labelledby="eval-user-tags-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="eval-user-tags-page-title"><span class="sub">每人单选一个评价角色，并控制是否参与评价</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="人员评价角色操作">
        <el-button :loading="loading" @click="reload">刷新</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="人员评价角色筛选">
      <el-form class="filter-form" inline aria-label="人员评价角色筛选">
        <el-form-item label="人员">
          <el-input
            v-model="keyword"
            aria-label="按姓名或工号筛选"
            placeholder="搜索姓名或工号"
            clearable
            class="kw-input"
            @keyup.enter="doSearch"
            @clear="doSearch"
          />
        </el-form-item>
        <el-form-item label="参与评价">
          <el-select v-model="evalEnabledFilter" aria-label="按是否参与评价筛选" style="width:140px" @change="doSearch">
          <el-option label="参与：是" value="1" />
          <el-option label="参与：否" value="0" />
          <el-option label="全部" value="all" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="doSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button @click="openImport">导入</el-button>
          <el-button :loading="exporting" :disabled="exporting" @click="doExport">导出</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel" aria-label="人员评价角色列表" aria-describedby="eval-user-tags-table-state">
      <div class="toolbar">
        <div>
          <h2 id="eval-user-tags-table-heading" class="section-title">人员评价角色列表</h2>
          <p class="hint">导入采用全量校验；编辑保存会覆盖当前人员的评价角色和参与状态。</p>
        </div>
        <p id="eval-user-tags-table-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '人员评价角色列表加载中' : loadError || (rows.length ? `共 ${total} 人` : '暂无人员评价角色数据') }}
        </p>
      </div>

      <el-table :data="rows" v-loading="loading" border size="default" empty-text="暂无人员评价角色数据"
        aria-labelledby="eval-user-tags-table-heading" aria-describedby="eval-user-tags-table-state">
        <el-table-column prop="userName" label="姓名" min-width="100" />
        <el-table-column prop="userId" label="工号" min-width="110" />
        <el-table-column prop="orgName" label="部门" min-width="140">
          <template #default="{ row }">{{ row.orgName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="position" label="岗位" min-width="120">
          <template #default="{ row }">{{ row.position || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="140">
          <template #default="{ row }">{{ (row.roleNames && row.roleNames.length) ? row.roleNames.join('，') : '—' }}</template>
        </el-table-column>
        <el-table-column label="评价角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.tag" type="success" effect="plain" size="small">{{ row.tag.tagName }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="是否参与评价" min-width="120">
          <template #default="{ row }">
            <el-tag v-if="row.evalEnabled === 1" type="success" effect="plain" size="small">是</el-tag>
            <el-tag v-else type="info" effect="plain" size="small">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <nav class="pager" aria-label="人员评价角色列表分页">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPageChange"
        />
      </nav>

      <el-dialog v-model="importVisible" class="bp-crud-dialog" title="导入人员评价角色" width="640px" :close-on-click-modal="false">
        <div class="imp-tip">
          <el-button @click="doDownloadTpl">下载导入模板</el-button>
          <span class="muted">模板列：工号 / 角色 / 是否参与评价。全部校验通过才会导入。</span>
        </div>
        <el-upload
          ref="impUploaderRef"
          drag
          action="#"
          :auto-upload="false"
          :show-file-list="true"
          :limit="1"
          :on-change="onImpFilePick"
          accept=".xlsx"
          style="margin-top:12px">
          <div class="el-upload__text">点击或拖拽 <em>.xlsx</em> 到此处</div>
        </el-upload>

        <div v-if="importErrors.length" class="imp-errors">
          <div class="err-title">导入失败，请修正后重传（共 {{ importErrors.length }} 条问题）：</div>
          <el-table :data="importErrors" size="small" border max-height="240">
            <el-table-column prop="row" label="行号" width="80" />
            <el-table-column prop="empId" label="工号" width="140" />
            <el-table-column prop="message" label="原因" min-width="240" />
          </el-table>
        </div>

        <template #footer>
          <el-button @click="importVisible = false">取消</el-button>
          <el-button type="primary" :loading="importing" :disabled="!impFile || importing" @click="doImport">开始导入</el-button>
        </template>
      </el-dialog>
    </section>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" class="bp-crud-dialog" title="编辑人员评价角色" width="520px" :close-on-click-modal="false" @closed="onDialogClosed">
      <div v-if="editing" class="edit-info">
        <div class="info-row"><span class="info-k">姓名</span><span>{{ editing.userName }}</span></div>
        <div class="info-row"><span class="info-k">工号</span><span>{{ editing.userId }}</span></div>
        <div class="info-row"><span class="info-k">部门</span><span>{{ editing.orgName || '—' }}</span></div>
        <div class="info-row"><span class="info-k">岗位</span><span>{{ editing.position || '—' }}</span></div>
        <div class="info-row"><span class="info-k">角色</span><span>{{ (editing.roleNames && editing.roleNames.length) ? editing.roleNames.join('，') : '—' }}</span></div>
      </div>

      <el-form label-width="110px" class="edit-form">
        <el-form-item label="评价角色">
          <el-select v-model="form.tagId" clearable filterable placeholder="单选，可清空" style="width: 100%">
            <el-option v-for="t in activeTags" :key="t.tagId" :value="t.tagId" :label="t.tagName" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否参与评价">
          <el-select v-model="form.evalEnabled" style="width: 100%">
            <el-option :value="1" label="是" />
            <el-option :value="0" label="否" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { listAllTags, pageUserRoles, saveUserRoles, importUserRoles, downloadImportTemplate, exportUserRoles } from '@/api/eval';

// === 全量启用标签（编辑弹窗下拉用） ===
const allTags = ref([]);
async function loadAllTags() {
  try {
    const r = await listAllTags({ status: 1 });
    allTags.value = Array.isArray(r) ? r : (r?.records || []);
  } catch {
    allTags.value = [];
  }
}

// === 列表 ===
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const keyword = ref('');
const evalEnabledFilter = ref('1'); // 默认只看启用=是
const loading = ref(false);
const loadError = ref('');
let searchTimer = null;
const exporting = ref(false);
const importVisible = ref(false);
const importing = ref(false);
const impFile = ref(null);
const impUploaderRef = ref(null);
const importErrors = ref([]);

async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const r = await pageUserRoles({ keyword: keyword.value.trim() || undefined, evalEnabled: evalEnabledFilter.value, page: page.value, pageSize: pageSize.value });
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = r?.total ?? rows.value.length;
  } catch {
    rows.value = [];
    total.value = 0;
    loadError.value = '人员评价角色加载失败，请刷新重试';
  } finally {
    loading.value = false;
  }
}

function doSearch() {
  page.value = 1;
  reload();
}
function resetSearch() {
  keyword.value = '';
  evalEnabledFilter.value = '1';
  page.value = 1;
  reload();
}

function openImport() {
  importErrors.value = [];
  impFile.value = null;
  impUploaderRef.value?.clearFiles();
  importVisible.value = true;
}
function onImpFilePick(uploadFile) {
  impFile.value = uploadFile.raw || null;
}
async function doImport() {
  if (!impFile.value || importing.value) return;
  importing.value = true;
  importErrors.value = [];
  try {
    const res = await importUserRoles(impFile.value);
    if (res && res.success) {
      ElMessage.success(`导入成功 ${res.importedCount} 条`);
      importVisible.value = false;
      reload();
    } else {
      importErrors.value = (res && res.errors) || [];
      ElMessage.error('导入未通过校验，请查看错误明细');
    }
  } catch (e) {
    // http.js 已弹错误消息
  } finally {
    importing.value = false;
  }
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
async function doDownloadTpl() {
  try {
    const blob = await downloadImportTemplate();
    saveBlob(blob, '人员评价角色导入模板.xlsx');
  } catch (e) { /* 已提示 */ }
}
async function doExport() {
  if (exporting.value) return;
  exporting.value = true;
  try {
    const blob = await exportUserRoles(keyword.value, evalEnabledFilter.value);
    saveBlob(blob, '人员标签列表.xlsx');
  } catch (e) { /* 已提示 */ } finally {
    exporting.value = false;
  }
}

function onPageChange(p) {
  page.value = p;
  reload();
}

// === 编辑 ===
const editVisible = ref(false);
const editing = ref(null);
const saving = ref(false);
const form = reactive({ tagId: null, evalEnabled: 0 });

// 全量启用标签（拍平，无类型）—— 单一角色单选项
const activeTags = computed(() => allTags.value.filter(t => t.status === 1));

function openEdit(row) {
  editing.value = row;
  form.tagId = row.tag ? row.tag.tagId : null;
  form.evalEnabled = (row.evalEnabled === 1) ? 1 : 0;
  editVisible.value = true;
}

function onDialogClosed() {
  editing.value = null;
  form.tagId = null;
  form.evalEnabled = 0;
}

async function handleSave() {
  if (!editing.value || saving.value) return;
  saving.value = true;
  try {
    await saveUserRoles(editing.value.userId, form.tagId ?? null, form.evalEnabled);
    ElMessage.success('保存成功');
    editVisible.value = false;
    await reload();
  } catch {
    ElMessage.error('保存失败，请重试');
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  await loadAllTags();
  await reload();
});
</script>

<style lang="scss" scoped>
.kw-input {
  width: 240px;
}
.muted {
  color: var(--color-text-muted);
}
.edit-info {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  padding: var(--space-3) var(--space-4);
  margin-bottom: var(--space-4);
}
.info-row {
  display: flex;
  gap: var(--space-2);
  font-size: 14px;
  line-height: 22px;
}
.info-k {
  width: 40px;
  color: var(--color-text-muted);
}
.edit-form {
  padding-right: var(--space-2);
}
.filter-actions { margin-left: auto; }
</style>
