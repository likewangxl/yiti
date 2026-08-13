// @vitest-environment happy-dom
import { afterAll, afterEach, beforeEach, describe, expect, it } from 'vitest';
import { compile } from 'sass';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { compileStyle, parse } from '@vue/compiler-sfc';
import { mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const previousResizeObserver = globalThis.ResizeObserver;
globalThis.ResizeObserver = ResizeObserverStub;

const OperationTable = defineComponent({
  template: `
    <main class="bp-crud">
      <el-table :data="[{ name: '普通长文本', action: '编辑' }]">
        <el-table-column prop="name" label="名称" width="80" />
        <el-table-column label="操作" class-name="operation-cell" fixed="right" width="100">
          <template #default><el-button link>编辑</el-button></template>
        </el-table-column>
      </el-table>
    </main>`
});

const CompactDensityTable = defineComponent({
  template: `
    <main class="bp-crud" style="width: 320px">
      <el-table :data="[{ primary: '西安高新支行', secondary: 'A001', status: '异常', detail: '规则校验失败' }]">
        <el-table-column label="客户 / 人员" label-class-name="compact-stack-header" class-name="compact-stack-cell" width="180">
          <template #header>
            <span class="compact-header-lines"><span>客户名称</span><span>客户编号</span></span>
          </template>
          <template #default="{ row }">
            <div>{{ row.primary }}</div>
            <div class="cell-meta">{{ row.secondary }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" class-name="compact-stack-cell compact-status-cell" width="140">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ row.status }}</el-tag>
            <div data-v-task-monitor-fixture class="cell-detail err-inline">{{ row.detail }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" fixed="right" width="100">
          <template #default><el-button link>查看详情</el-button></template>
        </el-table-column>
      </el-table>
    </main>`
});

const ResourcesDensityTable = defineComponent({
  template: `
    <main class="bp-crud resources-page" data-v-resources-fixture>
      <el-table class="menu-table" data-v-resources-fixture :data="[{ name: '系统资源' }]">
        <el-table-column prop="name" label="菜单名称" />
      </el-table>
    </main>`
});

const ProductClampTable = defineComponent({
  template: `
    <main class="bp-crud product-lib-page">
      <el-table :data="[{ department: '公司金融业务部重点产品支持团队', name: '产业链融资综合服务方案', description: '面向核心企业上下游客户提供覆盖采购生产销售环节的综合融资与结算服务方案' }]">
        <el-table-column prop="department" label="产品部门" width="190" class-name="compact-clamp-cell" />
        <el-table-column prop="name" label="产品名称" width="200" class-name="compact-clamp-cell" />
        <el-table-column prop="description" label="产品说明" width="270" class-name="compact-clamp-cell" />
      </el-table>
    </main>`
});

let wrapper;
let crudBaselineStyle;
let taskMonitorScopedStyle;
let resourcesScopedStyle;

const px = (value) => Number.parseFloat(value) || 0;
const tableCellMetrics = () => {
  const declaration = crudBaselineStyle.textContent.match(
    /\.bp-crud \.el-table th\.el-table__cell,\s*\.bp-crud \.el-table td\.el-table__cell\s*\{([^}]*)\}/
  )?.[1] || '';

  // happy-dom 会把 `border-bottom: 1px solid var(--color-border)` 错误解析成
  // `border-bottom-width: var(--color-border)`。这里从同一份编译 CSS 声明读取
  // 可见边框宽度，并继续通过真实 Element Plus DOM 校验内容盒高度。
  const borderBottom = px(declaration.match(/border-bottom:\s*([\d.]+)px\s+solid/i)?.[1]);
  return {
    borderBottom,
    declaredHeight: px(declaration.match(/height:\s*([\d.]+)px/i)?.[1])
  };
};
const contentWithCellBorderHeight = (element) => {
  const { borderBottom } = tableCellMetrics();
  return px(getComputedStyle(element).height) + borderBottom;
};
const compileScopedSfcStyle = (relativePath, scopeId) => {
  const filename = resolve(process.cwd(), relativePath);
  const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename });
  const result = compileStyle({
    source: descriptor.styles[0].content,
    filename,
    id: scopeId,
    scoped: true,
    preprocessLang: descriptor.styles[0].lang,
    preprocessOptions: {
      additionalData: `@use "${resolve(process.cwd(), 'src/styles/tokens.scss')}" as *;`
    }
  });
  if (result.errors.length) throw result.errors[0];
  return result.code;
};

beforeEach(() => {
  // Element Plus 官方样式先由上方真实入口加载，项目 SCSS 后注入。
  // 断言通过 DOM 的 getComputedStyle 验证两者的实际层叠结果，而非只匹配源码文本。
  crudBaselineStyle = document.createElement('style');
  crudBaselineStyle.textContent = compile(resolve(process.cwd(), 'src/styles/index.scss')).css;
  document.head.append(crudBaselineStyle);

  // 页面 scoped 样式在全局基线之后加载，复现生产页面最终级联顺序。
  taskMonitorScopedStyle = document.createElement('style');
  taskMonitorScopedStyle.textContent = compileScopedSfcStyle(
    'src/views/perf/TaskMonitor.vue',
    'data-v-task-monitor-fixture'
  );
  document.head.append(taskMonitorScopedStyle);

  resourcesScopedStyle = document.createElement('style');
  resourcesScopedStyle.textContent = compileScopedSfcStyle(
    'src/views/system/Resources.vue',
    'data-v-resources-fixture'
  );
  document.head.append(resourcesScopedStyle);
});

afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
  crudBaselineStyle?.remove();
  crudBaselineStyle = undefined;
  taskMonitorScopedStyle?.remove();
  taskMonitorScopedStyle = undefined;
  resourcesScopedStyle?.remove();
  resourcesScopedStyle = undefined;
});

describe('操作列真实 DOM 基线', () => {
  it('Element Plus 将 class-name 落在固定右侧单元格，且真实计算样式覆盖默认单元格规则', async () => {
    wrapper = mount(OperationTable, { attachTo: document.body, global: { plugins: [ElementPlus] } });
    await nextTick();
    await nextTick();

    const operationCell = wrapper.find('td.operation-cell .cell');
    const ordinaryCell = wrapper.find('td:not(.operation-cell) .cell');

    expect(operationCell.exists()).toBe(true);
    expect(operationCell.element.matches('.operation-cell .cell')).toBe(true);
    expect(operationCell.element.closest('td')?.classList.contains('el-table-fixed-column--right')).toBe(true);
    expect(ordinaryCell.element.matches('.operation-cell .cell')).toBe(false);

    const ordinaryStyle = getComputedStyle(ordinaryCell.element);
    expect(ordinaryStyle.overflow).toBe('hidden');
    expect(ordinaryStyle.textOverflow).toBe('ellipsis');
    expect(ordinaryStyle.whiteSpace).toBe('nowrap');

    const operationStyle = getComputedStyle(operationCell.element);
    expect(operationStyle.overflow).toBe('visible');
    expect(operationStyle.textOverflow).toBe('clip');
    expect(operationStyle.whiteSpace).toBe('nowrap');
  });

  it('真实 Element Plus 表头与单行数据把可见边框计入 40px 外框预算', async () => {
    wrapper = mount(OperationTable, { attachTo: document.body, global: { plugins: [ElementPlus] } });
    await nextTick();
    await nextTick();

    const headerCell = wrapper.get('th.el-table__cell').element;
    const dataCell = wrapper.get('td.el-table__cell').element;
    const metrics = tableCellMetrics();

    expect(metrics.borderBottom).toBe(1);
    expect(metrics.declaredHeight + metrics.borderBottom).toBe(40);
    for (const cell of [headerCell, dataCell]) {
      const style = getComputedStyle(cell);
      expect(style.paddingTop).toBe('0px');
      expect(style.paddingBottom).toBe('0px');
      expect(style.boxSizing).toBe('border-box');
    }
  });

  it('双行信息、24px 标签和 32px 操作控件在 40px 行内完整呈现，宽表只在表内滚动', async () => {
    wrapper = mount(CompactDensityTable, { attachTo: document.body, global: { plugins: [ElementPlus] } });
    await nextTick();
    await nextTick();

    const compactCell = wrapper.get('td.compact-stack-cell:not(.compact-status-cell) .cell').element;
    const compactTd = compactCell.closest('td');
    const compactChildren = [...compactCell.children].map((child) => getComputedStyle(child));
    expect(compactTd).not.toBeNull();
    expect(tableCellMetrics().declaredHeight + tableCellMetrics().borderBottom).toBe(40);
    expect(contentWithCellBorderHeight(compactCell)).toBe(40);
    expect(getComputedStyle(compactCell).display).toBe('grid');
    expect(compactChildren.map((style) => style.lineHeight)).toEqual(['18px', '18px']);
    expect(compactChildren.reduce((sum, style) => sum + Number.parseFloat(style.lineHeight), 0)).toBeLessThanOrEqual(px(getComputedStyle(compactCell).height));

    const compactHeader = wrapper.get('th.compact-stack-header');
    const headerLines = compactHeader.get('.compact-header-lines');
    expect(getComputedStyle(headerLines.element).display).toBe('grid');
    expect(headerLines.findAll('span').map((line) => getComputedStyle(line.element).lineHeight)).toEqual(['18px', '18px']);
    expect(contentWithCellBorderHeight(headerLines.element)).toBe(40);

    const statusCell = wrapper.get('td.compact-status-cell .cell');
    const tagStyle = getComputedStyle(statusCell.get('.el-tag').element);
    const detailStyle = getComputedStyle(statusCell.get('.cell-detail').element);
    expect(tagStyle.height).toBe('24px');
    expect(detailStyle.height).toBe('15px');
    expect(detailStyle.marginTop).toBe('0px');
    const detailOuterHeight = px(detailStyle.height) + px(detailStyle.marginTop) + px(detailStyle.marginBottom);
    expect(detailOuterHeight).toBeLessThanOrEqual(15);
    expect(px(tagStyle.height) + detailOuterHeight + tableCellMetrics().borderBottom).toBe(40);

    const operationCell = wrapper.get('td.operation-cell');
    expect(getComputedStyle(operationCell.element).position).toBe('sticky');
    expect(getComputedStyle(operationCell.get('.el-button').element).minHeight).toBe('32px');

    const main = wrapper.get('main').element;
    const table = wrapper.get('.el-table').element;
    const tableScroller = wrapper.get('.el-scrollbar__wrap').element;
    Object.defineProperties(main, {
      clientWidth: { configurable: true, value: 320 },
      scrollWidth: { configurable: true, value: 320 }
    });
    Object.defineProperties(tableScroller, {
      clientWidth: { configurable: true, value: 320 },
      scrollWidth: { configurable: true, value: 420 }
    });
    expect(main.scrollWidth).toBe(main.clientWidth);
    expect(tableScroller.scrollWidth).toBeGreaterThan(tableScroller.clientWidth);
    expect(getComputedStyle(table).maxWidth).toBe('100%');
    const scrollerStyle = getComputedStyle(tableScroller);
    expect(['auto', 'scroll']).toContain(scrollerStyle.overflowX || scrollerStyle.overflow);
  });

  it('Resources 后加载 scoped 样式不恢复单元格 padding，真实表格外框仍为 40px', async () => {
    wrapper = mount(ResourcesDensityTable, { attachTo: document.body, global: { plugins: [ElementPlus] } });
    await nextTick();
    await nextTick();

    const row = wrapper.get('tbody tr.el-table__row').element;
    const cell = wrapper.get('tbody td.el-table__cell').element;
    const content = wrapper.get('tbody td.el-table__cell .cell').element;
    const cellStyle = getComputedStyle(cell);
    const contentStyle = getComputedStyle(content);
    const activeScopedPadding = [...resourcesScopedStyle.sheet.cssRules]
      .filter((rule) => rule.selectorText && cell.matches(rule.selectorText))
      .map((rule) => rule.style.getPropertyValue('padding-block'))
      .filter((value) => value && value !== '0' && value !== '0px');

    expect(wrapper.get('.menu-table').attributes()).toHaveProperty('data-v-resources-fixture', '');
    // happy-dom 尚不把逻辑属性 padding-block 映射到 paddingTop/Bottom；因此同时
    // 对真实 td 匹配后加载的编译 scoped 规则，避免 computed 0px 的假阳性。
    expect(activeScopedPadding).toEqual([]);
    expect(getComputedStyle(row).height).toBe('40px');
    expect(cellStyle.paddingTop).toBe('0px');
    expect(cellStyle.paddingBottom).toBe('0px');
    expect(tableCellMetrics().borderBottom).toBe(1);
    expect(tableCellMetrics().declaredHeight + tableCellMetrics().borderBottom).toBe(40);
    expect(px(contentStyle.lineHeight || '22px')).toBeLessThanOrEqual(39);
  });

  it('ProductLib 三列真实长文本最多显示两行且总高度适配 40px 数据行', async () => {
    wrapper = mount(ProductClampTable, { attachTo: document.body, global: { plugins: [ElementPlus] } });
    await nextTick();
    await nextTick();

    const cells = wrapper.findAll('td.compact-clamp-cell .cell');
    expect(cells).toHaveLength(3);

    // happy-dom 会丢弃 CSSOM 中的 `display: -webkit-box`，且不会把 WebKit 扩展
    // 暴露到 computed style；display 由源码契约门禁，本测试用同一真实挂载后的
    // CSSOM 校验 line-clamp，再用计算样式校验其余尺度。
    const clampRule = [...crudBaselineStyle.sheet.cssRules].find((rule) =>
      rule.selectorText?.includes('td.compact-clamp-cell .cell')
    );
    expect(clampRule.style.getPropertyValue('-webkit-line-clamp')).toBe('2');
    expect(clampRule.style.getPropertyValue('-webkit-box-orient')).toBe('vertical');

    for (const cell of cells) {
      const style = getComputedStyle(cell.element);
      expect(style.lineHeight).toBe('18px');
      expect(style.maxHeight).toBe('36px');
      expect(style.overflow).toBe('hidden');
      expect(style.whiteSpace).toBe('normal');
      expect(cell.element.textContent.trim().length).toBeGreaterThan(0);
      expect(tableCellMetrics().declaredHeight + tableCellMetrics().borderBottom).toBe(40);
      expect(px(style.maxHeight) + tableCellMetrics().borderBottom).toBeLessThanOrEqual(40);
    }
  });
});

afterAll(() => {
  globalThis.ResizeObserver = previousResizeObserver;
});
