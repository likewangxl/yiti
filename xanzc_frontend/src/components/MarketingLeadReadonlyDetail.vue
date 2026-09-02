<template>
  <div class="marketing-lead-readonly-detail">
    <section class="detail-section">
      <h3><span>01</span>基础信息</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="线索类型">{{ leadTypeLabel(lead.leadType) }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ lead.custName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="统一社会信用代码">{{ lead.unifiedCreditCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="是否开户">{{ yesNo(lead.isAccountOpenedSnapshot) }}</el-descriptions-item>
        <el-descriptions-item label="客户号" :span="2">{{ lead.custNo ?? lead.custNoSnapshot ?? '-' }}</el-descriptions-item>
      </el-descriptions>
    </section>

    <section class="detail-section">
      <h3><span>02</span>经营属性</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="所属行业">{{ industryLabelOf(lead.industry) }}</el-descriptions-item>
        <el-descriptions-item label="所属集团类型">{{ groupTypeLabelOf(lead.groupType) }}</el-descriptions-item>
        <el-descriptions-item label="所属集团名称">{{ lead.groupName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户类型">{{ customerTypeLabelOf(lead.customerType) }}</el-descriptions-item>
        <el-descriptions-item label="是否基石客户">{{ yesNo(lead.isKeystone) }}</el-descriptions-item>
        <el-descriptions-item label="企业类型">{{ enterpriseTypeLabelOf(lead.enterpriseType) }}</el-descriptions-item>
        <el-descriptions-item label="客户标签" :span="2">{{ tagLabel }}</el-descriptions-item>
      </el-descriptions>
    </section>

    <section class="detail-section">
      <h3><span>03</span>分配信息</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="分配方式">{{ distributionLabel(lead.distributionMode) }}</el-descriptions-item>
        <el-descriptions-item label="客户经理范围">{{ managerScopeLabel }}</el-descriptions-item>
        <el-descriptions-item label="主办客户经理" :span="2">{{ currentOwnerLabel }}</el-descriptions-item>
      </el-descriptions>
    </section>

    <section class="detail-section">
      <h3><span>04</span>补充资料</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="是否触达限制">{{ yesNo(lead.touchRestricted) }}</el-descriptions-item>
        <el-descriptions-item label="客户说明">{{ lead.customerDesc || '-' }}</el-descriptions-item>
        <el-descriptions-item label="授信金额（万元）">{{ money(lead.creditAmount) }}</el-descriptions-item>
        <el-descriptions-item label="授信敞口金额（万元）">{{ money(lead.creditExposureAmount) }}</el-descriptions-item>
        <el-descriptions-item label="附件" :span="2">
          <div v-if="attachments.length" class="detail-tags">
            <el-tag v-for="file in attachments" :key="file.id || file.fileId" effect="plain">{{ file.fileName || file.name || file.id || file.fileId }}</el-tag>
          </div>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useDict } from '@/composables/useDict';

const props = defineProps({ detail: { type: Object, default: null } });
const lead = computed(() => props.detail?.lead || {});
const tagIds = computed(() => props.detail?.tagIds || lead.value?.tagIds || []);
const tagItems = computed(() => props.detail?.tags || lead.value?.tags || []);
const managerEmpIds = computed(() => props.detail?.managerEmpIds || lead.value?.managerEmpIds || []);
const managerScopes = computed(() => props.detail?.managerScopes || lead.value?.managerScopes || []);
const attachments = computed(() => props.detail?.attachments || lead.value?.attachments || []);

const { labelOf: industryLabelOf } = useDict('INDUSTRY');
const { labelOf: groupTypeLabelOf } = useDict('GROUP_TYPE');
const { labelOf: customerTypeLabelOf } = useDict('CUSTOMER_TYPE');
const { labelOf: enterpriseTypeLabelOf } = useDict('ENTERPRISE_TYPE');

const tagLabel = computed(() => {
  const values = tagItems.value.length
    ? tagItems.value.map(item => item.tagName || item.name || item.tagId || item.id)
    : tagIds.value;
  return values.filter(Boolean).join('、') || '-';
});
const currentOwnerLabel = computed(() => {
  const customer = props.detail?.currentCustomer;
  if (!customer?.mainManagerId) return '-';
  return employeeLabel(customer.mainManagerName, customer.mainManagerId);
});
const managerScopeLabel = computed(() => {
  if (lead.value.distributionMode === 'PUBLIC') return '全行客户经理';
  if (lead.value.distributionMode === 'OWNER') return currentOwnerLabel.value;
  const values = managerScopes.value.length
    ? managerScopes.value.map(item => employeeLabel(item.managerName, item.managerEmpId))
    : managerEmpIds.value;
  return values.filter(Boolean).join('、') || '-';
});

function employeeLabel(name, id) {
  if (name && id) return `${name}（${id}）`;
  return name || id || '-';
}
function leadTypeLabel(value) { return ({ NEW_ACCOUNT: '新客户开户线索', EXISTING_MARKETING: '存量客户营销线索' }[value] || value || '-'); }
function distributionLabel(value) { return ({ PUBLIC: '全行公开认领', SCOPE: '指定客户经理范围', OWNER: '主办专属' }[value] || value || '-'); }
function yesNo(value) { return value === 1 || value === true ? '是' : value === 0 || value === false ? '否' : '-'; }
function money(value) { return value == null || value === '' ? '-' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 }); }
</script>

<style scoped lang="scss">
.marketing-lead-readonly-detail { display: grid; gap: 18px; }
.detail-section h3 { display: flex; align-items: center; gap: 8px; margin: 0 0 10px; color: #303133; font-size: 14px; }
.detail-section h3 span { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; border-radius: 4px; color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-size: 11px; }
.detail-tags { display: flex; flex-wrap: wrap; gap: 6px; }
</style>
