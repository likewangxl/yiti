<!--
  公告查询 —— 历史数据查询
  来自 sys_notice，按 SEQ_NO 倒序。点击「查看」展示正文 + 附件。
  后端：GET /api/reports/notices（列表） / GET /api/reports/notices/{id}（详情）
-->
<template>
  <main class="bp-crud notice-query" aria-labelledby="notice-query-title">
    <div class="page-h">
      <PageTitle id="notice-query-title" />
      <span class="desc">通知公告查询，按序号倒序</span>
    </div>

    <section class="card-section filter-bar" aria-label="公告筛选">
      <el-form inline size="default" class="filter-form" @submit.prevent>
        <el-form-item label="标题">
          <el-input v-model="q.title" placeholder="模糊" clearable style="width:220px" />
        </el-form-item>
        <el-form-item label="是否公开">
          <el-select v-model="q.isPublic" placeholder="全部" clearable style="width:120px">
            <el-option label="公开" value="1" />
            <el-option label="私有" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="起" end-placeholder="止" style="width:240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel" aria-label="公告列表" aria-describedby="notice-table-state" :aria-busy="loading ? 'true' : 'false'">
      <div class="toolbar">
        <div>
          <h2 id="notice-table-heading" class="section-title">公告列表</h2>
          <p class="hint">按标题、公开范围和创建时间查询历史公告。</p>
        </div>
        <p id="notice-table-state" class="table-state" role="status" aria-live="polite">
          {{ errorMessage || (loading ? '公告列表加载中' : rows.length ? `共 ${total} 条公告` : '暂无公告') }}
        </p>
      </div>
      <div v-if="errorMessage" class="error-state" role="alert">
        <span>{{ errorMessage }}</span>
        <el-button link type="primary" @click="reload">重试</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="default" empty-text="暂无公告"
                aria-labelledby="notice-table-heading" aria-describedby="notice-table-state">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column label="是否公开" width="100">
          <template #default="{ row }">
            <el-tag :class="row.isPublic === '1' ? 'tag-success' : 'tag-info'" effect="plain" size="small">
              {{ row.isPublic === '1' ? '公开' : '私有' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column prop="seqNo" label="序号" width="90" align="right" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo" v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total" background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="reload" @current-change="reload" />
      </div>
    </section>

    <!-- 正文弹框 -->
    <el-dialog v-model="dlg.show" class="bp-crud-dialog" :title="dlg.data.title || '公告详情'" width="720px" top="6vh">
      <section v-loading="dlg.loading" class="detail-section notice-detail" aria-label="公告详情" :aria-busy="dlg.loading ? 'true' : 'false'">
        <p v-if="dlg.errorMessage" class="error-state" role="alert">{{ dlg.errorMessage }}</p>
        <div class="meta">
          <span>创建时间：{{ dlg.data.createTime || '-' }}</span>
          <el-tag :class="dlg.data.isPublic === '1' ? 'tag-success' : 'tag-info'" effect="plain" size="small">
            {{ dlg.data.isPublic === '1' ? '公开' : '私有' }}
          </el-tag>
        </div>
        <div class="content">{{ dlg.data.content || '（无正文）' }}</div>
        <div v-if="dlg.data.extend" class="extend">
          <span>附件：{{ dlg.data.extend }}</span>
          <el-button link type="primary" :loading="dlg.downloading" @click="onDownloadAtt">下载附件</el-button>
        </div>
      </section>
      <template #footer><el-button @click="dlg.show = false">关闭</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listNotices, getNotice, downloadNoticeAttachment } from '@/api/history';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const errorMessage = ref('');
const pageNo = ref(1);
const pageSize = ref(20);
const q = reactive({ title: '', isPublic: '' });
const dateRange = ref(null);

async function reload() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value };
    if (q.title) params.title = q.title.trim();
    if (q.isPublic) params.isPublic = q.isPublic;
    if (dateRange.value?.[0]) params.createTimeStart = dateRange.value[0];
    if (dateRange.value?.[1]) params.createTimeEnd = dateRange.value[1] + ' 23:59:59';
    const r = await listNotices(params);
    rows.value = r.records || [];
    total.value = r.total || 0;
  } catch { rows.value = []; total.value = 0; errorMessage.value = '公告列表加载失败，请重试'; }
  finally { loading.value = false; }
}
function onSearch() { pageNo.value = 1; reload(); }
function onReset() { q.title = ''; q.isPublic = ''; dateRange.value = null; pageNo.value = 1; reload(); }

const dlg = reactive({ show: false, loading: false, downloading: false, data: {}, errorMessage: '' });
async function openDetail(row) {
  if (dlg.loading) return;
  dlg.data = { ...row };
  dlg.show = true; dlg.loading = true; dlg.errorMessage = '';
  try {
    const d = await getNotice(row.noticId);
    if (d && d.noticId) dlg.data = d;
  } catch { dlg.errorMessage = '公告详情加载失败，请重试'; ElMessage.error('加载公告失败'); }
  finally { dlg.loading = false; }
}

// 下载公告附件（EXTEND 作为对象存储文件标识，后端流式返回）
async function onDownloadAtt() {
  if (!dlg.data.noticId) return;
  dlg.downloading = true;
  try {
    await downloadNoticeAttachment(dlg.data.noticId, dlg.data.extend || '附件');
  } catch { ElMessage.error('附件下载失败（本地未配置对象存储时仅内网可用）'); }
  finally { dlg.downloading = false; }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.notice-query { min-width: 0; }
.notice-detail {
  .meta { display: flex; align-items: center; gap: var(--space-3); color: var(--color-text-muted); font-size: 13px; margin-bottom: var(--space-3); }
  .content { white-space: pre-wrap; line-height: 1.7; font-size: 14px; min-height: 80px; }
  .extend { margin-top: var(--space-4); padding-top: var(--space-3); border-top: 1px solid var(--color-border); font-size: 13px; color: var(--color-text);
            display: flex; align-items: center; gap: var(--space-3); }
}
.error-state {
  align-items: center;
  background: var(--color-danger-bg);
  border: 1px solid var(--color-border);
  color: var(--color-danger-fg);
  display: flex;
  gap: var(--space-3);
  justify-content: space-between;
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
}
</style>
