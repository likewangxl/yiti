<!--
  定价审批详情 —— 历史数据查询（只读）
  后端：GET /api/reports/amas-price-approvals/{priceApprId}
-->
<template>
  <div class="price-detail" v-loading="loading">
    <div class="page-h">
      <h1>定价审批详情</h1>
      <el-tag :type="STATUS_TAG[d.apprStatus] || 'info'" size="small" class="st-tag">
        {{ APPR_STATUS[d.apprStatus] || d.apprStatus || '-' }}
      </el-tag>
      <el-button link class="back-btn" @click="$router.back()">← 返回</el-button>
    </div>

    <el-descriptions :column="2" border size="default" class="desc-block">
      <el-descriptions-item label="价格审批编号">{{ d.priceApprId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ d.applyTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="申请人姓名">{{ d.applyFullname || '-' }}</el-descriptions-item>
      <el-descriptions-item label="申请人工号">{{ d.applyUsername || '-' }}</el-descriptions-item>
      <el-descriptions-item label="申请人部门">{{ d.applyDeptno || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审批进度">{{ d.apprProgress || '-' }}</el-descriptions-item>

      <el-descriptions-item label="客户名称">{{ d.custName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="客户编号">{{ d.custId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="客户部门">{{ d.custDept || '-' }}</el-descriptions-item>
      <el-descriptions-item label="对公/零售">{{ OR_RETAIL[d.businOrRetail] || d.businOrRetail || '-' }}</el-descriptions-item>
      <el-descriptions-item label="业务大类">{{ d.businCate || '-' }}</el-descriptions-item>
      <el-descriptions-item label="业务类型">{{ d.businType || '-' }}</el-descriptions-item>
      <el-descriptions-item label="业务测算编号">{{ d.businCalcuNo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="币种">{{ d.currency || '-' }}</el-descriptions-item>

      <el-descriptions-item label="金额">{{ d.money || '-' }}</el-descriptions-item>
      <el-descriptions-item label="年限">{{ d.years || '-' }}</el-descriptions-item>
      <el-descriptions-item label="执行利率">{{ d.executeRate || '-' }}</el-descriptions-item>
      <el-descriptions-item label="基准利率">{{ d.localRate || '-' }}</el-descriptions-item>
      <el-descriptions-item label="浮动比例">{{ d.slidScale || '-' }}</el-descriptions-item>
      <el-descriptions-item label="承诺金额">{{ d.promiseAmount || '-' }}</el-descriptions-item>
      <el-descriptions-item label="承诺时间">{{ d.promiseTime || '-' }}</el-descriptions-item>

      <el-descriptions-item label="客户信息" :span="2">{{ d.custInfo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="必要性说明" :span="2">{{ d.necessExplain || '-' }}</el-descriptions-item>
      <el-descriptions-item label="附件" :span="2">{{ d.files || '-' }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { getPriceApprovalDetail } from '@/api/report';

const route = useRoute();
const APPR_STATUS = { '0': '待审批', '1': '已通过', '2': '未通过' };
const STATUS_TAG = { '0': 'warning', '1': 'success', '2': 'danger' };
const OR_RETAIL = { '1': '对公', '2': '零售', CORP: '对公', RETAIL: '零售' };

const d = ref({});
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    d.value = (await getPriceApprovalDetail(route.params.priceApprId)) || {};
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>

<style lang="scss" scoped>
.price-detail { padding: 4px 2px; }
.page-h { margin-bottom: 12px; display: flex; align-items: center; gap: 12px;
  h1 { font-size: 18px; margin: 0; }
}
.st-tag { margin-left: 4px; }
.back-btn { margin-left: auto; }
.desc-block { margin-top: 8px; }
</style>
