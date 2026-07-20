<template>
  <div class="person-tags-page">
    <el-card shadow="never">
      <template #header>
        <PageTitle />
      </template>

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="标签名称"
          clearable
          style="width: 220px"
          @keyup.enter="onSearch"
          @clear="onSearch" />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <div class="toolbar-right">
          <el-button @click="openGlobalImport">导入</el-button>
          <el-button type="primary" @click="openCreate">新建标签</el-button>
        </div>
      </div>

      <!-- 标签列表 -->
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="tagName" label="标签名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column prop="memberCount" label="关联人数" width="100" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDeleteTag(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="pager"
        @current-change="reload"
        @size-change="reload" />
    </el-card>

    <!-- 新建/编辑标签弹窗 -->
    <el-dialog v-model="tagDlg.visible" :title="tagDlg.editing ? '编辑标签' : '新建标签'" width="480px">
      <el-form ref="tagFormRef" :model="tagDlg.form" :rules="tagRules" label-width="90px">
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="tagDlg.form.tagName" maxlength="100" show-word-limit placeholder="全局唯一" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="tagDlg.form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="tagDlg.saving" @click="saveTag">保存</el-button>
      </template>
    </el-dialog>

    <!-- 全局导入弹窗（标签+人员关联，缺标签自动新建，追加语义） -->
    <el-dialog v-model="globalImp.visible" title="导入人员标签" width="640px">
      <div class="imp-tip">
        <el-button size="small" @click="downloadGlobalTpl">📥 下载导入模板</el-button>
        <span class="muted">模板列：标签名称 / 工号 / 姓名。库中没有的标签自动新建；任一行错误则整体不导入。</span>
      </div>
      <el-upload
        drag
        action="#"
        :auto-upload="false"
        :show-file-list="true"
        :limit="1"
        :on-change="(f) => (globalImp.file = f.raw)"
        :on-remove="() => (globalImp.file = null)"
        accept=".xlsx"
        style="margin-top: 12px">
        <div class="el-upload__text">点击或拖拽 <em>.xlsx</em> 到此处</div>
      </el-upload>
      <ImportErrors :errors="globalImp.errors" />
      <template #footer>
        <el-button @click="globalImp.visible = false">取消</el-button>
        <el-button type="primary" :loading="globalImp.importing" :disabled="!globalImp.file" @click="doGlobalImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉：成员管理 -->
    <el-drawer v-model="detail.visible" :title="`标签详情 — ${detail.tag?.tagName || ''}`" size="720px">
      <div class="toolbar">
        <span class="muted">共 {{ detail.total }} 人</span>
        <div class="toolbar-right">
          <el-button size="small" @click="openMemberImport">导入（全量覆盖）</el-button>
          <el-button size="small" type="primary" @click="openMemberAdd">新增员工</el-button>
        </div>
      </div>

      <el-table v-loading="detail.loading" :data="detail.rows" border stripe size="small">
        <el-table-column prop="username" label="工号" width="140" />
        <el-table-column prop="displayName" label="姓名" width="120">
          <template #default="{ row }">{{ row.displayName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="orgName" label="机构" min-width="180">
          <template #default="{ row }">
            <span v-if="row.orgName">{{ row.orgName }}<span class="muted"> ({{ row.orgCode }})</span></span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openMemberEdit(row)">修改</el-button>
            <el-button link type="danger" size="small" @click="onRemoveMember(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="detail.page"
        v-model:page-size="detail.pageSize"
        :total="detail.total"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="reloadMembers" />
    </el-drawer>

    <!-- 新增员工弹窗 -->
    <el-dialog v-model="memberAdd.visible" title="新增员工" width="480px">
      <el-form label-width="90px">
        <el-form-item label="员工工号">
          <el-input
            v-model="memberAdd.text"
            type="textarea"
            :rows="4"
            placeholder="输入工号，多个用逗号或换行分隔" />
        </el-form-item>
      </el-form>
      <div class="muted">工号必须已存在于用户管理；已在标签下的工号自动跳过。</div>
      <template #footer>
        <el-button @click="memberAdd.visible = false">取消</el-button>
        <el-button type="primary" :loading="memberAdd.saving" @click="saveMemberAdd">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改员工弹窗 -->
    <el-dialog v-model="memberEdit.visible" title="修改员工" width="420px">
      <el-form label-width="90px">
        <el-form-item label="原工号">
          <span>{{ memberEdit.row?.username }}<template v-if="memberEdit.row?.displayName">（{{ memberEdit.row.displayName }}）</template></span>
        </el-form-item>
        <el-form-item label="新工号">
          <el-input v-model="memberEdit.username" placeholder="替换为另一个工号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberEdit.visible = false">取消</el-button>
        <el-button type="primary" :loading="memberEdit.saving" @click="saveMemberEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 成员导入弹窗（全量覆盖） -->
    <el-dialog v-model="memberImp.visible" title="导入标签成员（全量覆盖）" width="640px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="全量覆盖导入"
        description="导入成功后，该标签现有全部关联人员将被清空，并以本次文件内容为准。"
        style="margin-bottom: 12px" />
      <div class="imp-tip">
        <el-button size="small" @click="downloadMemberTpl">📥 下载导入模板</el-button>
        <span class="muted">模板列：工号 / 姓名。任一行错误则不改动现有数据。</span>
      </div>
      <el-upload
        drag
        action="#"
        :auto-upload="false"
        :show-file-list="true"
        :limit="1"
        :on-change="(f) => (memberImp.file = f.raw)"
        :on-remove="() => (memberImp.file = null)"
        accept=".xlsx"
        style="margin-top: 12px">
        <div class="el-upload__text">点击或拖拽 <em>.xlsx</em> 到此处</div>
      </el-upload>
      <ImportErrors :errors="memberImp.errors" />
      <template #footer>
        <el-button @click="memberImp.visible = false">取消</el-button>
        <el-button type="primary" :loading="memberImp.importing" :disabled="!memberImp.file" @click="doMemberImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { h, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listPersonTags, createPersonTag, updatePersonTag, deletePersonTag,
  listPersonTagMembers, addPersonTagMembers, updatePersonTagMember, removePersonTagMember,
  importPersonTags, downloadPersonTagTemplate,
  importPersonTagMembers, downloadPersonTagMemberTemplate
} from '@/api/system';

/** 导入错误明细表（行号/工号/原因），两个导入弹窗共用。 */
const ImportErrors = {
  name: 'ImportErrors',
  props: { errors: { type: Array, default: () => [] } },
  render() {
    if (!this.errors.length) return null;
    return h('div', { class: 'imp-errors' }, [
      h('div', { class: 'err-title' }, `导入未通过校验（共 ${this.errors.length} 条问题），未做任何改动：`),
      h('table', { class: 'err-table' }, [
        h('thead', [h('tr', [h('th', '行号'), h('th', '工号'), h('th', '原因')])]),
        h('tbody', this.errors.map((e) =>
          h('tr', { key: `${e.row}-${e.username}` },
            [h('td', e.row), h('td', e.username || '—'), h('td', e.message)])))
      ])
    ]);
  }
};

// ===== 标签列表 =====
const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const keyword = ref('');

async function reload() {
  loading.value = true;
  try {
    const r = await listPersonTags({
      keyword: keyword.value.trim() || undefined,
      pageNo: page.value,
      pageSize: pageSize.value
    });
    rows.value = r?.records || [];
    total.value = r?.total ?? rows.value.length;
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  page.value = 1;
  reload();
}

function formatTime(t) {
  if (!t) return '—';
  return String(t).replace('T', ' ').slice(0, 19);
}

// ===== 新建/编辑标签 =====
const tagFormRef = ref(null);
const tagRules = {
  tagName: [{ required: true, message: '标签名称不能为空', trigger: 'blur' }]
};
const tagDlg = reactive({ visible: false, editing: null, saving: false, form: { tagName: '', remark: '' } });

function openCreate() {
  tagDlg.editing = null;
  tagDlg.form = { tagName: '', remark: '' };
  tagDlg.visible = true;
}

function openEdit(row) {
  tagDlg.editing = row;
  tagDlg.form = { tagName: row.tagName, remark: row.remark || '' };
  tagDlg.visible = true;
}

async function saveTag() {
  if (tagFormRef.value) {
    try { await tagFormRef.value.validate(); } catch { return; }
  }
  tagDlg.saving = true;
  try {
    if (tagDlg.editing) {
      await updatePersonTag(tagDlg.editing.tagId, tagDlg.form);
      ElMessage.success('已保存');
    } else {
      await createPersonTag(tagDlg.form);
      ElMessage.success('已创建');
    }
    tagDlg.visible = false;
    reload();
  } catch { /* http.js 已弹错误消息 */ } finally {
    tagDlg.saving = false;
  }
}

async function onDeleteTag(row) {
  try {
    await ElMessageBox.confirm(
      `删除标签「${row.tagName}」将同时删除其下 ${row.memberCount || 0} 条关联人员信息，是否继续？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    );
  } catch { return; }
  try {
    await deletePersonTag(row.tagId);
    ElMessage.success('已删除');
    reload();
  } catch { /* 已提示 */ }
}

// ===== 全局导入 =====
const globalImp = reactive({ visible: false, file: null, importing: false, errors: [] });

function openGlobalImport() {
  globalImp.file = null;
  globalImp.errors = [];
  globalImp.visible = true;
}

async function doGlobalImport() {
  if (!globalImp.file) return;
  globalImp.importing = true;
  globalImp.errors = [];
  try {
    const res = await importPersonTags(globalImp.file);
    if (res && res.success) {
      const parts = [`新增关联 ${res.importedCount} 条`];
      if (res.createdTagCount) parts.push(`自动新建标签 ${res.createdTagCount} 个`);
      if (res.skippedCount) parts.push(`已存在跳过 ${res.skippedCount} 条`);
      ElMessage.success(`导入成功：${parts.join('，')}`);
      globalImp.visible = false;
      reload();
    } else {
      globalImp.errors = (res && res.errors) || [];
      ElMessage.error('导入未通过校验，请查看错误明细');
    }
  } catch { /* 已提示 */ } finally {
    globalImp.importing = false;
  }
}

async function downloadGlobalTpl() {
  try {
    const blob = await downloadPersonTagTemplate();
    saveBlob(blob, '人员标签导入模板.xlsx');
  } catch { /* 已提示 */ }
}

// ===== 详情（成员管理） =====
const detail = reactive({
  visible: false, tag: null, loading: false,
  rows: [], total: 0, page: 1, pageSize: 20
});

function openDetail(row) {
  detail.tag = row;
  detail.page = 1;
  detail.visible = true;
  reloadMembers();
}

async function reloadMembers() {
  if (!detail.tag) return;
  detail.loading = true;
  try {
    const r = await listPersonTagMembers(detail.tag.tagId, {
      pageNo: detail.page,
      pageSize: detail.pageSize
    });
    detail.rows = r?.records || [];
    detail.total = r?.total ?? detail.rows.length;
  } finally {
    detail.loading = false;
  }
}

/** 成员变动后同步列表页的关联人数列。 */
function refreshBoth() {
  reloadMembers();
  reload();
}

// ===== 新增员工 =====
const memberAdd = reactive({ visible: false, text: '', saving: false });

function openMemberAdd() {
  memberAdd.text = '';
  memberAdd.visible = true;
}

async function saveMemberAdd() {
  const usernames = memberAdd.text.split(/[,，\n\s]+/).map((s) => s.trim()).filter(Boolean);
  if (!usernames.length) {
    ElMessage.warning('请输入至少一个工号');
    return;
  }
  memberAdd.saving = true;
  try {
    const added = await addPersonTagMembers(detail.tag.tagId, usernames);
    ElMessage.success(`新增 ${added} 人${added < usernames.length ? `（跳过已存在 ${usernames.length - added} 人）` : ''}`);
    memberAdd.visible = false;
    refreshBoth();
  } catch { /* 已提示（含无效工号清单） */ } finally {
    memberAdd.saving = false;
  }
}

// ===== 修改员工 =====
const memberEdit = reactive({ visible: false, row: null, username: '', saving: false });

function openMemberEdit(row) {
  memberEdit.row = row;
  memberEdit.username = row.username;
  memberEdit.visible = true;
}

async function saveMemberEdit() {
  const username = memberEdit.username.trim();
  if (!username) {
    ElMessage.warning('工号不能为空');
    return;
  }
  memberEdit.saving = true;
  try {
    await updatePersonTagMember(detail.tag.tagId, memberEdit.row.id, username);
    ElMessage.success('已修改');
    memberEdit.visible = false;
    reloadMembers();
  } catch { /* 已提示 */ } finally {
    memberEdit.saving = false;
  }
}

// ===== 删除员工 =====
async function onRemoveMember(row) {
  try {
    await ElMessageBox.confirm(
      `将「${row.displayName || row.username}」从标签「${detail.tag.tagName}」移除？`,
      '删除确认',
      { type: 'warning' }
    );
  } catch { return; }
  try {
    await removePersonTagMember(detail.tag.tagId, row.id);
    ElMessage.success('已移除');
    refreshBoth();
  } catch { /* 已提示 */ }
}

// ===== 成员导入（全量覆盖） =====
const memberImp = reactive({ visible: false, file: null, importing: false, errors: [] });

function openMemberImport() {
  memberImp.file = null;
  memberImp.errors = [];
  memberImp.visible = true;
}

async function doMemberImport() {
  if (!memberImp.file) return;
  // 全量覆盖前的强提示（需求硬约束）
  try {
    await ElMessageBox.confirm(
      `本次导入将清空标签「${detail.tag.tagName}」现有全部 ${detail.total} 条关联，并以文件内容全量覆盖，是否继续？`,
      '全量覆盖确认',
      { type: 'warning', confirmButtonText: '覆盖导入', cancelButtonText: '取消' }
    );
  } catch { return; }
  memberImp.importing = true;
  memberImp.errors = [];
  try {
    const res = await importPersonTagMembers(detail.tag.tagId, memberImp.file);
    if (res && res.success) {
      ElMessage.success(`导入成功，当前标签共 ${res.importedCount} 人`);
      memberImp.visible = false;
      refreshBoth();
    } else {
      memberImp.errors = (res && res.errors) || [];
      ElMessage.error('导入未通过校验，现有数据未改动');
    }
  } catch { /* 已提示 */ } finally {
    memberImp.importing = false;
  }
}

async function downloadMemberTpl() {
  try {
    const blob = await downloadPersonTagMemberTemplate();
    saveBlob(blob, '标签成员导入模板.xlsx');
  } catch { /* 已提示 */ }
}

// ===== 工具 =====
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

onMounted(reload);
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.toolbar-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.imp-tip {
  display: flex;
  align-items: center;
  gap: 10px;
}
.imp-errors {
  margin-top: 12px;
}
.imp-errors .err-title {
  color: #f56c6c;
  font-size: 13px;
  margin-bottom: 6px;
}
.imp-errors .err-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.imp-errors .err-table th,
.imp-errors .err-table td {
  border: 1px solid #ebeef5;
  padding: 4px 8px;
  text-align: left;
}
</style>
