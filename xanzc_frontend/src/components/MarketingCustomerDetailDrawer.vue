<template>
  <el-drawer
    v-model="visible"
    class="bp-crud-dialog marketing-customer-detail-drawer"
    title="营销客户详情"
    size="min(820px, 94vw)"
    destroy-on-close
  >
    <el-skeleton v-if="loading" :rows="11" animated />
    <template v-else-if="customer">
      <el-descriptions title="企业信息" :column="2" border>
        <el-descriptions-item label="企业名称">{{ customer.custName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户号">{{ customer.custNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="统一社会信用代码" :span="2">{{ customer.unifiedCreditCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="法定代表人">{{ customer.legalRepresentative || '-' }}</el-descriptions-item>
        <el-descriptions-item label="企业类型">{{ customer.enterpriseType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="注册资本">{{ money(customer.registeredCapital) }}</el-descriptions-item>
        <el-descriptions-item label="所属行业">{{ industryLabelOf(customer.industry) }}</el-descriptions-item>
        <el-descriptions-item label="注册地址" :span="2">{{ customer.registeredAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="经营地址" :span="2">{{ customer.businessAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="经营范围" :span="2">{{ customer.businessScope || '-' }}</el-descriptions-item>
        <el-descriptions-item label="企业联系人">{{ customer.contactPerson || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ customer.contactMobile || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions title="营销与授信" :column="2" border class="detail-block">
        <el-descriptions-item label="客户类型">{{ customer.customerType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="集团类型">{{ customer.groupType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="集团名称">{{ customer.groupName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="基石客户">{{ yesNo(customer.isKeystone) }}</el-descriptions-item>
        <el-descriptions-item label="开户状态">{{ yesNo(customer.isAccountOpened) }}</el-descriptions-item>
        <el-descriptions-item label="授信金额">{{ money(customer.creditAmount) }}</el-descriptions-item>
        <el-descriptions-item label="授信敞口">{{ money(customer.creditExposureAmount) }}</el-descriptions-item>
        <el-descriptions-item label="是否触达限制">{{ yesNo(customer.touchRestricted) }}</el-descriptions-item>
        <el-descriptions-item label="客户说明" :span="2">{{ customer.customerDesc || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions title="主办与开户" :column="2" border class="detail-block">
        <el-descriptions-item label="主办客户经理" :span="2">
          {{ customer.mainManagerName || customer.mainManagerId || '无' }}
          <template v-if="customer.mainManagerId">（{{ customer.mainManagerId }}）</template>
        </el-descriptions-item>
        <el-descriptions-item label="主办机构">{{ customer.mainOrgName || customer.mainOrgId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主办维护方式">{{ ownershipModeLabel(customer.ownershipMaintainMode) }}</el-descriptions-item>
        <el-descriptions-item label="主办来源">{{ ownershipSourceLabel(customer.ownershipSource) }}</el-descriptions-item>
        <el-descriptions-item label="主办数据日期">{{ customer.ownershipDataDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开户办理人">{{ customer.accountOpenedByEmpId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开户时间">{{ formatTime(customer.accountOpenedTime) }}</el-descriptions-item>
        <el-descriptions-item label="最近触达">{{ formatTime(customer.lastTouchTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(customer.updatedTime) }}</el-descriptions-item>
      </el-descriptions>

      <div class="drawer-actions">
        <el-button type="primary" @click="emit('edit', customer)">编辑客户资料</el-button>
      </div>
    </template>
    <el-empty v-else description="暂无客户详情" :image-size="64" />
  </el-drawer>
</template>

<script setup>
import { computed, watch } from 'vue';
import { useDict } from '@/composables/useDict';

const emit = defineEmits(['update:modelValue', 'edit']);
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  customer: { type: Object, default: null },
  loading: { type: Boolean, default: false },
});
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
});
const { labelOf: industryLabelOf, reload: reloadIndustry } = useDict('INDUSTRY');

watch(() => props.modelValue, value => {
  if (value) reloadIndustry();
});

const yesNo = value => value === 1 || value === true ? '是' : value === 0 || value === false ? '否' : '-';
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-';
const money = value => value == null || value === '' ? '-' : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
const ownershipModeLabel = value => ({ AUTO: '自动同步', MANUAL: '人工维护' }[value] || value || '-');
const ownershipSourceLabel = value => ({ AUTO_SYNC: '每日关系快照', MANUAL: '人工维护', OPEN_ACCOUNT: '开户转移' }[value] || value || '-');
</script>

<style scoped lang="scss">
.detail-block { margin-top: 20px; }
.drawer-actions { display: flex; justify-content: flex-end; margin-top: 22px; }
</style>
