// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { reactive } from "vue";

const api = vi.hoisted(() => ({ loadPersonalCockpit: vi.fn() }));
const http = vi.hoisted(() => ({ get: vi.fn() }));
const router = vi.hoisted(() => ({ push: vi.fn() }));
const route = vi.hoisted(() => ({ query: {} }));
const store = vi.hoisted(() => ({ user: null, setUser: vi.fn() }));
const menus = vi.hoisted(() => ({
  load: vi.fn().mockResolvedValue(),
  hasUrl: vi.fn().mockReturnValue(true),
}));
const permissions = vi.hoisted(() => ({
  load: vi.fn().mockResolvedValue(),
  hasUrl: vi.fn().mockReturnValue(true),
}));
const reactiveStore = reactive(store);
vi.mock("@/api/http", () => ({ API_BASE: "/api", default: http }));
vi.mock("@/api/personalCockpit", () => api);
vi.mock("@/stores/user", () => ({ useUserStore: () => reactiveStore }));
vi.mock("@/stores/menu", () => ({ useMenuStore: () => menus }));
vi.mock("@/stores/permission", () => ({
  usePermissionStore: () => permissions,
}));
vi.mock("vue-router", () => ({
  useRouter: () => router,
  useRoute: () => route,
}));
vi.mock("../PersonalDashboard.vue", () => ({
  default: {
    name: "PersonalDashboard",
    props: ["model", "loading", "backLabel"],
    emits: ["refresh", "range-change", "back", "fullscreen"],
    template:
      '<main><span>{{ model.identity?.name }}</span><span>{{ backLabel }}</span><button data-action="refresh" @click="$emit(\'refresh\')">刷新</button><button data-action="range" @click="$emit(\'range-change\', \'6m\')">6月</button><button data-action="back" @click="$emit(\'back\')">返回</button></main>',
  },
}));
import PersonalDashboardPage from "../PersonalDashboardPage.vue";

const identity = {
  empId: "E-1",
  displayName: "当前用户",
  mainOrgName: "当前机构",
  mainOrgCode: "ORG-1",
};
const model = {
  range: "6m",
  identity: { empId: "E-1", name: "当前用户", orgName: "当前机构" },
  settings: {},
  metrics: { items: [] },
  refreshedAt: null,
};

beforeEach(() => {
  http.get.mockReset().mockResolvedValue(identity);
  api.loadPersonalCockpit.mockReset().mockResolvedValue(model);
  router.push.mockReset();
  route.query = {};
  store.user = null;
  store.setUser.mockReset().mockImplementation((value) => {
    store.user = value;
  });
  menus.load.mockClear();
  permissions.load.mockClear();
  menus.hasUrl.mockReturnValue(true);
  permissions.hasUrl.mockReturnValue(true);
});

describe("PersonalDashboardPage", () => {
  it('初始化保存完整身份角色，不能用显示模型覆盖授权身份', async () => {
    const current = { ...identity, roles: [{ roleCode: 'QA_ROLE' }], username: 'qa' };
    http.get.mockResolvedValueOnce(current);
    const wrapper = mount(PersonalDashboardPage);
    await flushPromises();
    expect(store.setUser).toHaveBeenCalledWith(current);
    wrapper.unmount();
  });
  it("首次请求不传默认 range，直接采用 API 返回的 model.range 且只请求一次", async () => {
    const wrapper = mount(PersonalDashboardPage);
    await flushPromises();
    expect(api.loadPersonalCockpit).toHaveBeenCalledTimes(1);
    expect(api.loadPersonalCockpit.mock.calls[0][0]).toMatchObject({
      identity,
      screenCode: "PERSONAL_COCKPIT",
    });
    expect(api.loadPersonalCockpit.mock.calls[0][0]).not.toHaveProperty(
      "range",
    );
    expect(wrapper.vm.range).toBe("6m");
    wrapper.unmount();
  });

  it("缺少员工或主机构身份时失败关闭，不写入驾驶舱", async () => {
    http.get.mockResolvedValueOnce({ empId: "E-1", displayName: "无机构" });
    const wrapper = mount(PersonalDashboardPage);
    await flushPromises();
    expect(api.loadPersonalCockpit).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("有效员工及主机构身份");
    wrapper.unmount();
  });

  it("草稿显示未发布提示并返回设计器，preview切换重新加载", async () => {
    const wrapper = mount(PersonalDashboardPage, {
      props: { preview: "draft" },
    });
    await flushPromises();
    expect(wrapper.text()).toContain("草稿预览");
    await wrapper.get('[data-action="back"]').trigger("click");
    expect(router.push).toHaveBeenCalledWith(
      "/screen-admin/designer?tab=personal",
    );
    await wrapper.setProps({ preview: "" });
    await flushPromises();
    expect(api.loadPersonalCockpit).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it("范围切换只请求一次并保留受控范围，返回入口可用", async () => {
    api.loadPersonalCockpit.mockResolvedValue({ ...model, range: "7d" });
    const wrapper = mount(PersonalDashboardPage);
    await flushPromises();
    await wrapper.get('[data-action="range"]').trigger("click");
    await flushPromises();
    expect(api.loadPersonalCockpit).toHaveBeenCalledTimes(2);
    expect(api.loadPersonalCockpit.mock.calls[1][0]).toMatchObject({
      range: "6m",
    });
    await wrapper.get('[data-action="back"]').trigger("click");
    expect(router.push).toHaveBeenCalledWith("/workspace");
    wrapper.unmount();
  });

  it("换账号会先清空并废弃旧响应，且刷新菜单权限后再加载", async () => {
    let resolveOld;
    api.loadPersonalCockpit.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveOld = resolve;
      }),
    );
    const wrapper = mount(PersonalDashboardPage);
    await flushPromises();
    store.user = { empId: "E-2" };
    await flushPromises();
    await wrapper.vm.refresh();
    await flushPromises();
    expect(wrapper.vm.model.identity.empId).toBe("E-1");
    expect(menus.load).toHaveBeenCalledWith(true);
    expect(permissions.load).toHaveBeenCalledWith(true);
    resolveOld({ ...model, identity: { empId: "E-1", name: "旧用户" } });
    await flushPromises();
    expect(wrapper.vm.model.identity.name).toBe("当前用户");
    wrapper.unmount();
  });
});
