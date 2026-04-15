# 2026-04-14 workflow-center 真实环境 curl 联调手册

> 适用环境：Windows PowerShell + `curl.exe` + 本机 MySQL/Redis
> 目标：在真实启动服务后，用 `curl` 跑通流程提交、转交、审批、撤回

## 1. 环境准备

先对齐数据库：

```powershell
Get-Content -Raw -Encoding UTF8 docs\superpowers\sql\2026-04-14-workflow-real-env-align.sql | & mysql -uroot -p123456 onepl
```

然后启动服务：

```powershell
cd bootstrap
mvn spring-boot:run
```

看到类似下面日志再继续：

```text
Tomcat started on port 8080
Started BranchPlatformApplication
```

## 2. 登录 + curl 辅助函数

新开一个 PowerShell 窗口，在项目根目录执行：

```powershell
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$work = '.omx\logs\curl-real-env'
New-Item -ItemType Directory -Force -Path $work | Out-Null

$rmCookie       = Join-Path $work 'rm.cookies.txt'
$branchCookie   = Join-Path $work 'branch.cookies.txt'
$corpCookie     = Join-Path $work 'corp.cookies.txt'
$reviewerCookie = Join-Path $work 'reviewer.cookies.txt'
$approverCookie = Join-Path $work 'approver.cookies.txt'

Remove-Item $rmCookie,$branchCookie,$corpCookie,$reviewerCookie,$approverCookie -Force -ErrorAction SilentlyContinue

function Invoke-CurlJson {
    param(
        [string]$Method,
        [string]$Url,
        [string]$CookieFile,
        [string]$Body = ''
    )

    $args = @('-sS', '-X', $Method, '-b', $CookieFile, '-c', $CookieFile)
    $tmpBody = $null

    if ($Body -ne '') {
        $tmpBody = Join-Path $work ([guid]::NewGuid().ToString('N') + '.json')
        Set-Content -Path $tmpBody -Value $Body -Encoding UTF8
        $args += @('-H', 'Content-Type: application/json', '--data-binary', ('@' + $tmpBody))
    }

    $args += $Url
    $raw = & curl.exe @args

    if ($tmpBody -and (Test-Path $tmpBody)) {
        Remove-Item $tmpBody -Force
    }

    if ($LASTEXITCODE -ne 0) {
        throw "curl failed: $Method $Url"
    }

    return $raw | ConvertFrom-Json
}

function Login {
    param([string]$Username, [string]$CookieFile)

    $body = (@{
        username = $Username
        password = 'password'
    } | ConvertTo-Json -Compress)

    $resp = Invoke-CurlJson -Method 'POST' -Url "$base/api/auth/login" -CookieFile $CookieFile -Body $body
    if ($resp.code -ne '0') {
        throw "login failed for ${Username}: $($resp | ConvertTo-Json -Compress)"
    }
}

function Assert-Code {
    param([object]$Resp, [string]$Expected, [string]$Label)

    if ($Resp.code -ne $Expected) {
        throw "${Label} expected code ${Expected} but got $($Resp.code): $($Resp | ConvertTo-Json -Compress -Depth 6)"
    }
}

Login -Username 'rm_zhang' -CookieFile $rmCookie
Login -Username 'branch_wang' -CookieFile $branchCookie
Login -Username 'corp_zhao' -CookieFile $corpCookie
Login -Username 'reviewer_chen' -CookieFile $reviewerCookie
Login -Username 'approver_he' -CookieFile $approverCookie
```

## 3. 发起流程 → 转交 → 审批通过整条链

```powershell
$passBizId = 'CURL' + [guid]::NewGuid().ToString('N').Substring(0,8)

$submitBody = (@{
    bizType = 'LOAN'
    bizId = $passBizId
    businessKey = "LOAN:$passBizId"
    processDefinitionKey = 'loan_approve_v1'
    title = 'curl-真实环境提交审批链'
    variables = @{
        bizId = $passBizId
        title = 'curl-真实环境提交审批链'
    }
} | ConvertTo-Json -Compress)

# 1) 客户经理发起流程
$submitResp = Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/processes/submit" -CookieFile $rmCookie -Body $submitBody
Assert-Code -Resp $submitResp -Expected '0' -Label 'submit-process'

$passPid = $submitResp.data.processInstanceId
$branchTaskId = $submitResp.data.firstTaskId

# 2) 经营机构负责人签收
Assert-Code -Resp (Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/tasks/$branchTaskId/claim" -CookieFile $branchCookie) -Expected '0' -Label 'branch-claim'

# 3) 经营机构负责人转交给客户经理
$transferBody = (@{
    targetEmpId = 'E10001'
    reason = 'curl 转发给客户经理补录材料'
} | ConvertTo-Json -Compress)

Assert-Code -Resp (Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/tasks/$branchTaskId/transfer" -CookieFile $branchCookie -Body $transferBody) -Expected '0' -Label 'branch-transfer'

# 4) 客户经理查看自己待办并审批
$rmTodoResp = Invoke-CurlJson -Method 'GET' -Url "$base/api/workflow/tasks" -CookieFile $rmCookie
Assert-Code -Resp $rmTodoResp -Expected '0' -Label 'rm-todo'

$rmTaskId = $rmTodoResp.page.records[0].taskId

$approveBranchBody = (@{
    opinion = 'curl 客户经理补录后提交通过'
    formData = @{
        amount = 1000000
        needCreditMeeting = 'NO'
    }
} | ConvertTo-Json -Compress)

Assert-Code -Resp (Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/tasks/$rmTaskId/approve" -CookieFile $rmCookie -Body $approveBranchBody) -Expected '0' -Label 'rm-approve'

# 5) 公司部查询待办、签收、审批
$corpTodoResp = Invoke-CurlJson -Method 'GET' -Url "$base/api/workflow/tasks" -CookieFile $corpCookie
Assert-Code -Resp $corpTodoResp -Expected '0' -Label 'corp-todo'
$corpTaskId = $corpTodoResp.page.records[0].taskId

Assert-Code -Resp (Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/tasks/$corpTaskId/claim" -CookieFile $corpCookie) -Expected '0' -Label 'corp-claim'

$corpApproveBody = (@{
    opinion = 'curl 公司部同意'
    formData = @{
        corpOpinion = '同意'
    }
} | ConvertTo-Json -Compress)

Assert-Code -Resp (Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/tasks/$corpTaskId/approve" -CookieFile $corpCookie -Body $corpApproveBody) -Expected '0' -Label 'corp-approve'

# 6) 授信审查查询待办、签收、审批
$reviewTodoResp = Invoke-CurlJson -Method 'GET' -Url "$base/api/workflow/tasks" -CookieFile $reviewerCookie
Assert-Code -Resp $reviewTodoResp -Expected '0' -Label 'review-todo'
$reviewTaskId = $reviewTodoResp.page.records[0].taskId

Assert-Code -Resp (Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/tasks/$reviewTaskId/claim" -CookieFile $reviewerCookie) -Expected '0' -Label 'review-claim'

$reviewApproveBody = (@{
    opinion = 'curl 授信审查通过'
    formData = @{
        reviewOpinion = '通过'
    }
} | ConvertTo-Json -Compress)

Assert-Code -Resp (Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/tasks/$reviewTaskId/approve" -CookieFile $reviewerCookie -Body $reviewApproveBody) -Expected '0' -Label 'review-approve'

# 7) 授信批复查询待办、签收、审批
$approverTodoResp = Invoke-CurlJson -Method 'GET' -Url "$base/api/workflow/tasks" -CookieFile $approverCookie
Assert-Code -Resp $approverTodoResp -Expected '0' -Label 'approver-todo'
$approverTaskId = $approverTodoResp.page.records[0].taskId

Assert-Code -Resp (Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/tasks/$approverTaskId/claim" -CookieFile $approverCookie) -Expected '0' -Label 'approver-claim'

$approverApproveBody = (@{
    opinion = 'curl 授信批复通过'
    formData = @{
        approvalLimit = 1000000
    }
} | ConvertTo-Json -Compress)

Assert-Code -Resp (Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/tasks/$approverTaskId/approve" -CookieFile $approverCookie -Body $approverApproveBody) -Expected '0' -Label 'approver-approve'

# 8) 用 MySQL 校验流程终态
$passStatus = (& mysql -N -B -uroot -p123456 -D onepl -e "SELECT process_status FROM biz_process_map WHERE business_key='LOAN:$passBizId' ORDER BY created_time DESC LIMIT 1;").Trim()
$passStatus
```

如果一切正常，最后 `$passStatus` 应该输出：

```text
COMPLETED
```

## 4. 发起流程 → 发起人撤回链

```powershell
$cancelBizId = 'CANCEL' + [guid]::NewGuid().ToString('N').Substring(0,8)

$cancelSubmitBody = (@{
    bizType = 'LOAN'
    bizId = $cancelBizId
    businessKey = "LOAN:$cancelBizId"
    processDefinitionKey = 'loan_approve_v1'
    title = 'curl-真实环境撤回链'
    variables = @{
        bizId = $cancelBizId
        title = 'curl-真实环境撤回链'
    }
} | ConvertTo-Json -Compress)

# 1) 客户经理发起第二条流程
$cancelSubmitResp = Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/processes/submit" -CookieFile $rmCookie -Body $cancelSubmitBody
Assert-Code -Resp $cancelSubmitResp -Expected '0' -Label 'cancel-submit'

$cancelPid = $cancelSubmitResp.data.processInstanceId

# 2) 发起人直接撤回
$cancelBody = (@{
    reason = 'curl 发起人主动撤回'
} | ConvertTo-Json -Compress)

$cancelResp = Invoke-CurlJson -Method 'POST' -Url "$base/api/workflow/processes/$cancelPid/cancel" -CookieFile $rmCookie -Body $cancelBody
Assert-Code -Resp $cancelResp -Expected '0' -Label 'cancel-process'

# 3) 用 MySQL 校验撤回终态
$cancelStatus = (& mysql -N -B -uroot -p123456 -D onepl -e "SELECT process_status FROM biz_process_map WHERE business_key='LOAN:$cancelBizId' ORDER BY created_time DESC LIMIT 1;").Trim()
$cancelStatus
```

如果正常，最后 `$cancelStatus` 应该输出：

```text
CANCELLED
```

## 5. 一次性输出两条链结果

```powershell
[pscustomobject]@{
    submit_business_key        = "LOAN:$passBizId"
    submit_process_instance_id = $passPid
    transfer_task_id           = $branchTaskId
    completed_status           = $passStatus
    cancel_business_key        = "LOAN:$cancelBizId"
    cancel_process_instance_id = $cancelPid
    cancelled_status           = $cancelStatus
} | ConvertTo-Json -Depth 4
```

你会得到类似：

```json
{
  "submit_business_key": "LOAN:CURL73bf4d11",
  "submit_process_instance_id": "b31114f0-37e7-11f1-b687-7413ea9d5f70",
  "transfer_task_id": "b31114fb-37e7-11f1-b687-7413ea9d5f70",
  "completed_status": "COMPLETED",
  "cancel_business_key": "LOAN:CANCEL837d2550",
  "cancel_process_instance_id": "b416eb6d-37e7-11f1-b687-7413ea9d5f70",
  "cancelled_status": "CANCELLED"
}
```

## 6. 本次实际使用的接口

- `POST /api/auth/login`
- `GET /api/workflow/tasks`
- `POST /api/workflow/processes/submit`
- `POST /api/workflow/processes/{processInstanceId}/cancel`
- `POST /api/workflow/tasks/{taskId}/claim`
- `POST /api/workflow/tasks/{taskId}/transfer`
- `POST /api/workflow/tasks/{taskId}/approve`
