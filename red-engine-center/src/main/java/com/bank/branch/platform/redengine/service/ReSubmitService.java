package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.redengine.api.dto.ReSubmitCreateReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowActionRespDTO;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.entity.ReSubmitFile;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.mapper.ReSubmitFileMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 红色引擎-材料上报服务。
 * <p>移植自 redengine {@code BizSubmitServiceImpl}（{@code business.service.impl}）。
 * 附件不再由本模块自建文件存储端点，改依赖 governance {@code FileApi}：前端先经既有
 * {@code POST /api/files/upload} 直传拿到 fileObjectId，再随创建请求提交，本服务逐个写
 * {@code RE_SUBMIT_FILE} 落业务侧文件元数据，并调用 {@link FileApi#bindFile} 建立
 * "文件-业务对象"关联登记（供 governance 侧统一追踪文件归属）。</p>
 * <p><b>status 语义变更</b>（相对源系统）：源 {@code createSubmit} 无论是否携带附件均硬编码
 * {@code status=0}（草稿），但源前端 {@code JointView} 实际把"创建"直接当"已提交"处理，
 * 草稿态入口从未被使用。本次移植明确废弃草稿语义（YAGNI）：创建即 {@code status=1} 已提交，
 * 不再提供"先存草稿、后提交"的两段式接口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReSubmitService {

    private final ReSubmitMapper reSubmitMapper;
    private final ReSubmitFileMapper reSubmitFileMapper;
    private final ReUserPartyMapService reUserPartyMapService;
    private final FileApi fileApi;
    private final ReTaskFourDimensionAdapter fourDimensionAdapter;
    private final ReTaskWorkflowService taskWorkflowService;

    /**
     * 创建材料上报（含附件绑定）。
     * <p>orgId 不由前端传入，而是依据当前登录人 userId（PT_USER.USER_ID）通过
     * {@link ReUserPartyMapService#getRequiredPartyOrgId} 解析当前用户归属的党组织，
     * 防止越权把上报记录挂到别的党组织名下；未绑定党组织时该调用直接抛 RE-40001，
     * 本方法不做兜底吞掉，事务连同已执行的写操作一并回滚。</p>
     *
     * @param req   创建请求（考核维度/项目/日期等表单字段 + 附件 fileObjectId 列表）
     * @param userId 当前登录人平台用户ID（提交人）
     * @return 新建上报记录ID
     * @throws com.bank.branch.platform.common.web.exception.BizException code=RE-40001，当前用户未绑定党组织
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createSubmit(ReSubmitCreateReqDTO req, String userId) {
        Long orgId = reUserPartyMapService.getRequiredPartyOrgId(userId);

        ReSubmit submit = new ReSubmit();
        submit.setOrgId(orgId);
        submit.setSubmitterId(userId);
        submit.setDimension(req.getDimension());
        submit.setItemCode(req.getItemCode());
        submit.setItemName(req.getItemName());
        submit.setMaxScore(req.getMaxScore());
        submit.setProjectName(req.getProjectName());
        submit.setSubmitType(req.getSubmitType());
        submit.setSubmitDate(req.getSubmitDate());
        submit.setFormData(req.getFormData());
        // 创建即已提交，草稿态(status=0)语义废弃(YAGNI)，详见类注释
        submit.setStatus(1);
        reSubmitMapper.insert(submit);

        List<String> fileObjectIds = req.getFileObjectIds();
        if (fileObjectIds != null) {
            for (String fileObjectId : fileObjectIds) {
                ReSubmitFile file = new ReSubmitFile();
                file.setSubmitId(submit.getId());
                file.setFileObjectId(fileObjectId);
                file.setFileName(fileApi.getFileName(fileObjectId));
                reSubmitFileMapper.insert(file);
                // 登记"文件-业务对象"关联，bizId 按 FileApi 契约传字符串化的 submitId
                fileApi.bindFile("RE_SUBMIT", String.valueOf(submit.getId()), fileObjectId, "ATTACHMENT");
            }
        }

        // 只有带完整任务上下文的四维请求才进入任务工作流；旧客户端不带字段时保持原行为。
        if (req.getTaskId() != null && req.getTaskInstanceId() != null
                && req.getTaskAssignmentId() != null) {
            submitInTaskWorkflow(req, submit, userId);
        }

        log.info("[ReSubmitService.createSubmit] id={}, orgId={}, submitterId={}, fileCount={}",
                submit.getId(), orgId, userId, fileObjectIds == null ? 0 : fileObjectIds.size());
        return submit.getId();
    }

    /**
     * 将四维旧材料上报接入任务域。
     *
     * <p>同一任务窗口允许连续上报多个维度/材料。首个材料或驳回后的首次重提创建新的任务提交
     * 版本；当前版本仍处于支部待审时，只复用该版本并追加桥接关系，避免每个材料都触发一次状态
     * 流转。方法由外层 {@code createSubmit} 事务调用，旧记录、任务版本、桥接和进度因此原子提交。</p>
     */
    private void submitInTaskWorkflow(ReSubmitCreateReqDTO request, ReSubmit legacySubmission,
                                      String operatorId) {
        if (fourDimensionAdapter == null || taskWorkflowService == null) {
            throw new com.bank.branch.platform.common.web.exception.BizException(
                    "RE-50011", "四维任务服务未就绪");
        }

        // 复用当前 BRANCH_PENDING 版本时也必须经过任务域实体级访问校验，不能只依赖
        // assignment/task 三个客户端字段；首次提交同样由 workflow 服务检查当前处理人。
        taskWorkflowService.getAssignment(request.getTaskAssignmentId(), operatorId);
        ReTaskSubmission current = fourDimensionAdapter.findCurrentSubmission(
                request.getTaskId(), request.getTaskInstanceId(), request.getTaskAssignmentId());
        if (current != null && current.getStatus() == ReTaskSubmissionStatus.BRANCH_PENDING) {
            fourDimensionAdapter.linkExistingSubmissionToVersion(
                    legacySubmission, legacySubmission.getId(), request.getTaskId(),
                    request.getTaskInstanceId(), request.getTaskAssignmentId(), current.getId(),
                    request.getDimension(), request.getItemCode(), operatorId);
            return;
        }

        ReTaskWorkflowActionRespDTO workflowResult = taskWorkflowService.submit(
                toTaskSubmissionRequest(request, legacySubmission.getId()), operatorId);
        if (workflowResult == null || workflowResult.getSubmissionId() == null) {
            throw new com.bank.branch.platform.common.web.exception.BizException(
                    "RE-50012", "任务提交保存失败");
        }
        if (workflowResult.getAssignmentId() != null
                && !Objects.equals(workflowResult.getAssignmentId(), request.getTaskAssignmentId())) {
            throw new com.bank.branch.platform.common.web.exception.BizException(
                    "RE-40906", "任务提交分配与材料上报不匹配");
        }
        // workflow.submit 已调用一次 recordTaskUpload；此处只建桥，不能再次推进四维进度。
        fourDimensionAdapter.linkFromLegacyRequest(legacySubmission, legacySubmission.getId(),
                request.getTaskId(), request.getTaskInstanceId(), request.getTaskAssignmentId(),
                workflowResult.getSubmissionId(), operatorId);
    }

    /** 把旧材料字段转换为任务提交请求；任务域以 assignment 关联反查任务和实例。 */
    private ReTaskSubmissionReqDTO toTaskSubmissionRequest(ReSubmitCreateReqDTO request,
                                                            Long legacySubmissionId) {
        ReTaskSubmissionReqDTO taskRequest = new ReTaskSubmissionReqDTO();
        taskRequest.setAssignmentId(request.getTaskAssignmentId());
        taskRequest.setContent(null);
        taskRequest.setFormData(request.getFormData());
        taskRequest.setFileObjectIds(request.getFileObjectIds());
        taskRequest.setClientRequestId("RE_SUBMIT:" + legacySubmissionId);
        taskRequest.setDimensionCode(request.getDimension());
        taskRequest.setItemCode(request.getItemCode());
        taskRequest.setItemName(request.getItemName());
        taskRequest.setMaxScore(request.getMaxScore());
        taskRequest.setProjectName(request.getProjectName());
        taskRequest.setSubmitType(request.getSubmitType());
        taskRequest.setSubmitDate(request.getSubmitDate());
        return taskRequest;
    }

    /**
     * 查询"我的上报"分页列表。
     * <p>按当前登录人映射的党组织(orgId)过滤——与源系统一致，语义是"本党组织的上报记录"，
     * 而非"本人提交的记录"，供支部内报送员/书记/审核员共同查看同一党组织的上报进度。</p>
     *
     * @param userId   当前登录人平台用户ID（PT_USER.USER_ID）
     * @param pageNo   页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页结果
     * @throws com.bank.branch.platform.common.web.exception.BizException code=RE-40001，当前用户未绑定党组织
     */
    public PageResult<ReSubmit> getMySubmits(String userId, int pageNo, int pageSize) {
        Long orgId = reUserPartyMapService.getRequiredPartyOrgId(userId);

        LambdaQueryWrapper<ReSubmit> wrapper = new LambdaQueryWrapper<ReSubmit>()
                .eq(ReSubmit::getOrgId, orgId)
                .orderByDesc(ReSubmit::getCreateTime);
        IPage<ReSubmit> page = reSubmitMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(pageNo, pageSize, page.getTotal(), page.getRecords());
    }

    /**
     * 查询上报详情，并按当前用户映射的党组织执行实体级数据范围校验。
     *
     * <p>详情接口不能只依赖前端菜单或列表过滤；即使调用者拿到了其他党组织的 ID，
     * 也必须在服务层拒绝。党组织映射缺失继续由 {@link ReUserPartyMapService} 以
     * RE-40001 fail-close。</p>
     *
     * @param id 上报ID
     * @param userId 当前登录人平台用户ID
     * @return 当前党组织内的上报实体；不存在时返回 null
     */
    public ReSubmit getDetail(Long id, String userId) {
        ReSubmit submit = reSubmitMapper.selectById(id);
        if (submit == null) {
            return null;
        }
        Long currentOrgId = reUserPartyMapService.getRequiredPartyOrgId(userId);
        if (!Objects.equals(currentOrgId, submit.getOrgId())) {
            throw new com.bank.branch.platform.common.web.exception.BizException(
                    "RE-40304", "无权查看该上报记录");
        }
        return submit;
    }
}
