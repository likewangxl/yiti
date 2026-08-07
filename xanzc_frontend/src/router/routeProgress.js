const DONE_DELAY_MS = 300;
const DEFAULT_TIMEOUT_MS = 15_000;

/**
 * 创建路由导航进度控制器。
 *
 * @param {{ timeoutMs?: number, onTimeout?: () => void }} options 控制器配置
 * @returns {{ start: () => void, done: () => boolean, fail: () => boolean }} 进度控制器
 */
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
