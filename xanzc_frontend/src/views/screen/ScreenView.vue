<template>
  <div class="screen-root">
    <div class="scr-stage" :style="{ transform: `translate(-50%, -50%) scale(${scale})` }">
      <div class="scr-header">
        <span class="scr-back" @click="goBack">‹ 返回</span>
        <span class="scr-title">{{ view?.screen?.screenName || '经营管理大屏' }}</span>
        <span class="scr-clock"><span class="scr-live-dot" /> {{ clock }}</span>
      </div>
      <div class="scr-body" v-if="view">
        <ScreenRenderer :screen="view.screen" :blocks="view.blocks"
                        :map-points="view.mapPoints" :context="context" />
      </div>
      <div v-else-if="loadError" class="scr-block-err" style="margin:auto">
        大屏配置加载失败：{{ loadError }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getScreenView } from '@/api/screen';
import ScreenRenderer from './components/ScreenRenderer.vue';

const route = useRoute();
const router = useRouter();

const view = ref(null);
const loadError = ref('');
const scale = ref(1);
const clock = ref('');

// 路由参数即取数上下文：同一份屏配置服务所有支行/员工
const context = computed(() => ({
  orgCode: route.query.orgCode || '',
  empId: route.query.empId || ''
}));

async function load() {
  loadError.value = '';
  view.value = null;
  try {
    view.value = await getScreenView(route.params.screenCode);
  } catch (e) {
    loadError.value = e?.message || '未知错误';
  }
}

function fit() {
  // 1920×1080 设计稿等比缩放，电视墙/投屏一致
  scale.value = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
}

let clockTimer = null;
function tick() {
  const d = new Date();
  const p = n => String(n).padStart(2, '0');
  clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function goBack() {
  if (window.history.length > 1) router.back();
  else router.push('/workspace');
}

watch(() => [route.params.screenCode, route.query.orgCode, route.query.empId], load);

onMounted(() => {
  fit();
  window.addEventListener('resize', fit);
  tick();
  clockTimer = setInterval(tick, 1000);
  load();
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', fit);
  clearInterval(clockTimer);
});
</script>

<style lang="scss">
@use '@/styles/screen.scss';
</style>
