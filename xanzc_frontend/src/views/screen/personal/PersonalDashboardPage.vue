<template>
  <section class="personal-dashboard-page">
    <div v-if="preview === 'draft'" class="personal-draft-banner" role="status">
      草稿预览 · 尚未发布
    </div>
    <div v-if="error" class="personal-page-error" role="alert">{{ error }}</div>
    <PersonalDashboard
      :model="model"
      :loading="loading"
      :back-label="
        preview === 'draft'
          ? '返回设计器'
          : fromScreenCenter
            ? '返回大屏中心'
            : '返回工作台'
      "
      @refresh="refresh"
      @range-change="changeRange"
      @back="goBack"
      @fullscreen="enterFullscreen"
    />
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import http, { API_BASE } from "@/api/http";
import { loadPersonalCockpit } from "@/api/personalCockpit";
import { useUserStore } from "@/stores/user";
import { useMenuStore } from "@/stores/menu";
import { usePermissionStore } from "@/stores/permission";
import PersonalDashboard from "./PersonalDashboard.vue";

const props = defineProps({ preview: { type: String, default: "" } });
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const menuStore = useMenuStore();
const permissionStore = usePermissionStore();
const model = ref({
  identity: {},
  settings: {},
  metrics: { status: "empty", items: [] },
  refreshedAt: null,
});
const loading = ref(false);
const error = ref("");
const range = ref(undefined);
const generation = ref(0);
let expectedStoreEmpId = "";
const fromScreenCenter = computed(() => route.query?.from === "screen-center");
const preview = computed(() =>
  props.preview === "draft" || route.query?.preview === "draft" ? "draft" : "",
);

function identityOf(value) {
  return {
    empId: String(value?.empId || value?.employeeId || "").trim(),
    displayName: value?.displayName || value?.name || value?.userName || "",
    mainOrgName:
      value?.mainOrgName || value?.orgName || value?.organizationName || "",
    mainOrgCode:
      value?.mainOrgCode || value?.orgCode || value?.mainOrganizationCode || "",
  };
}

function clear() {
  model.value = {
    identity: {},
    settings: {},
    metrics: { status: "empty", items: [] },
    refreshedAt: null,
  };
  error.value = "";
}

function isValidIdentity(identity) {
  return Boolean(identity.empId && identity.mainOrgCode);
}
function isForbidden(cause) {
  return (
    Number(cause?.response?.status || cause?.status) === 403 ||
    String(cause?.code || "")
      .toUpperCase()
      .includes("FORBIDDEN")
  );
}
function hasRequiredAccess() {
  return route.params?.screenCode === 'PERSONAL_COCKPIT'
    ? permissionStore.canAccess('/api/screen/view/*')
    : menuStore.hasUrl('/workspace');
}

async function load() {
  const token = ++generation.value;
  loading.value = true;
  error.value = "";
  try {
    const confirmedUser = await http.get(`${API_BASE}/auth/current-user`, { silent: true });
    const current = identityOf(confirmedUser);
    if (!isValidIdentity(current))
      throw new Error("当前会话未返回有效员工及主机构身份");
    if (token !== generation.value) return;
    const cachedEmpId = String(userStore.user?.empId || "").trim();
    const switched = Boolean(cachedEmpId && cachedEmpId !== current.empId);
    if (switched) { clear(); range.value = undefined; }
    if (!cachedEmpId || switched) {
      expectedStoreEmpId = current.empId;
      userStore.setUser(confirmedUser);
      if (switched) {
        await Promise.all([menuStore.load(true), permissionStore.load(true)]);
        if (token !== generation.value || !hasRequiredAccess())
          throw new Error("当前账号未授权工作台");
      }
    }
    if (token !== generation.value) return;
    const request = {
      identity: current,
      preview: preview.value,
      screenCode: "PERSONAL_COCKPIT",
    };
    if (range.value) request.range = range.value;
    const next = await loadPersonalCockpit(request);
    if (token !== generation.value) return;
    model.value = next;
    if (["7d", "6m", "1y"].includes(next?.range)) range.value = next.range;
  } catch (cause) {
    if (token !== generation.value) return;
    clear();
    error.value = isForbidden(cause)
      ? "当前账号无权查看个人驾驶舱"
      : cause?.message || "当前会话未确认，无法加载个人驾驶舱";
    model.value.metrics = {
      status: isForbidden(cause) ? "forbidden" : "error",
      message: error.value,
      items: [],
    };
  } finally {
    if (token === generation.value) loading.value = false;
  }
}

async function refresh() {
  await load();
}
async function changeRange(next) {
  if (!["7d", "6m", "1y"].includes(next) || next === range.value) return;
  range.value = next;
  await load();
}
function goBack() {
  if (route.query?.from === 'login' && preview.value !== 'draft') {
    router.replace('/workspace');
    return;
  }
  router.push(
    preview.value === "draft"
      ? "/screen-admin/designer?tab=personal"
      : fromScreenCenter.value
        ? "/screens"
        : "/workspace",
  );
}
async function enterFullscreen() {
  if (document.fullscreenElement) {
    if (document.exitFullscreen) await document.exitFullscreen();
    return;
  }
  if (!document.documentElement?.requestFullscreen) return;
  try {
    await document.documentElement.requestFullscreen();
  } catch {
    /* keep page usable */
  }
}

watch(
  () => userStore.user?.empId,
  (next, previous) => {
    if (!next) {
      generation.value += 1;
      loading.value = false;
      clear();
      return;
    }
    if (expectedStoreEmpId && String(next) === expectedStoreEmpId) {
      expectedStoreEmpId = "";
      return;
    }
    if (next !== previous) {
      clear();
      range.value = undefined;
      load();
    }
  },
);
watch(preview, () => {
  clear();
  load();
});
onMounted(load);
onBeforeUnmount(() => {
  generation.value += 1;
});
defineExpose({ model, loading, range, refresh });
</script>

<style scoped>
.personal-dashboard-page {
  min-height: 100vh;
  background: #030b1c;
}
.personal-draft-banner {
  padding: 8px 24px;
  color: #241900;
  background: #ffcb6b;
  font-weight: 700;
  text-align: center;
}
.personal-page-error {
  padding: 10px 24px;
  color: #ffb7b7;
  background: #431c2a;
}
</style>
