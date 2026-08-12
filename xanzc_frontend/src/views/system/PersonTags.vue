<template>
  <main class="bp-crud person-tags-page" aria-labelledby="person-tags-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="person-tags-page-title"><span class="sub">维护可复用的员工与机构标签，支持精确成员维护和带校验的批量导入。</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="人员标签操作">
        <el-button @click="reload">刷新</el-button>
        <el-button @click="openGlobalImport">导入</el-button>
        <el-button type="primary" @click="openCreate">新建标签</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="人员标签筛选">
      <el-form class="filter-form" inline size="default" aria-label="人员标签筛选条件" @submit.prevent="onSearch">
        <el-form-item label="标签名称">
        <el-input
          v-model="keyword"
          placeholder="输入标签名称"
          clearable
          aria-label="按标签名称筛选"
          style="width:260px"
          @keyup.enter="onSearch"
          @clear="onSearch" />
        </el-form-item>
        <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel person-tags-table-panel" aria-label="人员标签列表" aria-labelledby="person-tags-heading" aria-describedby="person-tags-state" :aria-busy="loading ? 'true' : 'false'">
      <div class="toolbar">
        <div>
          <h2 id="person-tags-heading" class="section-title">业务标签</h2>
          <p class="hint">删除标签会级联删除该标签下的成员关联；成员导入按当前维度全量覆盖，需二次确认。</p>
        </div>
        <p id="person-tags-state" class="table-state" role="status" aria-live="polite">{{ loading ? '人员标签加载中' : rows.length ? `共 ${total} 个标签` : '暂无人员标签' }}</p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">{{ loadError }} <el-button link type="primary" @click="reload">重试</el-button></p>
      <el-table v-loading="loading" :data="rows" aria-labelledby="person-tags-heading" aria-describedby="person-tags-state">
        <el-table-column prop="tagName" label="标签名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column prop="memberCount" label="关联成员数" width="110" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" :loading="isDeletingTag(row.tagId)" :disabled="isDeletingTag(row.tagId)" @click="onDeleteTag(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <nav class="pager" aria-label="人员标签分页">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="pager"
        @current-change="reload"
        @size-change="onTagPageSizeChange" />
      </nav>
    </section>

    <!-- 新建/编辑标签弹窗 -->
    <el-dialog v-model="tagDlg.visible" class="bp-crud-dialog" :title="tagDlg.editing ? '编辑标签' : '新建标签'" width="500px" :close-on-click-modal="false">
      <el-form ref="tagFormRef" :model="tagDlg.form" :rules="tagRules" label-width="90px">
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="tagDlg.form.tagName" maxlength="100" show-word-limit placeholder="全局唯一" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="tagDlg.form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="tagDlg.saving" @click="tagDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="tagDlg.saving" :disabled="tagDlg.saving" @click="saveTag">保存</el-button>
      </template>
    </el-dialog>

    <!-- 全局导入弹窗（标签+成员关联，缺标签自动新建，追加语义；按维度导入） -->
    <el-dialog v-model="globalImp.visible" class="bp-crud-dialog" title="导入业务标签" width="640px" :close-on-click-modal="false">
      <div class="dim-bar">
        <span class="dim-label">导入维度</span>
        <el-radio-group v-model="globalImp.dim" size="small" @change="onGlobalDimChange">
          <el-radio-button label="EMP">员工</el-radio-button>
          <el-radio-button label="ORG">机构</el-radio-button>
        </el-radio-group>
      </div>
      <div class="imp-tip">
        <el-button size="small" @click="downloadGlobalTpl">下载导入模板</el-button>
        <span class="muted">
          模板列：{{ globalImp.dim === 'ORG' ? '标签名称 / 机构名称' : '标签名称 / 工号' }}。库中没有的标签自动新建；任一行错误则整体不导入。
        </span>
      </div>
      <el-upload
        ref="globalUploaderRef"
        drag
        action="#"
        :auto-upload="false"
        :show-file-list="true"
        :limit="1"
        :on-change="(f) => (globalImp.file = f.raw)"
        :on-remove="() => (globalImp.file = null)"
        :on-exceed="onGlobalExceed"
        accept=".xlsx"
        style="margin-top: 12px">
        <div class="el-upload__text">点击或拖拽 <em>.xlsx</em> 到此处</div>
      </el-upload>
      <ImportErrors :errors="globalImp.errors" :dim="globalImp.dim" />
      <template #footer>
        <el-button :disabled="globalImp.importing" @click="globalImp.visible = false">取消</el-button>
        <el-button type="primary" :loading="globalImp.importing" :disabled="globalImp.importing || !globalImp.file" @click="doGlobalImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉：成员管理（按维度切换） -->
    <el-drawer v-model="detail.visible" :title="`标签详情 — ${detail.tag?.tagName || ''}`" size="720px" :aria-busy="detail.loading ? 'true' : 'false'">
      <div class="toolbar">
        <el-radio-group v-model="detail.dim" size="small" @change="onDetailDimChange">
          <el-radio-button label="EMP">员工</el-radio-button>
          <el-radio-button label="ORG">机构</el-radio-button>
        </el-radio-group>
        <span class="muted">共 {{ detail.total }} {{ detail.dim === 'ORG' ? '个机构' : '人' }}</span>
        <div class="toolbar-right">
          <el-button size="small" @click="openMemberImport">导入（当前维度全量覆盖）</el-button>
          <el-button size="small" type="primary" @click="openMemberAdd">新增成员</el-button>
        </div>
      </div>
      <p v-if="detail.error" class="error-state" role="alert">{{ detail.error }} <el-button link type="primary" @click="reloadMembers">重试</el-button></p>

      <!-- 员工维度：工号 + 员工姓名 -->
      <el-table v-if="detail.dim === 'EMP'" v-loading="detail.loading" :data="detail.rows" border stripe size="small">
        <el-table-column prop="username" label="工号" width="180" />
        <el-table-column prop="displayName" label="员工姓名" min-width="200">
          <template #default="{ row }">{{ row.displayName || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openMemberEdit(row)">修改</el-button>
            <el-button link type="danger" size="small" @click="onRemoveMember(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 机构维度：机构号 + 机构名称 -->
      <el-table v-else v-loading="detail.loading" :data="detail.rows" border stripe size="small">
        <el-table-column prop="orgDeptNo" label="机构号" width="180" />
        <el-table-column prop="orgName" label="机构名称" min-width="200">
          <template #default="{ row }">{{ row.orgName || '—' }}</template>
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

    <!-- 新增成员弹窗（员工工号 + 机构名称，可同时提交） -->
    <el-dialog v-model="memberAdd.visible" class="bp-crud-dialog" title="新增成员" width="560px" :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item label="员工工号">
          <el-input
            v-model="memberAdd.empText"
            type="textarea"
            :rows="3"
            placeholder="输入员工工号，多个用逗号或换行分隔（可留空）" />
        </el-form-item>
        <el-form-item label="机构名称">
          <el-select
            v-model="memberAdd.orgDeptNos"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :loading="memberAdd.orgLoading"
            :disabled="memberAdd.orgLoading"
            placeholder="按机构名称搜索并选择（可留空）"
            style="width: 100%">
            <el-option
              v-for="org in memberOrgOptions"
              :key="org.deptNo"
              :label="`${org.name}（${org.deptNo}）`"
              :value="org.deptNo" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="muted">
        工号须存在于用户管理；机构请按名称搜索选择。已在标签下的同维度成员自动跳过，员工与机构至少填写一类。
      </div>
      <template #footer>
        <el-button :disabled="memberAdd.saving" @click="memberAdd.visible = false">取消</el-button>
        <el-button type="primary" :loading="memberAdd.saving" :disabled="memberAdd.saving" @click="saveMemberAdd">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改成员弹窗（按行维度：工号 / 机构编号） -->
    <el-dialog v-model="memberEdit.visible" class="bp-crud-dialog" :title="memberEdit.isOrg ? '修改机构' : '修改员工'" width="460px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <template v-if="memberEdit.isOrg">
          <el-form-item label="原机构号">
            <span>{{ memberEdit.row?.orgDeptNo }}<template v-if="memberEdit.row?.orgName">（{{ memberEdit.row.orgName }}）</template></span>
          </el-form-item>
          <el-form-item label="新机构号">
            <el-input v-model="memberEdit.value" placeholder="替换为另一个机构编号(dept_no)" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="原工号">
            <span>{{ memberEdit.row?.username }}</span>
          </el-form-item>
          <el-form-item label="新工号">
            <el-input v-model="memberEdit.value" placeholder="替换为另一个工号" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button :disabled="memberEdit.saving" @click="memberEdit.visible = false">取消</el-button>
        <el-button type="primary" :loading="memberEdit.saving" :disabled="memberEdit.saving" @click="saveMemberEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 成员导入弹窗（当前维度全量覆盖） -->
    <el-dialog v-model="memberImp.visible" class="bp-crud-dialog" :title="`导入标签成员（${detail.dim === 'ORG' ? '机构' : '员工'}维度全量覆盖）`" width="640px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="按维度全量覆盖导入"
        :description="`导入成功后，该标签「${detail.dim === 'ORG' ? '机构' : '员工'}维度」现有全部成员将被清空，并以本次文件内容为准（不影响另一维度成员）。`"
        style="margin-bottom: 12px" />
      <div class="imp-tip">
        <el-button size="small" @click="downloadMemberTpl">下载导入模板</el-button>
        <span class="muted">模板列：{{ detail.dim === 'ORG' ? '机构名称' : '工号' }}。任一行错误则不改动现有数据。</span>
      </div>
      <el-upload
        ref="memberUploaderRef"
        drag
        action="#"
        :auto-upload="false"
        :show-file-list="true"
        :limit="1"
        :on-change="(f) => (memberImp.file = f.raw)"
        :on-remove="() => (memberImp.file = null)"
        :on-exceed="onMemberExceed"
        accept=".xlsx"
        style="margin-top: 12px">
        <div class="el-upload__text">点击或拖拽 <em>.xlsx</em> 到此处</div>
      </el-upload>
      <ImportErrors :errors="memberImp.errors" :dim="detail.dim" />
      <template #footer>
        <el-button :disabled="memberImp.importing" @click="memberImp.visible = false">取消</el-button>
        <el-button type="primary" :loading="memberImp.importing" :disabled="memberImp.importing || !memberImp.file" @click="doMemberImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { h, nextTick, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, genFileId } from 'element-plus';
import {
  listPersonTags, createPersonTag, updatePersonTag, deletePersonTag,
  listPersonTagMembers, addPersonTagMembers, updatePersonTagMember, removePersonTagMember,
  importPersonTags, downloadPersonTagTemplate,
  importPersonTagMembers, downloadPersonTagMemberTemplate
} from '@/api/system';
import { getOrgTree } from '@/api/orgs';

/** 导入错误明细表（行号/标识/原因），两个导入弹窗共用；标识列名随维度切换（工号/机构名称）。 */
const ImportErrors = {
  name: 'ImportErrors',
  props: {
    errors: { type: Array, default: () => [] },
    dim: { type: String, default: 'EMP' }
  },
  render() {
    if (!this.errors.length) return null;
    const idLabel = this.dim === 'ORG' ? '机构名称' : '工号';
    return h('div', { class: 'imp-errors' }, [
      h('div', { class: 'err-title' }, `导入未通过校验（共 ${this.errors.length} 条问题），未做任何改动：`),
      h('table', { class: 'err-table' }, [
        h('thead', [h('tr', [h('th', '行号'), h('th', idLabel), h('th', '原因')])]),
        h('tbody', this.errors.map((e) =>
          h('tr', { key: `${e.row}-${e.username}` },
            [h('td', e.row), h('td', e.username || '—'), h('td', e.message)])))
      ])
    ]);
  }
};

// ===== 标签列表 =====
const loading = ref(false);
const loadError = ref('');
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const keyword = ref('');
const deletingTagIds = ref(new Set());

function isDeletingTag(tagId) { return deletingTagIds.value.has(String(tagId)); }
function setDeletingTag(tagId, deleting) {
  const next = new Set(deletingTagIds.value);
  if (deleting) next.add(String(tagId));
  else next.delete(String(tagId));
  deletingTagIds.value = next;
}

async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const r = await listPersonTags({
      keyword: keyword.value.trim() || undefined,
      pageNo: page.value,
      pageSize: pageSize.value
    });
    rows.value = r?.records || [];
    total.value = r?.total ?? rows.value.length;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    loadError.value = `人员标签加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  page.value = 1;
  reload();
}
function resetFilters() {
  if (!keyword.value) return reload();
  keyword.value = '';
  page.value = 1;
  reload();
}
function onTagPageSizeChange() {
  page.value = 1;
  reload();
}

function formatTime(t) {
  if (!t) return '—';
  return String(t).replace('T', ' ').slice(0, 19);
}

/** 文本框批量输入 → 去空白/去重的标识数组（逗号/换行/空白分隔）。 */
function splitIds(text) {
  return [...new Set((text || '').split(/[,，\n\s]+/).map((s) => s.trim()).filter(Boolean))];
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
  if (tagDlg.saving) return;
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
  if (!row?.tagId || isDeletingTag(row.tagId)) return;
  setDeletingTag(row.tagId, true);
  try {
    await ElMessageBox.confirm(
      `删除标签「${row.tagName}」将同时删除其下 ${row.memberCount || 0} 条关联成员信息，是否继续？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    );
  } catch {
    setDeletingTag(row.tagId, false);
    return;
  }
  try {
    await deletePersonTag(row.tagId);
    ElMessage.success('已删除');
    await reload();
  } catch { /* 已提示 */ } finally {
    setDeletingTag(row.tagId, false);
  }
}

// ===== 全局导入 =====
const globalImp = reactive({ visible: false, dim: 'EMP', file: null, importing: false, errors: [] });
const globalUploaderRef = ref(null);
const memberUploaderRef = ref(null);

/**
 * 清空某个导入弹窗的已选文件（含 el-upload 内部列表）。
 * 导入失败/异常后必须调用：一方面 limit=1 会挡住再次选择，另一方面浏览器对
 * "选择后又在磁盘上被修改过"的 File 会拒发请求（ERR_UPLOAD_FILE_CHANGED），
 * 强制重新选择可同时规避这两个问题。
 */
function resetUpload(uploaderRef, state) {
  uploaderRef.value?.clearFiles?.();
  state.file = null;
}

/** limit=1 时再选文件的覆盖式替换（不配 on-exceed 新文件会被 el-upload 静默丢弃）。 */
function replaceOnExceed(uploaderRef, state) {
  return (files) => {
    const f = files && files[0];
    if (!f) return;
    uploaderRef.value?.clearFiles?.();
    f.uid = genFileId();
    uploaderRef.value?.handleStart?.(f);
    state.file = f;
  };
}

const onGlobalExceed = replaceOnExceed(globalUploaderRef, globalImp);

function openGlobalImport() {
  globalImp.errors = [];
  globalImp.dim = 'EMP';
  globalImp.visible = true;
  // 弹窗内容首次打开才挂载，等一拍再清残留列表
  nextTick(() => resetUpload(globalUploaderRef, globalImp));
}

/** 切换导入维度：清空已选文件与错误（不同维度模板不同）。 */
function onGlobalDimChange() {
  globalImp.errors = [];
  resetUpload(globalUploaderRef, globalImp);
}

async function doGlobalImport() {
  if (!globalImp.file || globalImp.importing) return;
  globalImp.importing = true;
  globalImp.errors = [];
  try {
    const res = await importPersonTags(globalImp.file, globalImp.dim);
    if (res && res.success) {
      const parts = [`新增关联 ${res.importedCount} 条`];
      if (res.createdTagCount) parts.push(`自动新建标签 ${res.createdTagCount} 个`);
      if (res.skippedCount) parts.push(`已存在跳过 ${res.skippedCount} 条`);
      ElMessage.success(`导入成功：${parts.join('，')}`);
      globalImp.visible = false;
      reload();
    } else {
      globalImp.errors = (res && res.errors) || [];
      resetUpload(globalUploaderRef, globalImp);
      ElMessage.error('导入未通过校验，请修正后重新选择文件');
    }
  } catch {
    // http.js 已弹错误消息；文件句柄可能已失效，清空强制重选
    resetUpload(globalUploaderRef, globalImp);
    ElMessage.warning('请重新选择文件后重试');
  } finally {
    globalImp.importing = false;
  }
}

async function downloadGlobalTpl() {
  try {
    const blob = await downloadPersonTagTemplate(globalImp.dim);
    saveBlob(blob, globalImp.dim === 'ORG' ? '业务标签机构导入模板.xlsx' : '业务标签员工导入模板.xlsx');
  } catch { /* 已提示 */ }
}

// ===== 详情（成员管理，按维度） =====
const detail = reactive({
  visible: false, tag: null, dim: 'EMP', loading: false,
  rows: [], total: 0, page: 1, pageSize: 20, error: ''
});

function openDetail(row) {
  detail.tag = row;
  detail.dim = 'EMP';
  detail.page = 1;
  detail.error = '';
  detail.visible = true;
  reloadMembers();
}

/** 切换详情维度：回到第 1 页并重新拉取该维度成员。 */
function onDetailDimChange() {
  detail.page = 1;
  reloadMembers();
}

async function reloadMembers() {
  if (!detail.tag) return;
  detail.loading = true;
  detail.error = '';
  try {
    const r = await listPersonTagMembers(detail.tag.tagId, {
      dim: detail.dim,
      pageNo: detail.page,
      pageSize: detail.pageSize
    });
    detail.rows = r?.records || [];
    detail.total = r?.total ?? detail.rows.length;
  } catch (error) {
    detail.rows = [];
    detail.total = 0;
    detail.error = `标签成员加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    detail.loading = false;
  }
}

/** 成员变动后同步列表页的关联成员数列。 */
function refreshBoth() {
  reloadMembers();
  reload();
}

// ===== 新增成员（员工工号 + 机构名称多选） =====
const memberAdd = reactive({
  visible: false,
  empText: '',
  orgDeptNos: [],
  orgLoading: false,
  saving: false
});
const memberOrgOptions = ref([]);
const memberOrgLoaded = ref(false);

function flattenMemberOrgOptions(nodes) {
  const options = [];
  const seenDeptNos = new Set();
  const walk = (items) => {
    for (const node of items || []) {
      const deptNo = node?.deptNo == null ? '' : String(node.deptNo).trim();
      if (deptNo && !seenDeptNos.has(deptNo)) {
        seenDeptNos.add(deptNo);
        options.push({
          deptNo,
          name: node.name || deptNo,
          code: node.code || ''
        });
      }
      if (node?.children?.length) walk(node.children);
    }
  };
  walk(nodes);
  return options;
}

async function ensureMemberOrgOptions() {
  if (memberOrgLoaded.value || memberAdd.orgLoading) return;
  memberAdd.orgLoading = true;
  try {
    memberOrgOptions.value = flattenMemberOrgOptions(await getOrgTree({ strict: true }));
    memberOrgLoaded.value = true;
  } catch {
    memberOrgOptions.value = [];
    ElMessage.warning('机构列表加载失败，请重试');
  } finally {
    memberAdd.orgLoading = false;
  }
}

function openMemberAdd() {
  memberAdd.empText = '';
  memberAdd.orgDeptNos = [];
  memberAdd.visible = true;
  ensureMemberOrgOptions();
}

async function saveMemberAdd() {
  if (memberAdd.saving) return;
  const usernames = splitIds(memberAdd.empText);
  const orgDeptNos = [...memberAdd.orgDeptNos];
  if (!usernames.length && !orgDeptNos.length) {
    ElMessage.warning('请至少输入一个员工工号或选择一个机构');
    return;
  }
  memberAdd.saving = true;
  try {
    const added = await addPersonTagMembers(detail.tag.tagId, { usernames, orgDeptNos });
    const submitted = usernames.length + orgDeptNos.length;
    ElMessage.success(`新增 ${added} 个成员${added < submitted ? `（跳过已存在 ${submitted - added} 个）` : ''}`);
    memberAdd.visible = false;
    refreshBoth();
  } catch { /* 已提示（含无效工号/机构号清单） */ } finally {
    memberAdd.saving = false;
  }
}

// ===== 修改成员（按行维度） =====
const memberEdit = reactive({ visible: false, row: null, isOrg: false, value: '', saving: false });

function openMemberEdit(row) {
  memberEdit.row = row;
  memberEdit.isOrg = row.dimType === 'ORG';
  memberEdit.value = memberEdit.isOrg ? row.orgDeptNo : row.username;
  memberEdit.visible = true;
}

async function saveMemberEdit() {
  if (memberEdit.saving) return;
  const value = (memberEdit.value || '').trim();
  if (!value) {
    ElMessage.warning(memberEdit.isOrg ? '机构编号不能为空' : '工号不能为空');
    return;
  }
  memberEdit.saving = true;
  try {
    const data = memberEdit.isOrg ? { orgDeptNo: value } : { username: value };
    await updatePersonTagMember(detail.tag.tagId, memberEdit.row.id, data);
    ElMessage.success('已修改');
    memberEdit.visible = false;
    reloadMembers();
  } catch { /* 已提示 */ } finally {
    memberEdit.saving = false;
  }
}

// ===== 删除成员 =====
async function onRemoveMember(row) {
  const label = row.dimType === 'ORG' ? (row.orgName || row.orgDeptNo) : row.username;
  try {
    await ElMessageBox.confirm(
      `将「${label}」从标签「${detail.tag.tagName}」移除？`,
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

// ===== 成员导入（当前维度全量覆盖） =====
const memberImp = reactive({ visible: false, file: null, importing: false, errors: [] });
const onMemberExceed = replaceOnExceed(memberUploaderRef, memberImp);

function openMemberImport() {
  memberImp.errors = [];
  memberImp.visible = true;
  // 弹窗内容首次打开才挂载，等一拍再清残留列表
  nextTick(() => resetUpload(memberUploaderRef, memberImp));
}

async function doMemberImport() {
  if (!memberImp.file || memberImp.importing) return;
  const dimLabel = detail.dim === 'ORG' ? '机构' : '员工';
  // 全量覆盖前的强提示（需求硬约束）
  try {
    await ElMessageBox.confirm(
      `本次导入将清空标签「${detail.tag.tagName}」现有全部${dimLabel}维度成员，并以文件内容全量覆盖（不影响另一维度），是否继续？`,
      '全量覆盖确认',
      { type: 'warning', confirmButtonText: '覆盖导入', cancelButtonText: '取消' }
    );
  } catch { return; }
  memberImp.importing = true;
  memberImp.errors = [];
  try {
    const res = await importPersonTagMembers(detail.tag.tagId, memberImp.file, detail.dim);
    if (res && res.success) {
      ElMessage.success(`导入成功，当前标签${dimLabel}维度共 ${res.importedCount} 个成员`);
      memberImp.visible = false;
      refreshBoth();
    } else {
      memberImp.errors = (res && res.errors) || [];
      resetUpload(memberUploaderRef, memberImp);
      ElMessage.error('导入未通过校验，现有数据未改动，请修正后重新选择文件');
    }
  } catch {
    // http.js 已弹错误消息；文件句柄可能已失效（如磁盘上原地修改过），清空强制重选
    resetUpload(memberUploaderRef, memberImp);
    ElMessage.warning('请重新选择文件后重试');
  } finally {
    memberImp.importing = false;
  }
}

async function downloadMemberTpl() {
  try {
    const blob = await downloadPersonTagMemberTemplate(detail.dim);
    saveBlob(blob, detail.dim === 'ORG' ? '标签机构成员导入模板.xlsx' : '标签员工成员导入模板.xlsx');
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
.person-tags-page :deep(.el-drawer__body) .toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.toolbar-right {
  margin-left: auto;
  display: flex;
  gap: var(--space-2);
}
.muted {
  color: var(--color-text-muted);
  font-size: 12px;
}
.dim-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.dim-label {
  color: var(--color-text);
  font-size: 13px;
}
.imp-tip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
}
.imp-errors {
  background: var(--color-danger-bg);
  border: 1px solid var(--color-danger-fg);
  border-radius: var(--radius-control);
  margin-top: var(--space-3);
  padding: var(--space-3);
}
.imp-errors .err-title {
  color: var(--color-danger-fg);
  font-size: 13px;
  margin-bottom: var(--space-2);
}
.imp-errors .err-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.imp-errors .err-table th,
.imp-errors .err-table td {
  border: 1px solid var(--color-border);
  padding: var(--space-1) var(--space-2);
  text-align: left;
}
</style>
