<!--
  定价审批查询 —— 历史数据查询（只读）
  数据源：AMAS_PRICE_APPROVAL，按申请时间倒序。
  后端：GET /api/reports/amas-price-approvals （AmasPriceApprovalController.list）
  查询：客户名称 / 申请人姓名 模糊；数据范围按 BizType.REPORT（本机构/本级及下级机构，基于 APPLY_DEPTNO）。
-->
<template>
  <div class="price-approval">
    <div class="page-h">
      <h1>定价审批查询</h1>
      <span class="desc">AMAS 定价审批数据查询，按申请时间倒序；数据范围按机构权限控制</span>
    </div>

    <!-- 顶部查询项 -->
    <el-form :model="q" inline class="filter-form" @submit.prevent>
      <el-form-item label="客户名称">
        <el-input v-model="q.custName" placeholder="模糊匹配" clearable style="width:180px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="申请人姓名">
        <el-input v-model="q.applyFullname" placeholder="模糊匹配" clearable style="width:150px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="审批状态">
        <el-select v-model="q.apprStatus" placeholder="全部" clearable style="width:130px">
          <el-option label="待审批" value="0" />
          <el-option label="已通过" value="1" />
          <el-option label="未通过" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="申请时间">
        <el-date-picker
          v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
          start-placeholder="起" end-placeholder="止" style="width:240px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading" border stripe size="default" empty-text="暂无数据">
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column label="申请人" min-width="130">
        <template #default="{ row }">
          <a class="link-cell" @click="goDetail(row)">
            <div class="main">{{ row.applyFullname || row.applyUsername || '-' }}</div>
            <div class="sub" v-if="row.applyUsername">{{ row.applyUsername }}</div>
          </a>
        </template>
      </el-table-column>
      <el-table-column label="客户" min-width="170">
        <template #default="{ row }">
          <div class="main">{{ row.custName || '-' }}</div>
          <div class="sub" v-if="row.custId">{{ row.custId }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="money" label="金额" min-width="110" align="right" />
      <el-table-column prop="businType" label="业务类型" min-width="110" show-overflow-tooltip />
      <el-table-column label="对公/零售" width="100">
        <template #default="{ row }">{{ OR_RETAIL[row.businOrRetail] || row.businOrRetail || '-' }}</template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" width="170" />
      <el-table-column label="审批状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG[row.apprStatus] || 'info'" size="small">
            {{ APPR_STATUS[row.apprStatus] || row.apprStatus || '-' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background layout="total, prev, pager, next, sizes"
        :total="total" :current-page="page.pageNo" :page-size="page.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPage" @size-change="onSize" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { listPriceApprovals } from '@/api/report';

const router = useRouter();

const APPR_STATUS = { '0': '待审批', '1': '已通过', '2': '未通过' };
const STATUS_TAG = { '0': 'warning', '1': 'success', '2': 'danger' };
// 对公/零售：数字 1=对公、2=零售（兼容历史 CORP/RETAIL 字面值）
const OR_RETAIL = { '1': '对公', '2': '零售', CORP: '对公', RETAIL: '零售' };

// 点击申请人 → 跳转定价审批详情
function goDetail(row) {
  if (!row?.priceApprId) return;
  router.push({ name: 'HistoryPriceApprovalDetail', params: { priceApprId: row.priceApprId } });
}

const q = reactive({ custName: '', applyFullname: '', apprStatus: '' });
const dateRange = ref([]);
const page = reactive({ pageNo: 1, pageSize: 20 });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const params = {
      pageNo: page.pageNo, pageSize: page.pageSize,
      custName: q.custName || undefined,
      applyFullname: q.applyFullname || undefined,
      apprStatus: q.apprStatus || undefined,
      applyTimeStart: dateRange.value?.[0] ? dateRange.value[0] + ' 00:00:00' : undefined,
      applyTimeEnd: dateRange.value?.[1] ? dateRange.value[1] + ' 23:59:59' : undefined,
    };
    const r = await listPriceApprovals(params);
    rows.value = r?.records || [];
    total.value = r?.total || 0;
  } finally {
    loading.value = false;
  }
}
function onSearch() { page.pageNo = 1; load(); }
function onReset() {
  q.custName = ''; q.applyFullname = ''; q.apprStatus = '';
  dateRange.value = []; page.pageNo = 1; load();
}
function onPage(p) { page.pageNo = p; load(); }
function onSize(s) { page.pageSize = s; page.pageNo = 1; load(); }

onMounted(load);
</script>

<style lang="scss" scoped>
.price-approval { padding: 4px 2px; }
.page-h { margin-bottom: 12px;
  h1 { font-size: 18px; margin: 0; display: inline-block; }
  .desc { font-size: 12px; color: #909399; margin-left: 12px; }
}
.filter-form { margin-bottom: 8px; }
.main { color: #303133; }
.sub { color: #909399; font-size: 12px; }
.link-cell { cursor: pointer; display: block; }
.link-cell .main { color: #409eff; }
.link-cell:hover .main { text-decoration: underline; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
