// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const testState = vi.hoisted(() => ({
  hooks: { beforeEach: null, afterEach: null, onError: null },
  messageError: vi.fn()
}));

vi.mock('vue-router', () => ({
  createWebHashHistory: () => ({}),
  createRouter: () => ({
    beforeEach(handler) { testState.hooks.beforeEach = handler; },
    afterEach(handler) { testState.hooks.afterEach = handler; },
    onError(handler) { testState.hooks.onError = handler; },
    getRoutes: () => []
  }),
  useRoute: () => ({ meta: {}, fullPath: '/workspace' }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
}));
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ isLoggedIn: true })
}));
vi.mock('@/api/http', () => ({
  default: { get: vi.fn() }
}));
vi.mock('element-plus', () => ({
  ElMessage: { error: testState.messageError }
}));

let consoleError;

async function loadRouter() {
  vi.resetModules();
  testState.hooks.beforeEach = null;
  testState.hooks.afterEach = null;
  testState.hooks.onError = null;
  await import('../index.js');
}

async function startNavigation() {
  expect(await testState.hooks.beforeEach({ meta: {} })).toBe(true);
  return document.querySelector('#route-progress');
}

beforeEach(async () => {
  vi.useFakeTimers();
  document.body.innerHTML = '';
  testState.messageError.mockReset();
  consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
  await loadRouter();
});

afterEach(() => {
  consoleError.mockRestore();
  vi.useRealTimers();
  document.body.innerHTML = '';
});

describe('路由进度条容错', () => {
  it('动态模块加载失败时立即隐藏进度条并提示一次', async () => {
    const progress = await startNavigation();
    expect(progress.style.width).toBe('70%');

    testState.hooks.onError(
      new TypeError('Failed to fetch dynamically imported module')
    );

    expect(progress.style.width).toBe('0px');
    expect(progress.style.opacity).toBe('0');
    expect(testState.messageError).toHaveBeenCalledTimes(1);
    expect(testState.messageError).toHaveBeenCalledWith(
      '页面加载失败，请刷新后重试'
    );
  });

  it('导航挂起十五秒时隐藏进度条且后续失败不重复提示', async () => {
    const progress = await startNavigation();

    await vi.advanceTimersByTimeAsync(15_000);

    expect(progress.style.width).toBe('0px');
    expect(progress.style.opacity).toBe('0');
    expect(testState.messageError).toHaveBeenCalledTimes(1);
    expect(testState.messageError).toHaveBeenCalledWith(
      '页面加载超时，请刷新后重试'
    );

    testState.hooks.onError(
      new TypeError('Failed to fetch dynamically imported module')
    );
    expect(testState.messageError).toHaveBeenCalledTimes(1);
  });

  it('旧完成动画不能清除紧随其后的新导航进度', async () => {
    const progress = await startNavigation();
    testState.hooks.afterEach();
    await vi.advanceTimersByTimeAsync(100);

    await startNavigation();
    await vi.advanceTimersByTimeAsync(200);

    expect(progress.style.width).toBe('70%');
    expect(progress.style.opacity).toBe('1');
  });

  it('正常导航保持完成动画并在三百毫秒后归零', async () => {
    const progress = await startNavigation();

    testState.hooks.afterEach();
    expect(progress.style.width).toBe('100%');

    await vi.advanceTimersByTimeAsync(300);
    expect(progress.style.width).toBe('0px');
    expect(progress.style.opacity).toBe('0');
  });
});
