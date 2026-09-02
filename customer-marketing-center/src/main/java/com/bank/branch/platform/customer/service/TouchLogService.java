package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.resp.TouchWorklogVO;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.entity.TouchWorklog;
import com.bank.branch.platform.customer.entity.TouchWorklogParticipant;
import com.bank.branch.platform.customer.entity.TouchWorklogPictureRecord;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.customer.mapper.TouchWorklogMapper;
import com.bank.branch.platform.customer.mapper.TouchWorklogParticipantMapper;
import com.bank.branch.platform.customer.mapper.TouchWorklogPictureRecordMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** MARKETING_TOUCH_WORKLOG 工作日志服务。 */
@Service
@RequiredArgsConstructor
public class TouchLogService {
    private static final TypeReference<Map<String, List<String>>> PHOTO_GROUP_TYPE = new TypeReference<>() { };

    private final TouchTaskMapper taskMapper;
    private final TouchWorklogMapper worklogMapper;
    private final TouchWorklogPictureRecordMapper pictureMapper;
    private final TouchWorklogParticipantMapper participantMapper;
    private final MarketingCustomerInfoMapper customerMapper;
    private final ObjectMapper objectMapper;

    /** 创建工作日志；相同 taskId/clientUuid 返回原记录。 */
    @Transactional
    public TouchWorklogVO addLog(String touchTaskId, String clientUuid, String logContent,
                                 String photoUrlsJson, LocalDateTime touchTime, String touchMethod,
                                 String participantEmpIdsJson, String photoGroupsJson, String operatorLocation,
                                 String operatorEmpId, String operatorOrgId, boolean operatorIsAdmin) {
        Long taskId = parseId(touchTaskId);
        TouchTask task = taskMapper.selectByIdForUpdate(touchTaskId);
        if (task == null) {
            throw error(CustomerErrorCode.TOUCH_TASK_NOT_FOUND);
        }
        if (!operatorEmpId.equals(task.getAssigneeEmpId())) {
            throw error(CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN);
        }
        if (!List.of("PENDING", "IN_PROGRESS").contains(task.getTaskStatus())) {
            throw error(CustomerErrorCode.TOUCH_TASK_NOT_PENDING);
        }

        TouchWorklog existing = worklogMapper.selectByTaskAndClientUuid(taskId, clientUuid);
        if (existing != null) {
            return toVO(existing);
        }

        MarketingCustomerInfo customer = customerMapper.selectActiveById(task.getCustId());
        if (customer == null) {
            throw error(CustomerErrorCode.CUSTOMER_NOT_FOUND);
        }

        Map<String, List<String>> photoGroups = parsePhotoGroups(photoGroupsJson, photoUrlsJson);
        validatePhotos(photoGroups);
        List<String> participants = parseStringList(participantEmpIdsJson);
        LocalDateTime now = LocalDateTime.now();

        TouchWorklog worklog = new TouchWorklog();
        worklog.setWorklogNo("MWL" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        worklog.setTaskId(taskId);
        worklog.setCustId(task.getCustId());
        worklog.setCustomerNameSnapshot(customer.getCustName());
        worklog.setUnifiedCreditCodeSnapshot(customer.getUnifiedCreditCode());
        worklog.setOperatorEmpId(operatorEmpId);
        worklog.setOperatorOrgId(operatorOrgId);
        worklog.setTouchTime(touchTime == null ? now : touchTime);
        worklog.setTouchMethod(normalizeTouchMethod(touchMethod));
        worklog.setTouchPoints(logContent);
        worklog.setIsFirstTouch("FIRST_TOUCH".equals(task.getTaskType())
                && worklogMapper.countValidByTaskId(taskId) == 0 ? 1 : 0);
        applyLocation(worklog, operatorLocation);
        worklog.setClientUuid(clientUuid);
        worklog.setRecordStatus("VALID");
        worklog.setCreatedTime(now);
        worklog.setUpdatedTime(now);

        try {
            worklogMapper.insert(worklog);
            insertPictures(worklog.getId(), photoGroups, operatorEmpId, now);
            insertParticipants(worklog.getId(), participants, operatorEmpId, operatorOrgId, now);
            taskMapper.markInProgressIfPending(taskId, operatorEmpId, now);
            return toVO(worklog);
        } catch (DuplicateKeyException duplicate) {
            TouchWorklog duplicated = worklogMapper.selectByTaskAndClientUuid(taskId, clientUuid);
            if (duplicated != null) {
                return toVO(duplicated);
            }
            throw duplicate;
        }
    }

    /** 查询任务全部有效工作日志。 */
    public List<TouchWorklogVO> listByTaskId(String touchTaskId) {
        Long taskId = parseId(touchTaskId);
        return worklogMapper.selectValidByTaskId(taskId).stream().map(this::toVO).toList();
    }

    /** 同机构或管理员可查看任务日志。 */
    public List<TouchWorklogVO> listVisibleByTaskId(String touchTaskId, String operatorOrgCode,
                                                    boolean operatorIsAdmin) {
        TouchTask task = taskMapper.selectById(touchTaskId);
        if (task == null) {
            throw error(CustomerErrorCode.TOUCH_TASK_NOT_FOUND);
        }
        if (!operatorIsAdmin && !task.getOrgId().equals(operatorOrgCode)) {
            throw error(CustomerErrorCode.HISTORY_ACCESS_FORBIDDEN);
        }
        return listByTaskId(touchTaskId);
    }

    private void insertPictures(Long worklogId, Map<String, List<String>> groups,
                                String operator, LocalDateTime now) {
        Map<String, String> types = Map.of(
                "keyPerson", "KEY_PERSON", "doorplate", "COMPANY_SIGN", "workplace", "BUSINESS_SITE");
        groups.forEach((group, files) -> {
            String type = types.get(group);
            if (type == null || files == null) return;
            for (int i = 0; i < files.size(); i++) {
                TouchWorklogPictureRecord row = new TouchWorklogPictureRecord();
                row.setWorklogId(worklogId);
                row.setPictureType(type);
                row.setFileObjectId(files.get(i));
                row.setSortNo(i + 1);
                row.setCreatedBy(operator);
                row.setCreatedTime(now);
                row.setRecordStatus("ACTIVE");
                pictureMapper.insert(row);
            }
        });
    }

    private void insertParticipants(Long worklogId, List<String> participants, String operator,
                                    String orgId, LocalDateTime now) {
        LinkedHashSet<String> employees = new LinkedHashSet<>();
        employees.add(operator);
        if (participants != null) employees.addAll(participants);
        for (String empId : employees) {
            if (!StringUtils.hasText(empId)) continue;
            TouchWorklogParticipant row = new TouchWorklogParticipant();
            row.setWorklogId(worklogId);
            row.setParticipantEmpId(empId);
            row.setParticipantOrgId(orgId);
            row.setParticipantRole(operator.equals(empId) ? "OPERATOR" : "COLLABORATOR");
            row.setCreatedBy(operator);
            row.setCreatedTime(now);
            participantMapper.insert(row);
        }
    }

    private TouchWorklogVO toVO(TouchWorklog row) {
        TouchWorklogVO vo = new TouchWorklogVO();
        String id = row.getId() == null ? null : String.valueOf(row.getId());
        vo.setId(id);
        vo.setWorkLogId(id);
        vo.setWorklogNo(row.getWorklogNo());
        vo.setTouchTaskId(row.getTaskId() == null ? null : String.valueOf(row.getTaskId()));
        vo.setClientUuid(row.getClientUuid());
        vo.setLogTime(row.getTouchTime());
        vo.setLogContent(row.getTouchPoints());
        vo.setTouchMethod(row.getTouchMethod());
        vo.setOwnerOrgId(row.getOperatorOrgId());
        vo.setCreatedBy(row.getOperatorEmpId());
        vo.setCreatedTime(row.getCreatedTime());
        vo.setCompanyName(row.getCustomerNameSnapshot());
        vo.setCompanyUSCI(row.getUnifiedCreditCodeSnapshot());
        vo.setAccountOpenProgress(row.getAccountOpenProgress());
        vo.setRecordStatus(row.getRecordStatus());

        List<TouchWorklogParticipant> participantRows = participantMapper.selectByWorklogId(row.getId());
        vo.setParticipantEmpIds(participantRows.stream()
                .filter(item -> !"OPERATOR".equals(item.getParticipantRole()))
                .map(TouchWorklogParticipant::getParticipantEmpId).toList());
        Map<String, List<String>> groups = emptyGroups();
        for (TouchWorklogPictureRecord picture : pictureMapper.selectActiveByWorklogId(row.getId())) {
            String group = switch (picture.getPictureType()) {
                case "KEY_PERSON" -> "keyPerson";
                case "COMPANY_SIGN" -> "doorplate";
                case "BUSINESS_SITE" -> "workplace";
                default -> null;
            };
            if (group != null) groups.get(group).add(picture.getFileObjectId());
        }
        vo.setPhotoGroups(groups);
        vo.setPhotoUrls(groups.values().stream().flatMap(List::stream).toList());
        if (StringUtils.hasText(row.getLocationAddress())) {
            vo.setOperatorLocation(toJson(Map.of("address", row.getLocationAddress())));
        }
        return vo;
    }

    private Map<String, List<String>> parsePhotoGroups(String groupsJson, String urlsJson) {
        try {
            if (StringUtils.hasText(groupsJson)) {
                Map<String, List<String>> parsed = objectMapper.readValue(groupsJson, PHOTO_GROUP_TYPE);
                Map<String, List<String>> result = emptyGroups();
                result.keySet().forEach(key -> result.put(key,
                        parsed.get(key) == null ? new ArrayList<>() : new ArrayList<>(parsed.get(key))));
                return result;
            }
        } catch (Exception ignored) {
            // 统一由 validatePhotos 转为业务错误。
        }
        Map<String, List<String>> result = emptyGroups();
        result.get("workplace").addAll(parseStringList(urlsJson));
        return result;
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void validatePhotos(Map<String, List<String>> groups) {
        int total = groups.values().stream().mapToInt(List::size).sum();
        if (total == 0) throw error(CustomerErrorCode.TOUCH_LOG_PHOTO_REQUIRED);
        if (total > 9 || groups.values().stream().anyMatch(files -> files.size() > 3)) {
            throw error(CustomerErrorCode.TOUCH_LOG_PHOTO_LIMIT_EXCEEDED);
        }
        boolean invalid = groups.values().stream().flatMap(List::stream)
                .anyMatch(file -> !file.toLowerCase().matches(".*\\.(jpg|jpeg|png|heic)(\\?.*)?$"));
        if (invalid) throw error(CustomerErrorCode.TOUCH_LOG_PHOTO_FORMAT_INVALID);
    }

    private void applyLocation(TouchWorklog worklog, String locationJson) {
        if (!StringUtils.hasText(locationJson)) {
            worklog.setLocationStatus("NOT_PROVIDED");
            return;
        }
        worklog.setLocationStatus("CHECKED");
        worklog.setLocationTime(LocalDateTime.now());
        try {
            JsonNode node = objectMapper.readTree(locationJson);
            worklog.setLocationAddress(text(node, "address", locationJson));
            worklog.setLocationCityArea(text(node, "cityArea", null));
            worklog.setLocationRemark(text(node, "remark", null));
        } catch (Exception ignored) {
            worklog.setLocationAddress(locationJson);
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private String normalizeTouchMethod(String method) {
        return "VISIT".equalsIgnoreCase(method) ? "ONSITE" : method.toUpperCase();
    }

    private Map<String, List<String>> emptyGroups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("keyPerson", new ArrayList<>());
        groups.put("doorplate", new ArrayList<>());
        groups.put("workplace", new ArrayList<>());
        return groups;
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return null; }
    }

    private Long parseId(String id) {
        try { return Long.valueOf(id); }
        catch (Exception e) { throw error(CustomerErrorCode.TOUCH_TASK_NOT_FOUND); }
    }

    private BizException error(CustomerErrorCode code) {
        return new BizException(code.getCode(), code.getMessage());
    }
}
