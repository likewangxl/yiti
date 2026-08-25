<template>
  <el-drawer v-model="visible" title="客户线索详情" size="min(820px, 94vw)" destroy-on-close>
    <el-skeleton v-if="loading" :rows="10" animated />
    <template v-else-if="lead">
      <el-alert
        v-if="lead.leadStatus === 'REJECTED'"
        :title="`驳回原因：${lead.rejectReason || '未填写'}`"
        type="error"
        :closable="false"
        show-icon
        class="detail-alert"
      />
      <el-descriptions title="基础信息" :column="2" border>
        <el-descriptions-item label="线索编号">{{ lead.leadNo || lead.id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(lead.leadStatus)">{{ statusLabel(lead.leadStatus) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="线索类型">{{ leadTypeLabel(lead.leadType) }}</el-descriptions-item>
        <el-descriptions-item label="版本">V{{ lead.versionNo || 1 }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ lead.custName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户号">{{ lead.custNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="统一社会信用代码" :span="2">{{ lead.unifiedCreditCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ lead.contactPerson || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ lead.contactMobile || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions title="经营与授信信息" :column="2" border class="detail-block">
        <el-descriptions-item label="所属行业">{{ industryLabelOf(lead.industry) }}</el-descriptions-item>
        <el-descriptions-item label="客户类型">{{ customerTypeLabelOf(lead.customerType) }}</el-descriptions-item>
        <el-descriptions-item label="集团类型">{{ groupTypeLabelOf(lead.groupType) }}</el-descriptions-item>
        <el-descriptions-item label="所属集团">{{ lead.groupName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="企业类型">{{ enterpriseTypeLabelOf(lead.enterpriseType) }}</el-descriptions-item>
        <el-descriptions-item label="基石客户">{{ yesNo(lead.isKeystone) }}</el-descriptions-item>
        <el-descriptions-item label="是否开户">{{ yesNo(lead.isAccountOpened) }}</el-descriptions-item>
        <el-descriptions-item label="线索来源">{{ leadSourceLabelOf(lead.leadSource) }}</el-descriptions-item>
        <el-descriptions-item label="授信金额">{{ money(lead.creditAmount) }}</el-descriptions-item>
        <el-descriptions-item label="授信敞口">{{ money(lead.creditExposureAmount) }}</el-descriptions-item>
        <el-descriptions-item label="客户说明" :span="2">{{ lead.customerDesc || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions title="分配与审批信息" :column="2" border class="detail-block">
        <el-descriptions-item label="分配方式">{{ distributionLabel(lead.distributionMode) }}</el-descriptions-item>
        <el-descriptions-item label="归属机构">{{ lead.ownerOrgId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主办客户经理" :span="2">
          {{ lead.mainManagerName || '-' }}<template v-if="lead.mainManagerId">（{{ lead.mainManagerId }}）</template>
          <template v-if="lead.mainManagerOrgName"> · {{ lead.mainManagerOrgName }}</template>
        </el-descriptions-item>
        <el-descriptions-item label="指定客户经理" :span="2">
          <div v-if="lead.managerScopes?.length" class="tag-list">
            <el-tag v-for="manager in lead.managerScopes" :key="manager.managerEmpId" effect="plain">
              {{ manager.managerName || manager.managerEmpId }}（{{ manager.managerEmpId }}） · {{ manager.managerOrgName || manager.managerOrgId || '-' }}
            </el-tag>
          </div>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="提交人">{{ lead.createdByName || lead.submittedBy || lead.createdBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatTime(lead.submittedTime) }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ lead.reviewedByName || lead.reviewedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ formatTime(lead.reviewedTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ lead.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <section class="detail-section">
        <h3>客户标签</h3>
        <div v-if="lead.tags?.length" class="tag-list">
          <el-tag v-for="tag in lead.tags" :key="tag.tagId" type="success" effect="plain">{{ tag.tagName || tag.tagId }}</el-tag>
        </div>
        <el-empty v-else description="暂无标签" :image-size="52" />
      </section>
      <section class="detail-section">
        <h3>附件</h3>
        <LeadAttachmentPreview v-if="lead.attachments?.length" :attachments="lead.attachments" />
        <el-empty v-else description="暂无附件" :image-size="52" />
      </section>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue';
import LeadAttachmentPreview from '@/components/LeadAttachmentPreview.vue';
import { useDict } from '@/composables/useDict';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  lead: { type: Object, default: null },
  loading: { type: Boolean, default: false }
});
const emit = defineEmits(['update:modelValue']);
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) });

const { labelOf: industryLabelOf } = useDict('INDUSTRY');
const { labelOf: groupTypeLabelOf } = useDict('GROUP_TYPE');
const { labelOf: customerTypeLabelOf } = useDict('CUSTOMER_TYPE');
const { labelOf: enterpriseTypeLabelOf } = useDict('ENTERPRISE_TYPE');
const { labelOf: leadSourceLabelOf } = useDict('LEAD_SOURCE');

const statusLabel = value => ({ DRAFT:'草稿', SUBMITTED:'已提交', IN_APPROVAL:'审批中', APPROVED:'已通过', REJECTED:'已驳回' }[value] || value || '-');
const statusType = value => ({ DRAFT:'info', SUBMITTED:'warning', IN_APPROVAL:'warning', APPROVED:'success', REJECTED:'danger' }[value] || 'info');
const leadTypeLabel = value => ({ NEW_ACCOUNT:'新客户开户', EXISTING_MARKETING:'存量客户营销' }[value] || value || '-');
const distributionLabel = value => ({ PUBLIC:'全行公开认领', SCOPE:'指定客户经理范围', OWNER:'主办专属' }[value] || value || '-');
const yesNo = value => value === 1 || value === true ? '是' : value === 0 || value === false ? '否' : '-';
const money = value => value == null || value === '' ? '-' : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-';
</script>

<style scoped lang="scss">
.detail-alert{margin-bottom:16px}.detail-block{margin-top:20px}.tag-list{display:flex;flex-wrap:wrap;gap:8px}.detail-section{margin-top:22px}.detail-section h3{margin:0 0 10px;font-size:16px}
</style>
