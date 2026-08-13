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

  it('真实 mouseover 同时识别纵向截断，内容恢复后只清理指令自有 title', () => {
    const { cell } = mountTable({ text: '多行审批意见', width: 180, scrollWidth: 180 });
    Object.defineProperties(cell, {
      clientHeight: { configurable: true, value: 22 },
      scrollHeight: { configurable: true, value: 44 }
    });

    cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    expect(cell.getAttribute('title')).toBe('多行审批意见');

    Object.defineProperties(cell, {
      clientHeight: { configurable: true, value: 44 },
      scrollHeight: { configurable: true, value: 44 }
    });
    cell.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

    expect(cell.hasAttribute('title')).toBe(false);
    expect(cell.hasAttribute('data-bp-overflow-tooltip')).toBe(false);
  });

  it('真实 mouseover 能识别紧凑双行中单个子行的横向溢出并给出两行完整值', () => {
    const { cell } = mountTable({ text: '', width: 180, scrollWidth: 180 });
    cell.innerHTML = '<div>西安高新支行客户名称</div><div>A001</div>';
    const primary = cell.firstElementChild;
    Object.defineProperties(primary, {
      clientWidth: { configurable: true, value: 80 },
      scrollWidth: { configurable: true, value: 180 },
      clientHeight: { configurable: true, value: 18 },
      scrollHeight: { configurable: true, value: 18 }
    });

    primary.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

    expect(cell.getAttribute('title')).toBe('西安高新支行客户名称 / A001');
  });

  it('nested span 溢出时仍保留业务 title，并撤销指令旧 title 的所有权标记', () => {
    const { cell } = mountTable({ text: '', width: 180, scrollWidth: 180 });
    cell.innerHTML = '<div><span>客户名称</span></div><div><span>A001</span></div>';
    const primary = cell.firstElementChild;
    Object.defineProperties(primary, {
      clientWidth: { configurable: true, value: 60 },
      scrollWidth: { configurable: true, value: 120 }
    });

    primary.firstElementChild.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    expect(cell.getAttribute('title')).toBe('客户名称 / A001');
    expect(cell.getAttribute('data-bp-overflow-tooltip')).toBe('客户名称 / A001');

    cell.setAttribute('title', '业务专用客户说明');
    primary.firstElementChild.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));

    expect(cell.getAttribute('title')).toBe('业务专用客户说明');
    expect(cell.hasAttribute('data-bp-overflow-tooltip')).toBe(false);
  });

  it('ProductLib 两行省略的真实长文本 mouseover 写入全文，短文本不误加 title', () => {
    const fullText = '面向核心企业上下游客户提供覆盖采购生产销售环节的综合融资与结算服务方案';
    const long = mountTable({ text: fullText, width: 270, scrollWidth: 270 });
    long.cell.parentElement.classList.add('compact-clamp-cell');
    Object.defineProperties(long.cell, {
      clientHeight: { configurable: true, value: 36 },
      scrollHeight: { configurable: true, value: 72 }
    });

    long.cell.querySelector('span').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    expect(long.cell.getAttribute('title')).toBe(fullText);

    const short = mountTable({ text: '流动资金贷款', width: 270, scrollWidth: 120 });
    short.cell.parentElement.classList.add('compact-clamp-cell');
    Object.defineProperties(short.cell, {
      clientHeight: { configurable: true, value: 18 },
      scrollHeight: { configurable: true, value: 18 }
    });

    short.cell.querySelector('span').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    expect(short.cell.hasAttribute('title')).toBe(false);
  });
});
