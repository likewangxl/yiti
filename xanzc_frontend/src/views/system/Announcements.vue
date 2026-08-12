<template>
  <main class="bp-crud announcements-page" aria-labelledby="announcements-title">
    <header class="page-h">
      <PageTitle id="announcements-title" />
      <div v-if="userStore.isSystemAdmin" class="actions action-group" role="group" aria-label="公告管理操作">
        <el-button type="primary" @click="openCreate">新增公告</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="公告管理筛选">
      <el-form class="filter-form" inline size="default" aria-label="公告管理筛选">
        <el-form-item label="公告名称">
          <el-input
            v-model="filters.keyword"
            aria-label="按公告名称筛选"
            placeholder="输入公告名称模糊查询"
            clearable
            style="width:260px"
            @keyup.enter="reload"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section
      class="card-section data-panel"
      aria-label="公告管理列表"
      aria-describedby="admin-announcement-list-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="admin-announcement-list-heading" class="section-title">公告管理列表</h2>
          <p class="hint">系统管理员可进入详情页置顶、取消置顶或删除公告。</p>
        </div>
        <p id="admin-announcement-list-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '公告管理列表加载中' : rows.length ? `共 ${pager.total} 条公告` : '暂无公告数据' }}
        </p>
      </div>

      <el-table
        :data="rows"
        size="default"
        v-loading="loading"
        empty-text="暂无公告数据"
        aria-labelledby="admin-announcement-list-heading"
        aria-describedby="admin-announcement-list-state"
      >
        <el-table-column prop="title" label="公告名称" min-width="260" show-overflow-tooltip />
        <el-table-column label="发布日期" width="180">
          <template #default="{ row }">{{ fmtDate(row.publishDate) }}</template>
        </el-table-column>
        <el-table-column label="发布人" width="140">
          <template #default="{ row }">{{ row.publisherName || row.publisherId || '-' }}</template>
        </el-table-column>
        <el-table-column label="是否置顶" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isPinned" class="tag-danger" size="small" effect="plain">置顶</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === '已删除'" class="tag-info" size="small" effect="plain">已删除</el-tag>
            <el-tag v-else class="tag-success" size="small" effect="plain">已发布</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <nav class="pager" aria-label="公告管理列表分页">
        <el-pagination
          v-model:current-page="pager.pageNo"
          v-model:page-size="pager.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pager.total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="reload"
          @current-change="reload"
        />
      </nav>
    </section>

    <el-dialog v-model="createDlg.show" class="bp-crud-dialog" title="新增公告" width="680px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createDlg.form" :rules="createDlg.rules" label-width="80px">
        <el-form-item label="公告标题" prop="title">
          <el-input v-model="createDlg.form.title" aria-label="公告标题" placeholder="请输入公告标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="公告内容" prop="content">
          <el-input v-model="createDlg.form.content" aria-label="公告内容" type="textarea" :rows="8" placeholder="请输入公告内容" />
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload
            :auto-upload="false"
            multiple
            :disabled="createDlg.saving"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            :file-list="createDlg.fileList"
          >
            <el-button type="primary" plain :disabled="createDlg.saving">选择文件</el-button>
            <template #tip><div class="el-upload__tip">支持多文件上传，发布后自动上传</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="createDlg.saving" @click="createDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="createDlg.saving" :disabled="createDlg.saving" @click="doCreate">发布</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listAnnouncementsAdmin, createAnnouncement, uploadAnnouncementFile, togglePinAnnouncement, deleteAnnouncement } from '@/api/announcement';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();

function fmtDate(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 16);
}

const rows = ref([]);
const loading = ref(false);
const pinning = ref(false);
const deleting = ref(false);
const filters = reactive({ keyword: '' });
const pager = reactive({ pageNo: 1, pageSize: 20, total: 0 });

function resetFilters() { filters.keyword = ''; pager.pageNo = 1; reload(); }

async function reload() {
  loading.value = true;
  try {
    const r = await listAnnouncementsAdmin({ pageNo: pager.pageNo, pageSize: pager.pageSize, keyword: filters.keyword || undefined });
    rows.value = r?.records || (Array.isArray(r) ? r : []);
    pager.total = r?.total ?? rows.value.length;
  } catch { rows.value = []; }
  finally { loading.value = false; }
}

function goDetail(row) {
  router.push({ path: '/system/announcements/' + row.id, query: { admin: '1' } });
}

const createFormRef = ref(null);
const createDlg = reactive({
  show: false,
  saving: false,
  form: { title: '', content: '' },
  fileList: [],
  rawFiles: [],
  rules: {
    title: [{ required: true, message: '公告标题必填', trigger: 'blur' }],
    content: [{ required: true, message: '公告内容必填', trigger: 'blur' }]
  }
});

function openCreate() {
  createDlg.form = { title: '', content: '' };
  createDlg.fileList = [];
  createDlg.rawFiles = [];
  createDlg.show = true;
}

function onFileChange(file) {
  if (createDlg.saving || !file?.raw) return;
  createDlg.rawFiles.push(file.raw);
}

function onFileRemove(file) {
  createDlg.rawFiles = createDlg.rawFiles.filter(rawFile => rawFile !== file.raw);
}

async function doCreate() {
  if (createDlg.saving) return;
  createDlg.saving = true;
  try {
    try { await createFormRef.value?.validate(); } catch { return; }
    const annId = await createAnnouncement({
      title: createDlg.form.title,
      content: createDlg.form.content
    });
    for (const rawFile of createDlg.rawFiles) {
      await uploadAnnouncementFile(annId, rawFile);
    }
    ElMessage.success('公告已发布');
    createDlg.show = false;
    await reload();
  } catch (e) {
    ElMessage.error('发布失败：' + (e?.message || e));
  } finally { createDlg.saving = false; }
}

async function doTogglePin(row) {
  if (pinning.value) return;
  pinning.value = true;
  try {
    await togglePinAnnouncement(row.id);
    ElMessage.success(row.isPinned ? '已取消置顶' : '已置顶');
    await reload();
  } catch (e) {
    ElMessage.error('操作失败：' + (e?.message || e));
  } finally { pinning.value = false; }
}

async function doDelete(row) {
  if (deleting.value) return;
  deleting.value = true;
  try {
    await deleteAnnouncement(row.id);
    ElMessage.success('已删除');
    await reload();
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e));
  } finally { deleting.value = false; }
}

onMounted(reload);
</script>
