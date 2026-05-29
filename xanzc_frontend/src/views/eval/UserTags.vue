<template>
  <div>
    <div class="page-h">
      <h1>人员标签</h1>
      <span class="desc">维护人员的被评价人角色（单选）与评价人角色（多选）</span>
    </div>

    <div class="card-section">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索姓名或工号"
          clearable
          size="small"
          class="kw-input"
          @keyup.enter="doSearch"
          @clear="doSearch"
        />
        <el-button type="primary" size="small" @click="doSearch">查询</el-button>
        <el-button size="small" @click="resetSearch">重置</el-button>
        <el-button size="small" @click="openImport">导入</el-button>
        <el-button size="small" :loading="exporting" @click="doExport">下载</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border size="small" style="width: 100%">
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
        <el-table-column label="被评价人角色" min-width="130">
          <template #default="{ row }">
            <el-tag v-if="row.beEvalTag" type="success" effect="plain" size="small">{{ row.beEvalTag.tagName }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="评价人角色" min-width="180">
          <template #default="{ row }">
            <span v-if="row.evalTags && row.evalTags.length">{{ row.evalTags.map(t => t.tagName).join('，') }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>

      <el-dialog v-model="importVisible" title="导入人员评价角色" width="640px">
        <div class="imp-tip">
          <el-button size="small" @click="doDownloadTpl">📥 下载导入模板</el-button>
          <span class="muted">模板列：工号 / 被评价角色 / 评价角色；评价角色用逗号分隔。全部校验通过才会导入。</span>
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
          <el-button type="primary" :loading="importing" :disabled="!impFile" @click="doImport">开始导入</el-button>
        </template>
      </el-dialog>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑人员评价角色" width="520px" @closed="onDialogClosed">
      <div v-if="editing" class="edit-info">
        <div class="info-row"><span class="info-k">姓名</span><span>{{ editing.userName }}</span></div>
        <div class="info-row"><span class="info-k">工号</span><span>{{ editing.userId }}</span></div>
        <div class="info-row"><span class="info-k">部门</span><span>{{ editing.orgName || '—' }}</span></div>
        <div class="info-row"><span class="info-k">岗位</span><span>{{ editing.position || '—' }}</span></div>
        <div class="info-row"><span class="info-k">角色</span><span>{{ (editing.roleNames && editing.roleNames.length) ? editing.roleNames.join('，') : '—' }}</span></div>
      </div>

      <el-form label-width="110px" class="edit-form">
        <el-form-item label="被评价人角色">
          <el-select v-model="form.beEvalTagId" clearable filterable placeholder="单选，可清空" style="width: 100%">
            <el-option v-for="t in tagsOfType(1)" :key="t.tagId" :value="t.tagId" :label="t.tagName" />
          </el-select>
        </el-form-item>
        <el-form-item label="评价人角色">
          <el-select v-model="form.evalTagIds" multiple filterable placeholder="可多选" style="width: 100%">
            <el-option v-for="t in tagsOfType(2)" :key="t.tagId" :value="t.tagId" :label="t.tagName" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listAllTags, pageUserRoles, saveUserRoles, importUserRoles, downloadImportTemplate, exportUserRoles } from '@/api/eval';

// === 全量启用标签（编辑弹窗下拉用） ===
const allTags = ref([]);
function tagsOfType(type) {
  return allTags.value.filter(t => t.tagType === type && t.status === 1);
}
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
const loading = ref(false);
let searchTimer = null;
const exporting = ref(false);
const importVisible = ref(false);
const importing = ref(false);
const impFile = ref(null);
const impUploaderRef = ref(null);
const importErrors = ref([]);

async function reload() {
  loading.value = true;
  try {
    const r = await pageUserRoles({ keyword: keyword.value.trim() || undefined, page: page.value, pageSize: pageSize.value });
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = r?.total ?? rows.value.length;
  } catch {
    rows.value = [];
    total.value = 0;
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
  if (!impFile.value) return;
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
  exporting.value = true;
  try {
    const blob = await exportUserRoles(keyword.value);
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
const form = reactive({ beEvalTagId: null, evalTagIds: [] });

function openEdit(row) {
  editing.value = row;
  form.beEvalTagId = row.beEvalTag ? row.beEvalTag.tagId : null;
  form.evalTagIds = (row.evalTags || []).map(t => t.tagId);
  editVisible.value = true;
}

function onDialogClosed() {
  editing.value = null;
  form.beEvalTagId = null;
  form.evalTagIds = [];
}

async function handleSave() {
  if (!editing.value) return;
  saving.value = true;
  try {
    await saveUserRoles(editing.value.userId, form.beEvalTagId ?? null, form.evalTagIds || []);
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
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.kw-input {
  width: 240px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.muted {
  color: $text-3;
}
.edit-info {
  background: $bg-soft;
  border: 1px solid $border-1;
  border-radius: 4px;
  padding: 10px 14px;
  margin-bottom: 16px;
}
.info-row {
  display: flex;
  gap: 8px;
  font-size: 13px;
  line-height: 1.9;
}
.info-k {
  width: 40px;
  color: $text-3;
}
.edit-form {
  padding-right: 8px;
}
</style>
