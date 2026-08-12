<template>
  <main class="bp-crud announcement-detail-page" aria-labelledby="announcement-detail-title">
    <header class="page-h">
      <PageTitle id="announcement-detail-title" />
      <div class="actions action-group" role="group" aria-label="公告详情操作">
        <template v-if="isAdmin && data && data.status !== '已删除'">
          <el-button
            :type="data.isPinned ? 'warning' : 'primary'"
            :loading="pinning"
            :disabled="pinning || deleting"
            @click="doTogglePin"
          >
            {{ data.isPinned ? '取消置顶' : '置顶' }}
          </el-button>
          <el-popconfirm title="确认删除该公告？删除后无法恢复。" @confirm="doDelete">
            <template #reference>
              <el-button type="danger" :loading="deleting" :disabled="pinning || deleting">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
        <el-button :disabled="deleting" @click="goBack">返回</el-button>
      </div>
    </header>

    <article
      class="card-section data-panel ann-detail"
      aria-label="公告详情内容"
      aria-describedby="announcement-detail-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <p id="announcement-detail-state" class="table-state" role="status" aria-live="polite">
        {{ loading ? '公告详情加载中' : data ? '公告详情已加载' : '暂无公告详情' }}
      </p>
      <template v-if="data">
        <h2 class="ann-title">{{ data.title }}</h2>
        <div class="ann-meta">
          发布时间：{{ fmtDate(data.publishDate) }}
          <span class="meta-separator" aria-hidden="true">|</span>
          发布人：{{ data.publisherName || '-' }}
          <el-tag v-if="data.status === '已删除'" class="tag-info deleted-tag" effect="plain" size="small">已删除</el-tag>
        </div>
        <el-divider />
        <div class="ann-content" v-html="renderContent(data.content)"></div>

        <template v-if="data.files && data.files.length">
          <el-divider />
          <section class="ann-files" aria-label="公告附件">
            <h3 class="file-label">附件（{{ data.files.length }} 个文件）</h3>
            <div v-for="f in data.files" :key="f.id" class="file-item">
              <el-link type="primary" @click="downloadFile(f)">
                {{ f.fileName || f.originalName || '文件' }}
              </el-link>
              <span v-if="f.fileSize" class="file-size">({{ formatSize(f.fileSize) }})</span>
            </div>
          </section>
        </template>
        <template v-else>
          <el-divider />
          <section class="ann-files" aria-label="公告附件">
            <h3 class="file-label">附件（0 个文件）</h3>
          </section>
        </template>
      </template>
      <el-empty v-else-if="!loading" description="暂无公告详情" />
    </article>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getAnnouncementDetail, togglePinAnnouncement, deleteAnnouncement } from '@/api/announcement';
import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const pinning = ref(false);
const deleting = ref(false);
const data = ref(null);
const isAdmin = computed(() => userStore.isSystemAdmin);

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
  if (pinning.value || deleting.value) return;
  pinning.value = true;
  try {
    await togglePinAnnouncement(route.params.id);
    ElMessage.success(data.value.isPinned ? '已取消置顶' : '已置顶');
    data.value.isPinned = !data.value.isPinned;
  } catch (e) { ElMessage.error('操作失败：' + (e?.message || e)); } finally { pinning.value = false; }
}

async function doDelete() {
  if (deleting.value || pinning.value) return;
  deleting.value = true;
  try {
    await deleteAnnouncement(route.params.id);
    ElMessage.success('已删除');
    router.back();
  } catch (e) { ElMessage.error('删除失败：' + (e?.message || e)); } finally { deleting.value = false; }
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
.ann-detail { min-height: 220px; }
.ann-title { color: var(--color-text-strong); font-size: 20px; font-weight: 600; line-height: 28px; margin-bottom: var(--space-2); }
.ann-meta { color: var(--color-text-muted); font-size: 12px; line-height: 18px; }
.meta-separator { margin: 0 var(--space-2); }
.deleted-tag { margin-left: var(--space-3); }
.ann-content { color: var(--color-text); font-size: 14px; line-height: 1.8; min-height: 80px; }
.file-label { color: var(--color-text-strong); font-size: 14px; font-weight: 600; line-height: 22px; margin-bottom: var(--space-2); }
.file-item { margin-bottom: var(--space-1); }
.file-size { color: var(--color-text-muted); font-size: 12px; margin-left: var(--space-2); }
</style>
