<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head"><div><PageTitle /><span>四项规则全部通过后方可提交，公司层面审批通过自动生成触达任务。</span></div><el-button type="primary" @click="formVisible=!formVisible">{{ formVisible?'收起申请':'发起申请' }}</el-button></header>
    <el-card v-show="formVisible" shadow="never" class="apply-card">
      <el-form label-position="top">
        <el-form-item label="客户ID或客户号" required><div class="lookup"><el-input v-model="customerKey" placeholder="请输入客户ID或客户号" @keyup.enter="runValidate" /><el-button type="primary" :loading="validating" @click="runValidate">校验申请条件</el-button></div></el-form-item>
        <div v-if="validation" class="customer-summary"><div><small>客户名称</small><strong>{{ validation.custName }}</strong></div><div><small>客户号</small><strong>{{ validation.custNo||'-' }}</strong></div><div><small>主办人员</small><strong>{{ validation.mainManagerId||'-' }}</strong></div><div><small>主办机构</small><strong>{{ validation.mainOrgId||'-' }}</strong></div></div>
        <div v-if="validation" class="checks"><article v-for="item in validation.checks" :key="item.ruleCode" :class="item.passed?'pass':'fail'"><el-icon><CircleCheckFilled v-if="item.passed" /><CircleCloseFilled v-else /></el-icon><div><strong>{{ item.ruleName }}</strong><small>{{ item.message }} · {{ item.dataSource }}</small></div><span>{{ item.passed?'满足':'不满足' }}</span></article></div>
        <el-form-item label="申请原因与联合营销计划" required><el-input v-model="reason" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
        <div class="actions"><el-button type="primary" :disabled="!validation?.valid||!reason.trim()" :loading="submitting" @click="submit">提交审批</el-button></div>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <div class="toolbar"><el-radio-group v-model="status" @change="load"><el-radio-button value="">全部</el-radio-button><el-radio-button value="PENDING">待审批</el-radio-button><el-radio-button value="APPROVED">已通过</el-radio-button><el-radio-button value="REJECTED">已退回</el-radio-button></el-radio-group></div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="applyNo" label="申请编号" min-width="180" /><el-table-column prop="custName" label="客户名称" min-width="190" />
        <el-table-column label="申请人 / 机构" min-width="180"><template #default="{row}"><div class="stack"><strong>{{ row.applicantName||row.applicantEmpId }}</strong><small>{{ row.applicantOrgName||row.applicantOrgId }}</small></div></template></el-table-column>
        <el-table-column prop="applyReason" label="申请原因" min-width="220" show-overflow-tooltip /><el-table-column prop="createdTime" label="申请时间" min-width="165" />
        <el-table-column label="状态" width="95"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="185" fixed="right" class-name="operation-cell"><template #default="{row}"><template v-if="row.status==='PENDING'"><el-button link type="success" @click="approve(row)">通过</el-button><el-button link type="danger" @click="reject(row)">退回</el-button></template><el-button link type="primary" @click="selected=row;detailVisible=true">查看</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-drawer v-model="detailVisible" title="跨机构营销申请详情" size="560px"><el-descriptions v-if="selected" :column="1" border><el-descriptions-item label="客户">{{selected.custName}}</el-descriptions-item><el-descriptions-item label="申请人">{{selected.applicantName||selected.applicantEmpId}} · {{selected.applicantOrgName||selected.applicantOrgId}}</el-descriptions-item><el-descriptions-item label="申请原因">{{selected.applyReason}}</el-descriptions-item><el-descriptions-item label="触达任务">{{selected.generatedTouchTaskId||'-'}}</el-descriptions-item><el-descriptions-item label="退回原因">{{selected.rejectReason||'-'}}</el-descriptions-item></el-descriptions></el-drawer>
  </section>
</template>

<script setup>
import { ref } from 'vue';import { CircleCheckFilled,CircleCloseFilled } from '@element-plus/icons-vue';import { ElMessage,ElMessageBox } from 'element-plus';
import { approveCrossOrgMarketing,createCrossOrgMarketing,listCrossOrgMarketing,rejectCrossOrgMarketing,validateCrossOrgMarketing } from '@/api/customerMarketing';
const formVisible=ref(true),customerKey=ref(''),reason=ref(''),validation=ref(null),validating=ref(false),submitting=ref(false),loading=ref(false),rows=ref([]),status=ref(''),selected=ref(null),detailVisible=ref(false);
const statusLabel=v=>({PENDING:'待审批',APPROVED:'已通过',REJECTED:'已退回'}[v]||v),statusType=v=>({PENDING:'warning',APPROVED:'success',REJECTED:'danger'}[v]||'info');
async function runValidate(){if(!customerKey.value.trim())return ElMessage.warning('请输入客户ID或客户号');validating.value=true;try{validation.value=await validateCrossOrgMarketing(customerKey.value.trim());}finally{validating.value=false;}}
async function submit(){submitting.value=true;try{await createCrossOrgMarketing({custId:validation.value.custId,reason:reason.value});ElMessage.success('申请已提交，并已通知原主办及原机构');reason.value='';validation.value=null;customerKey.value='';await load();}finally{submitting.value=false;}}
async function load(){loading.value=true;try{rows.value=await listCrossOrgMarketing({status:status.value||undefined})||[];}finally{loading.value=false;}}
async function approve(row){try{const{value}=await ElMessageBox.prompt('请输入审批意见','跨机构营销审批',{inputType:'textarea',inputPlaceholder:'请输入审批意见（必填）',inputAttrs:{maxlength:500},inputValidator:v=>!!v?.trim()||'审批意见不能为空',inputErrorMessage:'审批意见不能为空'});const reason=typeof value==='string'?value.trim():'';if(!reason){ElMessage.warning('审批意见不能为空');return;}await approveCrossOrgMarketing(row.id,reason);ElMessage.success('审批通过，触达任务已生成');load();}catch(e){if(e!=='cancel'&&e!=='close'&&e?.message)throw e;}}
async function reject(row){try{const{value}=await ElMessageBox.prompt('请输入退回原因','退回申请',{inputValidator:v=>!!v?.trim()||'退回原因不能为空'});await rejectCrossOrgMarketing(row.id,value);ElMessage.success('申请已退回');load();}catch(e){if(e!=='cancel'&&e!=='close'&&e?.message)throw e;}}
load();
</script>

<style scoped lang="scss">.page-head{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:14px}.page-head h1{margin:0;font-size:18px}.page-head span{display:block;margin-top:4px;font-size:12px;color:#909399}.apply-card{margin-bottom:14px;border-top:3px solid #1f5b8f}.lookup{display:flex;width:100%;gap:10px}.customer-summary{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;padding:12px;margin-bottom:14px;background:#f7f9fc}.customer-summary div,.stack{display:flex;flex-direction:column}.customer-summary small,.stack small{font-size:11px;color:#909399}.checks{display:grid;grid-template-columns:repeat(2,1fr);gap:10px;margin-bottom:16px}.checks article{display:flex;gap:9px;align-items:center;padding:12px;border:1px solid #dcdfe6;border-radius:4px}.checks article>div{display:flex;flex:1;flex-direction:column}.checks small{color:#909399}.checks .pass{color:#27864b;background:#f4fbf6}.checks .fail{color:#b76a17;background:#fff8ef}.actions,.toolbar{display:flex;justify-content:flex-end;margin-bottom:12px}@media(max-width:760px){.customer-summary,.checks{grid-template-columns:1fr}}</style>
