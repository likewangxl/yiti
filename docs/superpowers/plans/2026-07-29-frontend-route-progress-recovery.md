# Frontend Route Progress Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent the Vue Router progress bar from remaining at 70% when a lazy-loaded page fails or never finishes loading.

**Architecture:** Extract the progress DOM and timer ownership into a small `createRouteProgress` controller. Keep authentication in the existing global guard, add a Vue Router error hook, and use a 15-second watchdog so both rejected and permanently pending route loads reach a visible terminal state.

**Tech Stack:** Vue 3.5, Vue Router 4.1, Element Plus 2.13, Vitest 1.6, happy-dom, Vite 4.5, Playwright CLI

## Global Constraints

- Preserve route-level dynamic `import()` and the normal `70% → 100% → hidden` animation.
- Do not automatically reload the browser.
- Do not modify `xanzc_frontend/vite.config.js`, backend code, database configuration, or unmatched menu routes.
- Use UTF-8 and preserve all pre-existing uncommitted changes.
- Follow Red-Green-Refactor: observe the regression tests fail before changing production code.

---

### Task 1: Route progress error and timeout recovery

**Files:**
- Create: `xanzc_frontend/src/router/__tests__/routeProgress.spec.js`
- Create: `xanzc_frontend/src/router/routeProgress.js`
- Modify: `xanzc_frontend/src/router/index.js:1-5`
- Modify: `xanzc_frontend/src/router/index.js:127-167`

**Interfaces:**
- Consumes: Vue Router `beforeEach`, `afterEach`, and `onError`; Element Plus `ElMessage.error(message)`.
- Produces: `createRouteProgress({ timeoutMs, onTimeout })` returning `{ start(), done(), fail() }`.
- `start()` shows 70% and owns a fresh watchdog; `done()` performs the success animation; `fail()` immediately resets and returns whether the navigation was still active.

- [ ] **Step 1: Write the failing route integration tests**

Create `xanzc_frontend/src/router/__tests__/routeProgress.spec.js`:

```js
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
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd xanzc_frontend
npm test -- src/router/__tests__/routeProgress.spec.js
```

Expected: the new suite fails because `router.onError` is not registered, no 15-second watchdog exists, and the old 300-millisecond completion timer clears a subsequent navigation.

- [ ] **Step 3: Implement the minimal progress controller**

Create `xanzc_frontend/src/router/routeProgress.js`:

```js
const DONE_DELAY_MS = 300;
const DEFAULT_TIMEOUT_MS = 15_000;

export function createRouteProgress({
  timeoutMs = DEFAULT_TIMEOUT_MS,
  onTimeout = () => {}
} = {}) {
  const element = document.createElement('div');
  element.id = 'route-progress';
  Object.assign(element.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    height: '2px',
    zIndex: '99999',
    background: 'linear-gradient(90deg, #409eff 0%, #1e5bba 100%)',
    transition: 'width .3s ease, opacity .2s',
    width: '0',
    opacity: '0'
  });
  document.body.appendChild(element);

  let active = false;
  let doneTimer = null;
  let watchdogTimer = null;

  function clearDoneTimer() {
    if (doneTimer !== null) clearTimeout(doneTimer);
    doneTimer = null;
  }

  function clearWatchdog() {
    if (watchdogTimer !== null) clearTimeout(watchdogTimer);
    watchdogTimer = null;
  }

  function reset() {
    element.style.opacity = '0';
    element.style.width = '0';
  }

  return {
    start() {
      clearDoneTimer();
      clearWatchdog();
      active = true;
      element.style.opacity = '1';
      element.style.width = '70%';
      watchdogTimer = setTimeout(() => {
        watchdogTimer = null;
        if (!active) return;
        active = false;
        reset();
        onTimeout();
      }, timeoutMs);
    },
    done() {
      if (!active) return false;
      clearWatchdog();
      clearDoneTimer();
      active = false;
      element.style.width = '100%';
      doneTimer = setTimeout(() => {
        doneTimer = null;
        reset();
      }, DONE_DELAY_MS);
      return true;
    },
    fail() {
      const wasActive = active;
      active = false;
      clearWatchdog();
      clearDoneTimer();
      reset();
      return wasActive;
    }
  };
}
```

- [ ] **Step 4: Wire the controller to Vue Router**

In `xanzc_frontend/src/router/index.js`, import the controller and Element Plus:

```js
import { ElMessage } from 'element-plus';
import { createRouteProgress } from './routeProgress';
```

Replace the inline bar with:

```js
const progress = createRouteProgress({
  onTimeout() {
    ElMessage.error('页面加载超时，请刷新后重试');
  }
});
```

Keep the authentication decisions unchanged, but use the controller:

```js
router.beforeEach(async (to) => {
  progress.start();
  const store = useUserStore();
  if (to.meta?.public) return true;
  if (store.isLoggedIn) return true;

  try {
    const user = await http.get('/api/auth/current-user');
    if (user && user.empId) {
      store.setUser(user);
      return true;
    }
  } catch (_) {
    // http.js 401 拦截器自己会清 sessionStorage；这里不重复
  }
  return { path: '/login', query: { redirect: to.fullPath } };
});

router.afterEach(() => {
  progress.done();
});

router.onError((error) => {
  const wasActive = progress.fail();
  console.error('[Router] 路由加载失败', error);
  if (wasActive) {
    ElMessage.error('页面加载失败，请刷新后重试');
  }
});
```

- [ ] **Step 5: Run the focused test and verify GREEN**

Run:

```bash
cd xanzc_frontend
npm test -- src/router/__tests__/routeProgress.spec.js
```

Expected: 4 tests pass with no uncaught errors.

- [ ] **Step 6: Refactor only if the focused test remains green**

Run:

```bash
cd xanzc_frontend
npm test -- src/router/__tests__/routeProgress.spec.js
```

Expected: 4 tests still pass after naming or formatting cleanup; do not add retry, reload, or unrelated route changes.

### Task 2: Full regression and browser verification

**Files:**
- Verify: `xanzc_frontend/src/router/__tests__/routeProgress.spec.js`
- Verify: `xanzc_frontend/src/router/routeProgress.js`
- Verify: `xanzc_frontend/src/router/index.js`

**Interfaces:**
- Consumes: the completed Task 1 route progress behavior.
- Produces: evidence that unit tests, production build, normal navigation, failed lazy loading, and repository scope are correct.

- [ ] **Step 1: Run all frontend unit tests**

Run:

```bash
cd xanzc_frontend
npm test
```

Expected: every Vitest file passes with zero failed tests.

- [ ] **Step 2: Build the production frontend**

Run:

```bash
cd xanzc_frontend
npm run build
```

Expected: Vite exits with code 0 and produces the production bundle.

- [ ] **Step 3: Verify with Playwright**

Start the backend and use the existing frontend on port 8091. Run the diagnostic Playwright script with `admin / 123456`:

```bash
YITI_TEST_USERNAME=admin \
YITI_TEST_PASSWORD=123456 \
PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright \
/home/djdev/.npm/_npx/e41f203b7505f1fb/node_modules/.bin/playwright \
test /tmp/yiti-playwright-diagnosis-20260729/yiti-progress.spec.cjs \
--reporter=line --workers=1
```

Expected:

- repeated normal navigation leaves `#route-progress` at width 0 and opacity 0;
- aborting `Dynamic.vue` records a dynamic-import error but leaves the progress bar hidden;
- the failed route produces one visible error notification;
- both Playwright scenarios pass.

- [ ] **Step 4: Check final scope**

Run:

```bash
git diff --check
git status --short
git diff -- xanzc_frontend/src/router/index.js \
  xanzc_frontend/src/router/routeProgress.js \
  xanzc_frontend/src/router/__tests__/routeProgress.spec.js
```

Expected: only the route controller, route integration, and its regression test are new implementation changes; pre-existing changes to `AGENTS.md`, `bootstrap/src/main/resources/application.yml`, and `xanzc_frontend/vite.config.js` remain untouched.

- [ ] **Step 5: Commit the verified implementation**

Run:

```bash
git add xanzc_frontend/src/router/index.js \
  xanzc_frontend/src/router/routeProgress.js \
  xanzc_frontend/src/router/__tests__/routeProgress.spec.js \
  docs/superpowers/plans/2026-07-29-frontend-route-progress-recovery.md
git commit -m "fix(frontend): recover stalled route progress"
```

Expected: one implementation commit containing only the plan, controller, router integration, and tests.
