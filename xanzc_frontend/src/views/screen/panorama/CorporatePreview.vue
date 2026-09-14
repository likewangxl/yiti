<template>
  <main class="corporate-preview" aria-label="对公经营总览本地演示">
    <div class="corporate-preview__source" data-testid="corporate-preview-source">
      <span>本地演示 · 非业务数据</span>
      <button type="button" data-action="corporate-configure-real-data" @click="goToDesigner">配置真实数据</button>
    </div>

    <CorporateDashboard
      :model="corporateDemoModel"
      :loading="false"
      error=""
      :demo="true"
      @back="goBack"
      @refresh="refreshDemo"
    />

    <footer class="corporate-preview__footer">
      <span data-testid="corporate-demo-updated-at">本地演示本次查询/刷新时间 {{ lastUpdated }}</span>
      <span>示例截止 {{ corporateDemoModel.dataDate }} · 机构资料为演示样例</span>
      <span>刷新仅更新本地演示时间，不产生服务端结果</span>
    </footer>
  </main>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import CorporateDashboard from './CorporateDashboard.vue';
import { corporateDemoModel } from './corporateDemoModel.js';

const router = useRouter();
const lastUpdated = ref(formatDemoTime(new Date()));

function formatDemoTime(value) {
  const date = value instanceof Date ? value : new Date(value);
  const pad = number => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function refreshDemo() {
  lastUpdated.value = formatDemoTime(new Date());
}

function goBack() {
  router.push('/screen-preview');
}

function goToDesigner() {
  router.push('/screen-admin/designer');
}
</script>
