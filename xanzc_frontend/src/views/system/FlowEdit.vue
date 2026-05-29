<template>
  <div>
    <div class="page-h">
      <h1>审批流程编辑 <span class="sub">建设中</span></h1>
      <div class="actions">
        <el-button @click="router.push('/system/workflow-flows')">← 返回列表</el-button>
      </div>
    </div>
    <div class="card-section">
      <p style="color: #909399; font-size: 14px;">审批流程编辑（建设中）&nbsp;&nbsp;流程ID：{{ id }}</p>
    </div>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();
/** 当前编辑的流程 ID，来自路由参数 :id */
const id = route.params.id;
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
</style>
