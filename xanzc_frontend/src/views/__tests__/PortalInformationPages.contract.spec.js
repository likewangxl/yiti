import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const pages = [
  'workspace/AnnouncementList.vue',
  'workspace/NotificationList.vue',
  'info/NavHub.vue',
  'info/AddressBook.vue',
  'info/ProductLib.vue',
  'info/DocCenter.vue',
  'system/Announcements.vue',
  'system/AnnouncementDetail.vue'
];

function sourceOf(page) {
  return readFileSync(new URL(`../${page}`, import.meta.url), 'utf8');
}

describe('门户公告与信息聚合页结构契约', () => {
  it.each(pages)('%s 显式使用主平台 bp-crud 页面边界和 PageTitle', (page) => {
    const source = sourceOf(page);

    expect(source).toMatch(/<main\b(?=[^>]*class="bp-crud\b[^\"]*")(?=[^>]*aria-labelledby=)[^>]*>/);
    expect(source).toMatch(/<header\s+class="page-h"/);
    expect(source).toMatch(/<PageTitle\b/);
    expect(source).toMatch(/<(?:section|article)\b/);
    expect(source).toMatch(/aria-busy=/);
    expect(source).toMatch(/role="status"/);
    expect(source).not.toMatch(/\b(?:redengine|screen(?:-admin)?)\b/i);
  });

  it.each([
    'workspace/AnnouncementList.vue',
    'workspace/NotificationList.vue',
    'info/AddressBook.vue',
    'info/ProductLib.vue',
    'info/DocCenter.vue',
    'system/Announcements.vue'
  ])('%s 使用数据面板、状态文本和语义分页', (page) => {
    const source = sourceOf(page);

    expect(source).toContain('data-panel');
    expect(source).toContain('table-state');
    expect(source).toMatch(/<nav\s+class="pager"[^>]*aria-label=/);
  });

  it.each([
    'workspace/AnnouncementList.vue',
    'info/AddressBook.vue',
    'info/ProductLib.vue',
    'info/DocCenter.vue',
    'system/Announcements.vue'
  ])('%s 为查询条件声明筛选区域与可访问输入标签', (page) => {
    const source = sourceOf(page);

    expect(source).toContain('filter-bar');
    expect(source).toMatch(/aria-label="[^"]*筛选/);
  });

  it('网址导航和公告详情也维持数据状态及操作语义，而非套用屏幕子系统结构', () => {
    const navHub = sourceOf('info/NavHub.vue');
    const detail = sourceOf('system/AnnouncementDetail.vue');

    expect(navHub).toContain('data-panel');
    expect(navHub).toContain('table-state');
    expect(navHub).toMatch(/<a\b[^>]*target="_blank"/);
    expect(detail).toContain('data-panel');
    expect(detail).toContain('table-state');
    expect(detail).toMatch(/<article\b/);
  });
});
