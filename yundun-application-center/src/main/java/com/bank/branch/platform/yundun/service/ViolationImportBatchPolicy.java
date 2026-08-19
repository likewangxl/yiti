package com.bank.branch.platform.yundun.service;

/** 云盾 Excel 导入的共享总量与单条 SQL 批次限制。 */
final class ViolationImportBatchPolicy {

    static final int IMPORT_LIMIT = 3000;
    static final int INSERT_BATCH_SIZE = 500;

    private ViolationImportBatchPolicy() {
    }
}
