<template>
  <main class="screen-preview" aria-label="经营全景本地演示" style="--panorama-viewport-offset: 78px">
    <div class="screen-preview__source screen-preview__source--top" data-testid="preview-source-top">
      本地演示 · 非业务数据
    </div>

    <PanoramaDashboard
      :model="demoModel"
      :loading="false"
      error=""
      :demo="true"
      @back="goBack"
      @refresh="refreshDemo"
    />

    <footer class="screen-preview__footer">
      <span data-testid="demo-updated-at">本地演示更新时间 {{ lastUpdated }}</span>
      <span>坐标为 GCJ-02 近似示意，仅用于开发态视觉验收</span>
      <span data-testid="demo-refresh-note">刷新仅更新本地演示时间，不产生服务端结果</span>
    </footer>
  </main>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import PanoramaDashboard from './PanoramaDashboard.vue';
import { demoModel } from './demoModel.js';

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
  // 工作区仍由全局路由守卫决定是否需要登录，演示页不自行探测会话或请求后端。
  router.push('/workspace');
}
</script>

<style scoped>
.screen-preview {
  --panorama-viewport-offset: 78px;
  min-height: 100vh;
  box-sizing: border-box;
  color: #eaf2ff;
  background: #07102c;
  display: flex;
  flex-direction: column;
}

.screen-preview__source {
  color: #f7ca72;
  font-size: 12px;
  letter-spacing: .08em;
}

.screen-preview__source--top {
  padding: 8px 20px;
  text-align: center;
  background: rgba(246, 184, 73, .12);
  border-bottom: 1px solid rgba(246, 184, 73, .35);
}

.screen-preview :deep(.panorama-dashboard) {
  flex: 1;
  min-height: 0;
}

/* Dashboard 仍收到 demo=true 以保留组件语义，但外壳只保留顶部唯一演示标识。 */
.screen-preview :deep(.panorama-demo-badge) {
  display: none;
}

.screen-preview__footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;
  padding: 8px 20px 12px;
  color: #7992bd;
  border-top: 1px solid rgba(100, 151, 227, .22);
  font-size: 11px;
  line-height: 1.5;
}
</style>
