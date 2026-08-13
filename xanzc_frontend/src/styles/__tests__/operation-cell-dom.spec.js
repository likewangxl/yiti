// @vitest-environment happy-dom
import { afterAll, afterEach, beforeEach, describe, expect, it } from 'vitest';
import { compile } from 'sass';
import { resolve } from 'node:path';
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
            <div class="cell-detail">{{ row.detail }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" fixed="right" width="100">
          <template #default><el-button link>查看详情</el-button></template>
        </el-table-column>
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

beforeEach(() => {
  // Element Plus 官方样式先由上方真实入口加载，项目 SCSS 后注入。
  // 断言通过 DOM 的 getComputedStyle 验证两者的实际层叠结果，而非只匹配源码文本。
  crudBaselineStyle = document.createElement('style');
  crudBaselineStyle.textContent = compile(resolve(process.cwd(), 'src/styles/index.scss')).css;
  document.head.append(crudBaselineStyle);
});

afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
  crudBaselineStyle?.remove();
  crudBaselineStyle = undefined;
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

  it('真实 Element Plus 单元格计算样式为 40px 且不再保留默认上下内边距', async () => {
    wrapper = mount(OperationTable, { attachTo: document.body, global: { plugins: [ElementPlus] } });
    await nextTick();
    await nextTick();

    const headerCell = wrapper.get('th.el-table__cell').element;
    const dataCell = wrapper.get('td.el-table__cell').element;

    for (const cell of [headerCell, dataCell]) {
      const style = getComputedStyle(cell);
      expect(style.height).toBe('40px');
      expect(style.paddingTop).toBe('0px');
      expect(style.paddingBottom).toBe('0px');
    }
  });

  it('双行信息、24px 标签和 32px 操作控件在 40px 行内完整呈现，宽表只在表内滚动', async () => {
    wrapper = mount(CompactDensityTable, { attachTo: document.body, global: { plugins: [ElementPlus] } });
    await nextTick();
    await nextTick();

    const compactCell = wrapper.get('td.compact-stack-cell:not(.compact-status-cell) .cell').element;
    const compactRow = compactCell.closest('tr');
    const compactTd = compactCell.closest('td');
    const compactChildren = [...compactCell.children].map((child) => getComputedStyle(child));
    expect(getComputedStyle(compactRow).height).toBe('40px');
    expect(getComputedStyle(compactTd).height).toBe('40px');
    expect(getComputedStyle(compactCell).display).toBe('grid');
    expect(compactChildren.map((style) => style.lineHeight)).toEqual(['18px', '18px']);
    expect(compactChildren.reduce((sum, style) => sum + Number.parseFloat(style.lineHeight), 0)).toBeLessThanOrEqual(40);

    const compactHeader = wrapper.get('th.compact-stack-header');
    const headerLines = compactHeader.get('.compact-header-lines');
    expect(getComputedStyle(compactHeader.element).height).toBe('40px');
    expect(getComputedStyle(headerLines.element).display).toBe('grid');
    expect(headerLines.findAll('span').map((line) => getComputedStyle(line.element).lineHeight)).toEqual(['18px', '18px']);

    const statusCell = wrapper.get('td.compact-status-cell .cell');
    const tagStyle = getComputedStyle(statusCell.get('.el-tag').element);
    const detailStyle = getComputedStyle(statusCell.get('.cell-detail').element);
    expect(tagStyle.height).toBe('24px');
    expect(detailStyle.height).toBe('16px');
    expect(Number.parseFloat(tagStyle.height) + Number.parseFloat(detailStyle.height)).toBe(40);

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
    }
    expect(getComputedStyle(cells[0].element.closest('tr')).height).toBe('40px');
  });
});

afterAll(() => {
  globalThis.ResizeObserver = previousResizeObserver;
});
