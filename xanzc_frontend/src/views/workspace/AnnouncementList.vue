<template>
  <div>
    <div class="page-h">
      <PageTitle />
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
        <el-table-column label="操作" width="100" fixed="right">
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { listAnnouncements } from '@/api/announcement';

const router = useRouter();

function fmtDate(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 16);
}

const rows = ref([]);
const loading = ref(false);
const filters = reactive({ keyword: '' });
const pager = reactive({ pageNo: 1, pageSize: 20, total: 0 });

function resetFilters() { filters.keyword = ''; pager.pageNo = 1; reload(); }

async function reload() {
  loading.value = true;
  try {
    const r = await listAnnouncements({ pageNo: pager.pageNo, pageSize: pager.pageSize, keyword: filters.keyword || undefined });
    rows.value = r?.records || (Array.isArray(r) ? r : []);
    pager.total = r?.total ?? rows.value.length;
  } catch { rows.value = []; }
  finally { loading.value = false; }
}

function goDetail(row) {
  router.push('/announcement/' + row.id);
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
