// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/redengine/dashboard' })
}));

vi.mock('@/api/auth', () => ({
  getMyPermissions: vi.fn().mockResolvedValue({ resourceUrls: [] }),
  logout: vi.fn().mockResolvedValue('OK')
}));

import RedEngineLayout from '../RedEngineLayout.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const stubs = {
  'el-container': passthrough('ElContainer'),
  'el-aside': passthrough('ElAside'),
  'el-menu': passthrough('ElMenu'),
  'el-menu-item': passthrough('ElMenuItem'),
  'el-header': passthrough('ElHeader'),
  'el-main': passthrough('ElMain'),
  'router-view': true,
  'el-button': passthrough('ElButton')
};

const source = readFileSync(resolve(process.cwd(), 'src/views/redengine/layout/RedEngineLayout.vue'), 'utf8');

let wrapper;

afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
});

function setViewport(width) {
  Object.defineProperty(window, 'innerWidth', {
    configurable: true,
    value: width
  });
  Object.defineProperty(document.documentElement, 'clientWidth', {
    configurable: true,
    value: width
  });
}

function ruleBody(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = source.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`));
  expect(match, `${selector} CSS rule`).toBeTruthy();
  return match[1];
}

describe('红色引擎布局横向溢出契约', () => {
  it('侧栏宽度和左对齐基线复用主系统布局 token', () => {
    expect(source).toMatch(/<el-aside[^>]*width="var\(--layout-sidebar-width\)"/);
    expect(source).not.toMatch(/275px/);

    const logoRule = ruleBody('.re-sidebar-logo');
    expect(logoRule).toMatch(/justify-content:\s*flex-start/);
    expect(logoRule).toMatch(/padding:\s*0 var\(--space-2\) 0 var\(--space-4\)/);
    expect(logoRule).toMatch(/height:\s*var\(--layout-header-height\)/);

    const menuItemRule = ruleBody('.el-menu-item');
    expect(menuItemRule).toMatch(/justify-content:\s*flex-start/);
    expect(menuItemRule).toMatch(/text-align:\s*left/);
    expect(menuItemRule).toMatch(/padding:\s*var\(--space-2\) var\(--space-4\) !important/);
  });

  it.each([
    ['1440px 桌面视口', 1440],
    ['窄视口', 640]
  ])('%s 保持主工作区可收缩且 DOM 不改变', async (_label, width) => {
    setViewport(width);
    setActivePinia(createPinia());
    wrapper = mount(RedEngineLayout, { global: { stubs } });

    expect(window.innerWidth).toBe(width);
    expect(wrapper.find('.re-layout').exists()).toBe(true);
    expect(wrapper.find('.re-main-content').exists()).toBe(true);
    expect(wrapper.find('.re-main-area').exists()).toBe(true);
    expect(wrapper.find('.re-sidebar-menu').exists()).toBe(true);

    // flex 子项默认 min-width:auto 会把长内容的最小宽度传给页面，导致 body 横向滚动。
    expect(ruleBody('.re-main-content')).toMatch(/min-width:\s*0/);
    expect(ruleBody('.re-main-area')).toMatch(/min-width:\s*0/);
  });
});
