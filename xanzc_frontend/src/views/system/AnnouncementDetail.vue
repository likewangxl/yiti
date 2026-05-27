<template>
  <div>
    <div class="page-h">
      <h1>公告详情</h1>
      <div class="actions">
        <template v-if="isAdmin && data && data.status !== '已删除'">
          <el-button :type="data.isPinned ? 'warning' : 'primary'" @click="doTogglePin">{{ data.isPinned ? '取消置顶' : '置顶' }}</el-button>
          <el-popconfirm title="确认删除该公告？" @confirm="doDelete">
            <template #reference>
              <el-button type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
        <el-button @click="goBack">← 返回</el-button>
      </div>
    </div>

    <div class="card-section ann-detail" v-loading="loading">
      <template v-if="data">
        <h2 class="ann-title">{{ data.title }}</h2>
        <div class="ann-meta">
          发布时间：{{ fmtDate(data.publishDate) }}
          &nbsp;|&nbsp; 发布人：{{ data.publisherName || '-' }}
          <el-tag v-if="data.status === '已删除'" type="info" size="small" style="margin-left:12px">已删除</el-tag>
        </div>
        <el-divider />
        <div class="ann-content" v-html="renderContent(data.content)"></div>

        <template v-if="data.files && data.files.length">
          <el-divider />
          <div class="ann-files">
            <div class="file-label">附件（{{ data.files.length }} 个文件）</div>
            <div v-for="f in data.files" :key="f.id" class="file-item">
              <el-link type="primary" @click="downloadFile(f)">
                {{ f.fileName || f.originalName || '文件' }}
              </el-link>
              <span class="file-size" v-if="f.fileSize">({{ formatSize(f.fileSize) }})</span>
            </div>
          </div>
        </template>
        <template v-else>
          <el-divider />
          <div class="ann-files">
            <div class="file-label">附件（0 个文件）</div>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getAnnouncementDetail, togglePinAnnouncement, deleteAnnouncement } from '@/api/announcement';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const data = ref(null);
const isAdmin = computed(() => route.query.admin === '1');

const API_BASE = import.meta.env.VITE_API_BASE || '/api';

function fmtDate(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 16);
}
function formatSize(bytes) {
  if (!bytes) return '';
  if (bytes < 1024) return bytes + 'B';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + 'KB';
  return (bytes / 1048576).toFixed(1) + 'MB';
}
function renderContent(c) {
  if (!c) return '';
  return c.replace(/\n/g, '<br>');
}
function downloadFile(f) {
  window.open(API_BASE + '/portal/announcements/files/' + f.id + '/download', '_blank');
}
async function doTogglePin() {
  try {
    await togglePinAnnouncement(route.params.id);
    ElMessage.success(data.value.isPinned ? '已取消置顶' : '已置顶');
    data.value.isPinned = !data.value.isPinned;
  } catch (e) { ElMessage.error('操作失败：' + (e?.message || e)); }
}
async function doDelete() {
  try {
    await deleteAnnouncement(route.params.id);
    ElMessage.success('已删除');
    router.back();
  } catch (e) { ElMessage.error('删除失败：' + (e?.message || e)); }
}
function goBack() {
  router.back();
}

onMounted(async () => {
  const id = route.params.id;
  if (!id) return;
  loading.value = true;
  try {
    data.value = await getAnnouncementDetail(id);
  } catch { /* handled by http interceptor */ }
  finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
.ann-detail {
  padding: 24px;
  .ann-title { font-size: 20px; font-weight: 600; margin-bottom: 10px; }
  .ann-meta { font-size: 13px; color: $text-3; }
  .ann-content { font-size: 14px; line-height: 1.8; min-height: 80px; }
  .ann-files {
    .file-label { font-size: 14px; font-weight: 500; margin-bottom: 10px; }
    .file-item { margin-bottom: 6px; }
    .file-size { font-size: 12px; color: $text-3; margin-left: 6px; }
  }
}
</style>
