import { describe, it, expect } from 'vitest';
import { cellDisplay, cellFull } from '../cellFmt';

describe('cellDisplay —— 自由报表单元格显示文本', () => {
  it('新批次(带 __raw)：直接用导入时保留的 Excel 显示文本，不再二次加工', () => {
    // 极小值：Excel 里显示 -0.0，不能变成科学计数法或 -0.00
    const row = { col_3: '-0.0', col_3__raw: '-0.000000500000000069889' };
    expect(cellDisplay(row, 'col_3')).toBe('-0.0');
  });

  it('新批次：百分比原样显示，不丢 % 号', () => {
    const row = { col_4: '54.5%', col_4__raw: '0.545175438596492' };
    expect(cellDisplay(row, 'col_4')).toBe('54.5%');
  });

  it('老批次(无 __raw 也无 __fmt)：沿用截断两位的旧行为，保证存量数据展示不变', () => {
    expect(cellDisplay({ col_3: '3.1779998' }, 'col_3')).toBe('3.17');
    expect(cellDisplay({ col_3: '3.1' }, 'col_3')).toBe('3.10');
  });

  it('新批次但显示文本==原值时后端不写 __raw，仅凭 __fmt 也必须判为新批次原样显示', () => {
    // 真实落库形态：{"col_5":"961.02","col_5__fmt":"0.00"} —— 无 __raw
    // 若只看 __raw 会误判成老批次，被 truncate2 再加工（961.02 恰好不变，但
    // 会计格式的 "-" 会被当成普通文本、百分比 "91%" 也会走错分支）
    expect(cellDisplay({ col_5: '961.02', col_5__fmt: '0.00' }, 'col_5')).toBe('961.02');
    expect(cellDisplay({ col_9: '91%', col_9__fmt: '0%' }, 'col_9')).toBe('91%');
  });

  it('会计格式：0 显示为 "-"，与原始 Excel 一致', () => {
    // 源 Excel 会计格式(numFmtId=41) 的 0 渲染为 "-"
    const row = { col_9: '-', col_9__raw: '0.0', col_9__fmt: '_ * #,##0_ ;_ * \\-#,##0_ ;_ * "-"_ ;_ @_' };
    expect(cellDisplay(row, 'col_9')).toBe('-');
    expect(cellFull(row, 'col_9')).toBe('0.0');
  });

  it('自定义格式：极小值显示 -0.00，点击看完整值', () => {
    const row = {
      col_9: '-0.00',
      col_9__raw: '-0.000000500000000069889',
      col_9__fmt: '0.00_ ;[Red]\\-0.00\\'
    };
    expect(cellDisplay(row, 'col_9')).toBe('-0.00');
    expect(cellDisplay(row, 'col_9')).not.toMatch(/[eE]-?\d/);
    expect(cellFull(row, 'col_9')).toBe('-0.000000500000000069889');
  });

  it('文本/整数/空值原样返回', () => {
    expect(cellDisplay({ col_3: '张三' }, 'col_3')).toBe('张三');
    expect(cellDisplay({ col_3: '1001' }, 'col_3')).toBe('1001');
    expect(cellDisplay({ col_3: '' }, 'col_3')).toBe('');
    expect(cellDisplay({}, 'col_3')).toBe('');
  });
});

describe('cellFull —— 点击/悬停展示的完整值', () => {
  it('有 __raw 时给完整原值（无科学计数法）', () => {
    const row = { col_3: '-0.0', col_3__raw: '-0.000000500000000069889' };
    expect(cellFull(row, 'col_3')).toBe('-0.000000500000000069889');
    expect(cellFull(row, 'col_3')).not.toMatch(/[eE]-?\d/);
  });

  it('百分比：完整值取原始小数', () => {
    const row = { col_4: '54.5%', col_4__raw: '0.545175438596492' };
    expect(cellFull(row, 'col_4')).toBe('0.545175438596492');
  });

  it('无 __raw 时回退到显示值本身', () => {
    expect(cellFull({ col_3: '3.1779998' }, 'col_3')).toBe('3.1779998');
    expect(cellFull({}, 'col_3')).toBe('');
  });
});
