package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.auth.api.OrgGroupApi;
import com.bank.branch.platform.performance.api.BranchDashboardBatchQueryApi;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchRowDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardQualityDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.service.BranchDashboardBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 分行大屏不可变批次查询实现，隐藏 PERF_RUN_TASK 和 JSON 存储细节。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchDashboardBatchQueryApiImpl implements BranchDashboardBatchQueryApi {

    private final BranchDashboardBatchService batchService;
    private final OrgGroupApi orgGroupApi;

    @Override
    public Optional<BranchDashboardBatchDTO> latest(String groupCode,
                                                     Collection<String> currentAuthorizedOrgCodes) {
        if (!StringUtils.hasText(groupCode)) {
            return Optional.empty();
        }
        try {
            return batchService.findLatestSuccess(groupCode)
                    .flatMap(task -> visibleSnapshot(task, groupCode, currentAuthorizedOrgCodes))
                    .map(snapshot -> {
                        batchService.findLatestAttempt(groupCode).ifPresent(snapshot::setLatestAttempt);
                        return snapshot;
                    });
        } catch (RuntimeException ex) {
            log.warn("[BranchDashboardBatchQueryApi] latest fail-close groupCode={}, reason={}",
                    groupCode, ex.toString());
            return Optional.empty();
        }
    }

    @Override
    public Optional<BranchDashboardBatchDTO> byId(String batchId,
                                                   Collection<String> currentAuthorizedOrgCodes) {
        if (!StringUtils.hasText(batchId)) {
            return Optional.empty();
        }
        try {
            return batchService.findTask(batchId)
                    .flatMap(task -> visibleSnapshot(task, task.getTaskKey(), currentAuthorizedOrgCodes));
        } catch (RuntimeException ex) {
            log.warn("[BranchDashboardBatchQueryApi] byId fail-close batchId={}, reason={}",
                    batchId, ex.toString());
            return Optional.empty();
        }
    }

    private Optional<BranchDashboardBatchDTO> visibleSnapshot(PerfRunTask task, String requestedGroupCode,
                                                                Collection<String> authorizedCodes) {
        if (!BranchDashboardBatchService.TASK_TYPE.equals(task.getTaskType())
                || !"SUCCESS".equals(task.getStatus())
                || !StringUtils.hasText(requestedGroupCode)
                || !requestedGroupCode.equals(task.getTaskKey())) {
            return Optional.empty();
        }
        Set<String> authorized = normalize(authorizedCodes);
        if (authorized.isEmpty()) {
            return Optional.empty();
        }
        Set<String> liveMembers = normalize(orgGroupApi.listActiveMemberCodes(requestedGroupCode));
        if (liveMembers.isEmpty()) {
            return Optional.empty();
        }
        Optional<BranchDashboardBatchDTO> decoded = batchService.readSnapshot(task);
        if (decoded.isEmpty()) {
            return Optional.empty();
        }
        BranchDashboardBatchDTO snapshot = decoded.get();
        Set<String> snapshotMembers = normalize(snapshot.getMemberOrgCodes());
        Set<String> visibleMembers = new LinkedHashSet<>(snapshotMembers);
        visibleMembers.retainAll(liveMembers);
        visibleMembers.retainAll(authorized);

        boolean membershipChanged = !snapshotMembers.equals(liveMembers);
        boolean authorizationReduced = !authorized.containsAll(liveMembers)
                || !visibleMembers.equals(snapshotMembers);
        if (membershipChanged || authorizationReduced) {
            snapshot.setStatus("PARTIAL");
            BranchDashboardQualityDTO quality = snapshot.getQuality();
            if (quality == null) {
                quality = BranchDashboardQualityDTO.builder().build();
                snapshot.setQuality(quality);
            }
            List<String> missing = quality.getMissing() == null
                    ? new ArrayList<>() : new ArrayList<>(quality.getMissing());
            for (String member : snapshotMembers) {
                if (!liveMembers.contains(member)) {
                    missing.add("MEMBER_REMOVED:" + member);
                }
            }
            for (String member : liveMembers) {
                if (!snapshotMembers.contains(member)) {
                    missing.add("MEMBER_MISSING_FROM_BATCH:" + member);
                }
            }
            quality.setMissing(missing.stream().distinct().toList());
            int expectedPerSubject = quality.getExpectedSubjects() > 0
                    ? quality.getExpected() / quality.getExpectedSubjects() : 0;
            if (expectedPerSubject > 0) {
                quality.setExpected(expectedPerSubject * liveMembers.size());
                quality.setReceived(expectedPerSubject * visibleMembers.size());
            }
            quality.setExpectedSubjects(liveMembers.size());
            quality.setReceivedSubjects(visibleMembers.size());
        }

        snapshot.setMemberOrgCodes(visibleMembers.stream().sorted().toList());
        snapshot.setRows(filterRows(snapshot.getRows(), visibleMembers));
        snapshot.setHistoryRows(filterRows(snapshot.getHistoryRows(), visibleMembers));
        return Optional.of(snapshot);
    }

    private List<BranchDashboardBatchRowDTO> filterRows(List<BranchDashboardBatchRowDTO> rows,
                                                          Set<String> visibleMembers) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> row != null && visibleMembers.contains(row.getOrgCode()))
                .sorted(Comparator.comparing(BranchDashboardBatchRowDTO::getDataDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(BranchDashboardBatchRowDTO::getOrgCode,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private Set<String> normalize(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
