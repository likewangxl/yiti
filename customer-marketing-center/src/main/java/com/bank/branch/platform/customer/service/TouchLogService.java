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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    /**
     * 创建工作日志；相同 taskId/clientUuid 返回原记录。
     *
     * <p>任务完成后仍允许任务执行人或由主执行人在既有日志中登记的协同参与人补录，
     * 但取消任务永远不可写。任务行锁确保状态判断、幂等查询和日志插入处于同一串行化
     * 边界；补录 SUCCESS 日志不会再触发状态迁移。</p>
     */
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
        // 取消任务是不可写终态，即使调用者是管理员也不能代录或补录。
        if ("CANCELLED".equals(task.getTaskStatus())) {
            throw error(CustomerErrorCode.TOUCH_TASK_NOT_PENDING);
        }
        // 管理员身份不扩大日志写入边界：仍须是执行人或已登记协同参与人。
        if (!canWriteLog(task, operatorEmpId, operatorOrgId)) {
            throw error(CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN);
        }
        if (!List.of("PENDING", "IN_PROGRESS", "SUCCESS").contains(task.getTaskStatus())) {
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
        worklog.setWorklogNo("MWL" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
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
            // 只有首条日志驱动 PENDING → IN_PROGRESS；SUCCESS 补录不能改变终态。
            if ("PENDING".equals(task.getTaskStatus())) {
                taskMapper.markInProgressIfPending(taskId, operatorEmpId, now);
            }
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
                // 前端使用平台下载地址展示照片，但数据库只保存真实文件对象 ID，避免把
                // 文件名 fragment 或下载路径挤进 VARCHAR(64) 并导致后续下载失效。
                row.setFileObjectId(toStoredFileObjectId(files.get(i)));
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
            if (group != null) groups.get(group).add(toPhotoUrl(picture.getFileObjectId()));
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
                .anyMatch(file -> !isImageReference(file));
        if (invalid) throw error(CustomerErrorCode.TOUCH_LOG_PHOTO_FORMAT_INVALID);
    }

    /**
     * 判断照片引用的扩展名。平台下载地址的文件名通常位于 URL fragment（#xxx.jpg），
     * 同时兼容旧客户端把扩展名放在路径或查询串中的形式；非图片扩展名仍然拒绝。
     */
    private boolean isImageReference(String reference) {
        if (!StringUtils.hasText(reference)) return false;
        String value = reference.toLowerCase();
        int hash = value.indexOf('#');
        String fragment = hash >= 0 ? value.substring(hash + 1) : "";
        String beforeFragment = hash >= 0 ? value.substring(0, hash) : value;
        String candidate = stripQuery(fragment);
        if (candidate == null || candidate.isBlank()) candidate = stripQuery(beforeFragment);
        return hasImageSuffix(candidate);
    }

    private boolean hasImageSuffix(String value) {
        return value != null && value.matches(".*\\.(jpg|jpeg|png|heic)$");
    }

    private String stripQuery(String value) {
        if (value == null) return null;
        int query = value.indexOf('?');
        return query >= 0 ? value.substring(0, query) : value;
    }

    /**
     * 将平台下载地址还原为数据库中的文件对象 ID；非平台 URL 维持兼容原值。
     */
    private String toStoredFileObjectId(String reference) {
        if (!StringUtils.hasText(reference)) return reference;
        String marker = "/api/files/";
        int markerIndex = reference.indexOf(marker);
        if (markerIndex < 0) return reference;
        int idStart = markerIndex + marker.length();
        int downloadIndex = reference.indexOf("/download", idStart);
        if (downloadIndex <= idStart) return reference;
        String suffix = reference.substring(downloadIndex + "/download".length());
        if (!suffix.isEmpty() && suffix.charAt(0) != '?' && suffix.charAt(0) != '#') return reference;
        return decodePathSegment(reference.substring(idStart, downloadIndex));
    }

    /**
     * 将数据库中的文件对象 ID转换成前端可直接展示的下载地址；已有完整 URL 保持不变。
     */
    private String toPhotoUrl(String fileObjectId) {
        if (!StringUtils.hasText(fileObjectId)) return fileObjectId;
        if (fileObjectId.startsWith("/api/files/")
                || fileObjectId.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            return fileObjectId;
        }
        return "/api/files/" + encodePathSegment(fileObjectId) + "/download";
    }

    private String decodePathSegment(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 日志写入者只能是任务执行人，或由任务执行人在有效历史日志中登记的协同参与人。
     * 管理员参数故意不参与判断，防止管理查看权限意外变成代录权限。
     */
    private boolean canWriteLog(TouchTask task, String operatorEmpId, String operatorOrgId) {
        if (!StringUtils.hasText(operatorEmpId) || !StringUtils.hasText(operatorOrgId)
                || !Objects.equals(task.getOrgId(), operatorOrgId)) return false;
        if (Objects.equals(operatorEmpId, task.getAssigneeEmpId())) return true;
        if (task.getId() == null) return false;

        List<TouchWorklog> assigneeLogs = worklogMapper.selectValidByTaskId(task.getId());
        if (assigneeLogs == null) return false;
        for (TouchWorklog log : assigneeLogs) {
            // 只认可主执行人登记的协同人，避免协同人员通过后续日志自助扩大写入范围。
            if (log == null || !Objects.equals(task.getAssigneeEmpId(), log.getOperatorEmpId())
                    || log.getId() == null) {
                continue;
            }
            List<TouchWorklogParticipant> participants = participantMapper.selectByWorklogId(log.getId());
            if (participants == null) continue;
            boolean registered = participants.stream().anyMatch(participant ->
                    participant != null
                            && Objects.equals(operatorEmpId, participant.getParticipantEmpId())
                            && "COLLABORATOR".equals(participant.getParticipantRole())
                            && (participant.getParticipantOrgId() == null
                            || Objects.equals(task.getOrgId(), participant.getParticipantOrgId())));
            if (registered) return true;
        }
        return false;
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
