const HANDLER_KEY = Symbol('bpOverflowTooltipHandler');
const GENERATED_TITLE_ATTR = 'data-bp-overflow-tooltip';
const CELL_SELECTOR = '.el-table__body .cell, .el-table__header .cell';

function syncOverflowTitle(cell) {
  const text = cell.textContent?.trim() || '';
  const isOverflowing = cell.scrollWidth > cell.clientWidth || cell.scrollHeight > cell.clientHeight;
  const isGenerated = cell.getAttribute(GENERATED_TITLE_ATTR) === 'true';

  // 已有业务专用 title 时不覆盖，避免破坏页面已有的字段说明。
  if (cell.hasAttribute('title') && !isGenerated) return;

  if (isOverflowing && text) {
    cell.setAttribute('title', text);
    cell.setAttribute(GENERATED_TITLE_ATTR, 'true');
    return;
  }

  if (isGenerated) {
    cell.removeAttribute('title');
    cell.removeAttribute(GENERATED_TITLE_ATTR);
  }
}

/**
 * 主平台 CRUD 表格的长文本提示。
 *
 * 指令只在显式 `.bp-crud` 容器内生效；红色引擎与大屏运行态没有该容器，
 * 即使误挂指令也不会发生 DOM 或交互变化。浏览器原生 title 只在真实溢出时写入，
 * 与全局 CSS 省略号共同保证“截断可见、悬停可读完整值”。
 */
export const bpOverflowTooltip = {
  mounted(el) {
    if (!el.classList.contains('bp-crud')) return;

    const handler = (event) => {
      if (!(event.target instanceof Element)) return;
      const cell = event.target.closest(CELL_SELECTOR);
      if (!cell) return;

      // 默认 Dialog 不传送；少量 append-to-body 对话框通过 bp-crud-dialog 保持同一受控边界。
      const isInCurrentPage = el.contains(cell);
      const isInPlatformDialog = cell.closest('.bp-crud-dialog');
      if (!isInCurrentPage && !isInPlatformDialog) return;
      syncOverflowTitle(cell);
    };

    el[HANDLER_KEY] = handler;
    document.addEventListener('mouseover', handler);
  },

  unmounted(el) {
    const handler = el[HANDLER_KEY];
    if (!handler) return;
    document.removeEventListener('mouseover', handler);
    delete el[HANDLER_KEY];
  }
};
