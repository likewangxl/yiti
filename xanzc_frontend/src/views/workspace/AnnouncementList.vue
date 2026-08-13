<template>
<main v-bp-overflow-tooltip class="bp-crud announcement-list-page" aria-labelledby="announcement-list-title">
    <header class="page-h">
      <PageTitle id="announcement-list-title" />
    </header>

    <section class="card-section filter-bar" aria-label="公告筛选">
      <el-form class="filter-form" inline size="default" aria-label="公告筛选">
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
      aria-label="公告列表"
      aria-describedby="announcement-list-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="announcement-list-heading" class="section-title">公告列表</h2>
          <p class="hint">查看分行已发布的公告及发布时间。</p>
        </div>
        <p id="announcement-list-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '公告列表加载中' : rows.length ? `共 ${pager.total} 条公告` : '暂无公告数据' }}
        </p>
      </div>

      <el-table
        :data="rows"
        size="default"
        v-loading="loading"
        empty-text="暂无公告数据"
        aria-labelledby="announcement-list-heading"
        aria-describedby="announcement-list-state"
      >
        <el-table-column prop="title" label="公告名称" min-width="260" show-overflow-tooltip />
        <el-table-column label="发布日期" width="180">
          <template #default="{ row }">{{ fmtDate(row.publishDate) }}</template>
        </el-table-column>
        <el-table-column label="发布人" width="140">
          <template #default="{ row }">{{ row.publisherName || row.publisherId || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <nav class="pager" aria-label="公告列表分页">
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
  </main>
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
