// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest';
import { bpOverflowTooltip } from '../bpOverflowTooltip';

const mountedRoots = [];
const nodes = [];

function mountTable({ text = '西安高新支行名称过长', width = 80, scrollWidth = 180, inCrud = true } = {}) {
  const page = document.createElement('main');
  if (inCrud) page.className = 'bp-crud';
  page.innerHTML = `
    <div class="el-table">
      <div class="el-table__body"><div class="cell"><span>${text}</span></div></div>
    </div>`;
  document.body.appendChild(page);
  const cell = page.querySelector('.cell');
  Object.defineProperties(cell, {
    clientWidth: { configurable: true, value: width },
    scrollWidth: { configurable: true, value: scrollWidth }
  });
  bpOverflowTooltip.mounted(page);
  mountedRoots.push(page);
  nodes.push(page);
  return { page, cell };
}

afterEach(() => {
  for (const root of mountedRoots.splice(0)) bpOverflowTooltip.unmounted(root);
  for (const node of nodes.splice(0)) node.remove();
});

describe('bpOverflowTooltip 受控表格溢出提示', () => {
  it('仅在 bp-crud 中的真实溢出单元格写入完整文本 title', () => {
    const { cell } = mountTable();

    cell.querySelector('span').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

    expect(cell.getAttribute('title')).toBe('西安高新支行名称过长');
  });

  it('未溢出、空文本或 bp-crud 范围外均不写入 title', () => {
    const compact = mountTable({ width: 180, scrollWidth: 80 });
    compact.cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    expect(compact.cell.hasAttribute('title')).toBe(false);

    const outside = mountTable({ inCrud: false });
    outside.cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    expect(outside.cell.hasAttribute('title')).toBe(false);
  });

  it('卸载后移除监听器，避免页面切换残留', () => {
    const { page, cell } = mountTable({ width: 180, scrollWidth: 80 });
    bpOverflowTooltip.unmounted(page);
    Object.defineProperties(cell, {
      clientWidth: { configurable: true, value: 80 },
      scrollWidth: { configurable: true, value: 180 }
    });

    cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

    expect(cell.hasAttribute('title')).toBe(false);
  });

  it('append-to-body 对话框以 bp-crud-dialog 标识后仍可提示完整文本', () => {
    mountTable({ width: 180, scrollWidth: 80 });
    const dialog = document.createElement('div');
    dialog.className = 'bp-crud-dialog';
    dialog.innerHTML = '<div class="el-table__body"><div class="cell">导入批次异常原因过长</div></div>';
    document.body.appendChild(dialog);
    nodes.push(dialog);
    const cell = dialog.querySelector('.cell');
    Object.defineProperties(cell, {
      clientWidth: { configurable: true, value: 80 },
      scrollWidth: { configurable: true, value: 180 }
    });

    cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

    expect(cell.getAttribute('title')).toBe('导入批次异常原因过长');
  });

  it('动态内容变化时只更新自己维护的 title', () => {
    const { cell } = mountTable({ text: '第一版机构名称', width: 80, scrollWidth: 180 });

    cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    expect(cell.getAttribute('title')).toBe('第一版机构名称');

    cell.querySelector('span').textContent = '第二版机构名称';
    cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

    expect(cell.getAttribute('title')).toBe('第二版机构名称');
    expect(cell.getAttribute('data-bp-overflow-tooltip')).toBe('第二版机构名称');
  });

  it('业务后来写入 title 时不再覆盖或删除该业务文案', () => {
    const { cell } = mountTable({ width: 80, scrollWidth: 180 });

    cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    expect(cell.getAttribute('title')).toBe('西安高新支行名称过长');

    cell.setAttribute('title', '业务字段专用说明');
    cell.querySelector('span').textContent = '业务更新后的单元格文本';
    cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    expect(cell.getAttribute('title')).toBe('业务字段专用说明');

    Object.defineProperties(cell, {
      clientWidth: { configurable: true, value: 180 },
      scrollWidth: { configurable: true, value: 80 }
    });
    cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

    expect(cell.getAttribute('title')).toBe('业务字段专用说明');
    expect(cell.hasAttribute('data-bp-overflow-tooltip')).toBe(false);
  });
});
