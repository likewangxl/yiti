# 线索导入 XLSX 模板验收

## 验收结论

- 线索录入页面“批量导入”弹窗的下载地址已切换为 `/templates/lead-import-template.xlsx`。
- 官方 `playwright-cli` 下载得到 `lead-import-template.xlsx`，与仓库静态模板 SHA-256 一致。
- 下载文件首个工作表为“线索导入模板”，首行共 18 列，字段及顺序与录入线索抽屉一致。
- Apache POI `WorkbookFactory` 可直接读取工作簿，未产生工作簿结构警告。
- 浏览器会话未注册 mock route；console 无 error，有 2 条与本需求无关的既有路由 warning。

## 文件一致性

```text
543aaa1cc88521ad84e594560805dd6b19e22c0787cb9af76326167356664b9b  public/templates/lead-import-template.xlsx
543aaa1cc88521ad84e594560805dd6b19e22c0787cb9af76326167356664b9b  .playwright-cli/lead-import-template.xlsx
```

截图见 `import-dialog-xlsx.png`。
