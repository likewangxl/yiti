<!--
  公告查询 —— 历史数据查询
  来自 sys_notice，按 SEQ_NO 倒序。点击「查看」展示正文 + 附件。
  后端：GET /api/reports/notices（列表） / GET /api/reports/notices/{id}（详情）
-->
<template>
  <div class="notice-query">
    <div class="page-h">
      <PageTitle />
      <span class="desc">通知公告查询，按序号倒序</span>
    </div>

    <div class="card-section">
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

      <el-table :data="rows" v-loading="loading" border stripe size="default" empty-text="暂无公告">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column label="是否公开" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isPublic === '1' ? 'success' : 'info'" effect="plain" size="small">
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
    </div>

    <!-- 正文弹框 -->
    <el-dialog v-model="dlg.show" :title="dlg.data.title || '公告详情'" width="720px" top="6vh">
      <div v-loading="dlg.loading" class="notice-detail">
        <div class="meta">
          <span>创建时间：{{ dlg.data.createTime || '-' }}</span>
          <el-tag :type="dlg.data.isPublic === '1' ? 'success' : 'info'" effect="plain" size="small">
            {{ dlg.data.isPublic === '1' ? '公开' : '私有' }}
          </el-tag>
        </div>
        <div class="content">{{ dlg.data.content || '（无正文）' }}</div>
        <div v-if="dlg.data.extend" class="extend">
          <span>附件：{{ dlg.data.extend }}</span>
          <el-button link type="primary" :loading="dlg.downloading" @click="onDownloadAtt">下载附件</el-button>
        </div>
      </div>
      <template #footer><el-button @click="dlg.show = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listNotices, getNotice, downloadNoticeAttachment } from '@/api/history';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const q = reactive({ title: '', isPublic: '' });
const dateRange = ref(null);

async function reload() {
  loading.value = true;
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value };
    if (q.title) params.title = q.title.trim();
    if (q.isPublic) params.isPublic = q.isPublic;
    if (dateRange.value?.[0]) params.createTimeStart = dateRange.value[0];
    if (dateRange.value?.[1]) params.createTimeEnd = dateRange.value[1] + ' 23:59:59';
    const r = await listNotices(params);
    rows.value = r.records || [];
    total.value = r.total || 0;
  } catch { rows.value = []; total.value = 0; }
  finally { loading.value = false; }
}
function onSearch() { pageNo.value = 1; reload(); }
function onReset() { q.title = ''; q.isPublic = ''; dateRange.value = null; pageNo.value = 1; reload(); }

const dlg = reactive({ show: false, loading: false, downloading: false, data: {} });
async function openDetail(row) {
  dlg.data = { ...row };
  dlg.show = true; dlg.loading = true;
  try {
    const d = await getNotice(row.noticId);
    if (d && d.noticId) dlg.data = d;
  } catch { ElMessage.error('加载公告失败'); }
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
.filter-form { margin-bottom: 12px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.notice-detail {
  .meta { display: flex; align-items: center; gap: 12px; color: #888; font-size: 13px; margin-bottom: 12px; }
  .content { white-space: pre-wrap; line-height: 1.7; font-size: 14px; min-height: 80px; }
  .extend { margin-top: 14px; padding-top: 12px; border-top: 1px solid #eee; font-size: 13px; color: #555;
            display: flex; align-items: center; gap: 12px; }
}
</style>
