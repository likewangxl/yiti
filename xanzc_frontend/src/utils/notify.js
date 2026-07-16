// 通知相关工具。
// parseApprovalNode：从通知正文里解析「当前审批环节：XXX。」中的 XXX（取全角冒号与全角句号之间的内容）；
// 解析不到（普通通知/空）返回空串。用于通知详情页把审批环节单独成行展示。
export function parseApprovalNode(content) {
  if (!content) return '';
  const m = String(content).match(/当前审批环节：([^。]+)。/);
  return m ? m[1].trim() : '';
}
