<template>
  <div>
    <div class="page-h">
      <PageTitle />
      <div class="actions">
        <el-button v-if="userStore.isSystemAdmin" type="primary" @click="openCreate">+ 新增公告</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form inline size="default">
        <el-form-item label="公告名称">
          <el-input v-model="filters.keyword" placeholder="输入公告名称模糊查询" clearable style="width:260px" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无公告">
        <el-table-column prop="title" label="公告名称" min-width="260" show-overflow-tooltip />
        <el-table-column label="发布日期" width="180">
          <template #default="{ row }">{{ fmtDate(row.publishDate) }}</template>
        </el-table-column>
        <el-table-column label="发布人" width="140">
          <template #default="{ row }">{{ row.publisherName || row.publisherId || '-' }}</template>
        </el-table-column>
        <el-table-column label="是否置顶" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isPinned" type="danger" size="small">置顶</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === '已删除'" type="info" size="small">已删除</el-tag>
            <el-tag v-else type="success" size="small">已发布</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
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
      </div>
    </div>

    <!-- 新增公告弹窗 -->
    <el-dialog v-model="createDlg.show" title="新增公告" width="680px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createDlg.form" :rules="createDlg.rules" label-width="80px">
        <el-form-item label="公告标题" prop="title">
          <el-input v-model="createDlg.form.title" placeholder="请输入公告标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="公告内容" prop="content">
          <el-input v-model="createDlg.form.content" type="textarea" :rows="8" placeholder="请输入公告内容" />
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload
            :auto-upload="false"
            multiple
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            :file-list="createDlg.fileList"
          >
            <el-button type="primary" plain>选择文件</el-button>
            <template #tip><div class="el-upload__tip">支持多文件上传，发布后自动上传</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="createDlg.saving" @click="doCreate">发布</el-button>
      </template>
    </el-dialog>
  </div>
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

// === 列表 ===
const rows = ref([]);
const loading = ref(false);
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

// === 新增 ===
const createFormRef = ref(null);
const createDlg = reactive({
  show: false, saving: false,
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
  createDlg.rawFiles.push(file.raw);
}
function onFileRemove(file) {
  createDlg.rawFiles = createDlg.rawFiles.filter(f => f !== file.raw);
}

async function doCreate() {
  try { await createFormRef.value?.validate(); } catch { return; }
  createDlg.saving = true;
  try {
    // 先创建公告
    const annId = await createAnnouncement({
      title: createDlg.form.title,
      content: createDlg.form.content
    });
    // 再逐个上传文件
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

// === 置顶 ===
async function doTogglePin(row) {
  try {
    await togglePinAnnouncement(row.id);
    ElMessage.success(row.isPinned ? '已取消置顶' : '已置顶');
    await reload();
  } catch (e) {
    ElMessage.error('操作失败：' + (e?.message || e));
  }
}

// === 删除 ===
async function doDelete(row) {
  try {
    await deleteAnnouncement(row.id);
    ElMessage.success('已删除');
    await reload();
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e));
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
