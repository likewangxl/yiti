<template>
  <div class="report-container">
    <h2 class="page-title">四大维度材料上报</h2>
    <p class="page-desc">按考核维度上传材料，系统自动关联评分标准。每个考核项可提交多条记录。</p>

    <!-- 维度Tab -->
    <div class="dim-tabs">
      <div
        v-for="tab in dimTabs"
        :key="tab.id"
        :class="['dim-tab', { active: activeDim === tab.id }]"
        :style="activeDim === tab.id ? { color: tab.color, borderBottomColor: tab.color } : {}"
        @click="switchDim(tab.id)"
      >{{ tab.label }}</div>
    </div>

    <!-- ========== 维度一：外联共建 ========== -->
    <template v-if="activeDim === 'dim1'">
      <div class="sub-tabs">
        <div v-for="sub in dim1Subs" :key="sub.id"
          :class="['sub-tab', { active: activeSub === sub.id }]"
          @click="activeSub = sub.id"
        >{{ sub.label }}</div>
      </div>

      <!-- 1.1 联建规范度 -->
      <div v-if="activeSub === '1.1'" class="sub-section">
        <div class="section-header">
          <h3 class="form-title">1.1 联建规范度</h3>
          <div class="record-count">已提交 <strong>{{ getRecords('1.1').length }}</strong> 条</div>
        </div>
        <div class="rule-box yellow">
          <strong>评分标准：</strong>每次1.5分，最高6分。<span class="text-red">纯座谈不计分。</span>
        </div>

        <!-- 已提交记录 -->
        <div v-if="getRecords('1.1').length > 0" class="records-list">
          <div v-for="(rec, idx) in getRecords('1.1')" :key="rec.id" class="record-card">
            <div class="record-header">
              <span class="record-no">第{{ idx + 1 }}条</span>
              <el-tag :type="rec.status === 'passed' ? 'success' : rec.status === 'rejected' ? 'danger' : 'warning'" size="small">
                {{ rec.status === 'passed' ? '已通过' : rec.status === 'rejected' ? '已驳回' : '待审核' }}
              </el-tag>
              <span class="record-date">{{ rec.date }}</span>
              <el-button type="danger" text size="small" @click="removeRecord('1.1', rec.id)">删除</el-button>
            </div>
            <div class="record-body">
              <span class="record-field">{{ rec.summary }}</span>
            </div>
          </div>
        </div>

        <!-- 新增表单 -->
        <div v-if="showForm['1.1']" class="form-card">
          <div class="form-card-header">
            <span>📝 新增记录</span>
            <el-button text size="small" @click="showForm['1.1'] = false">收起</el-button>
          </div>
          <div class="form-field"><label>共建单位 *</label><el-input v-model="forms['1.1'].unit" placeholder="XX产业链单位" /></div>
          <div class="form-field"><label>活动日期 *</label><el-date-picker v-model="forms['1.1'].date" type="date" placeholder="选择日期" style="width:100%;" /></div>
          <div class="form-field"><label>痛点研讨结论 *</label><el-input v-model="forms['1.1'].conclusion" type="textarea" :rows="5" placeholder="双方确认的业务突破口及下一步行动..." /></div>
          <div class="upload-label">📎 强制佐证上传</div>
          <el-upload class="upload-uploader" multiple :auto-upload="false" :on-change="(file) => onFileChange('1.1', file)" :on-remove="(file) => onFileRemove('1.1', file)">
            <div class="upload-grid">
              <div class="upload-box"><div class="upload-icon">📕</div><div class="upload-name">联建活动纪要</div><div class="upload-hint">须含痛点研讨</div></div>
              <div class="upload-box"><div class="upload-icon">📸</div><div class="upload-name">签到表与照片</div><div class="upload-hint">JPG/ZIP</div></div>
            </div>
          </el-upload>
          <div class="form-actions">
            <el-button @click="showForm['1.1'] = false">取消</el-button>
            <el-button type="danger" :loading="submitting['1.1']" @click="submitRecord('1.1')">提交审核</el-button>
          </div>
        </div>

        <!-- 新增按钮 -->
        <div v-if="!showForm['1.1']" class="add-record-btn" @click="openForm('1.1')">
          <span class="add-icon">＋</span> 新增一条联建记录
        </div>
      </div>

      <!-- 1.2 合作契约化 -->
      <div v-if="activeSub === '1.2'" class="sub-section">
        <div class="section-header">
          <h3 class="form-title">1.2 合作契约化</h3>
          <div class="record-count">已提交 <strong>{{ getRecords('1.2').length }}</strong> 条</div>
        </div>
        <div class="rule-box yellow"><strong>评分标准：</strong>每份协议1分，最高4分。</div>

        <div v-if="getRecords('1.2').length > 0" class="records-list">
          <div v-for="(rec, idx) in getRecords('1.2')" :key="rec.id" class="record-card">
            <div class="record-header">
              <span class="record-no">第{{ idx + 1 }}条</span>
              <el-tag :type="rec.status === 'passed' ? 'success' : 'warning'" size="small">
                {{ rec.status === 'passed' ? '已通过' : '待审核' }}
              </el-tag>
              <span class="record-date">{{ rec.date }}</span>
              <el-button type="danger" text size="small" @click="removeRecord('1.2', rec.id)">删除</el-button>
            </div>
            <div class="record-body"><span class="record-field">{{ rec.summary }}</span></div>
          </div>
        </div>

        <div v-if="showForm['1.2']" class="form-card">
          <div class="form-card-header"><span>📝 新增协议</span><el-button text size="small" @click="showForm['1.2'] = false">收起</el-button></div>
          <div class="form-field"><label>协议名称 *</label><el-input v-model="forms['1.2'].name" placeholder="合作协议名称" /></div>
          <el-upload class="upload-uploader" multiple :auto-upload="false" :on-change="(file) => onFileChange('1.2', file)" :on-remove="(file) => onFileRemove('1.2', file)">
            <div class="upload-grid">
              <div class="upload-box"><div class="upload-icon">📋</div><div class="upload-name">上传协议复印件</div></div>
            </div>
          </el-upload>
          <div class="form-actions">
            <el-button @click="showForm['1.2'] = false">取消</el-button>
            <el-button type="danger" :loading="submitting['1.2']" @click="submitRecord('1.2')">提交</el-button>
          </div>
        </div>

        <div v-if="!showForm['1.2']" class="add-record-btn" @click="openForm('1.2')">
          <span class="add-icon">＋</span> 新增一份协议记录
        </div>
      </div>

      <!-- 1.3 业务实质转化 -->
      <div v-if="activeSub === '1.3'" class="sub-section">
        <div class="section-header">
          <h3 class="form-title">1.3 业务实质转化</h3>
          <div class="record-count">已提交 <strong>{{ getRecords('1.3').length }}</strong> 条</div>
        </div>
        <div class="rule-box yellow">
          <strong>评分标准：</strong>取最高项不叠加：破冰(2分)→推进(4分)→<strong class="text-green">落地(8分)</strong>。满分25分。
        </div>

        <div v-if="getRecords('1.3').length > 0" class="records-list">
          <div v-for="(rec, idx) in getRecords('1.3')" :key="rec.id" class="record-card">
            <div class="record-header">
              <span class="record-no">第{{ idx + 1 }}条</span>
              <el-tag :type="rec.status === 'passed' ? 'success' : 'warning'" size="small">
                {{ rec.status === 'passed' ? '已通过' : '待审核' }}
              </el-tag>
              <span class="record-date">{{ rec.date }}</span>
              <el-button type="danger" text size="small" @click="removeRecord('1.3', rec.id)">删除</el-button>
            </div>
            <div class="record-body"><span class="record-field">{{ rec.summary }}</span></div>
          </div>
        </div>

        <div v-if="showForm['1.3']" class="form-card">
          <div class="form-card-header"><span>📝 新增转化记录</span><el-button text size="small" @click="showForm['1.3'] = false">收起</el-button></div>
          <div class="form-field"><label>转化级别 *</label>
            <el-select v-model="forms['1.3'].level" placeholder="请选择" style="width:100%;"><el-option label="破冰级（2分）" value="破冰级" /><el-option label="推进级（4分）" value="推进级" /><el-option label="落地级（8分）" value="落地级" /></el-select>
          </div>
          <div class="form-field"><label>客户号 *</label><el-input v-model="forms['1.3'].customer" placeholder="CUST-2026-xxxxx" /></div>
          <div class="form-field"><label>交易流水号 *</label><el-input v-model="forms['1.3'].txn" placeholder="TXN-xxxxxxxx" /></div>
          <div class="form-field"><label>到账金额</label><el-input v-model="forms['1.3'].amount" placeholder="¥ 0.00" /></div>
          <el-upload class="upload-uploader" multiple :auto-upload="false" :on-change="(file) => onFileChange('1.3', file)" :on-remove="(file) => onFileRemove('1.3', file)">
            <div class="upload-grid">
              <div class="upload-box"><div class="upload-icon">🖼️</div><div class="upload-name">核心系统截图</div><div class="upload-hint">须含客户号、流水号、金额</div></div>
            </div>
          </el-upload>
          <div class="form-actions">
            <el-button @click="showForm['1.3'] = false">取消</el-button>
            <el-button type="danger" :loading="submitting['1.3']" @click="submitRecord('1.3')">提交审核</el-button>
          </div>
        </div>

        <div v-if="!showForm['1.3']" class="add-record-btn" @click="openForm('1.3')">
          <span class="add-icon">＋</span> 新增一条转化记录
        </div>
      </div>
    </template>

    <!-- ========== 维度二：业务提升 ========== -->
    <template v-if="activeDim === 'dim2'">
      <div class="sub-tabs">
        <div v-for="sub in dim2Subs" :key="sub.id"
          :class="['sub-tab', { active: activeSub === sub.id }]"
          :style="activeSub === sub.id ? { color: '#2563eb', borderBottomColor: '#2563eb' } : {}"
          @click="activeSub = sub.id"
        >{{ sub.label }}</div>
      </div>
      <div class="sub-section">
        <div class="section-header">
          <h3 class="form-title">{{ activeSub === '2.2' ? '2.2 中后台定量考核' : '2.1 经营机构定量考核' }}</h3>
          <div class="record-count">已提交 <strong>{{ getRecords(activeSub).length }}</strong> 条</div>
        </div>
        <div class="rule-box blue"><strong>评分标准：</strong>实际达成量÷目标×50分。≥100%计50分；60%-99%按公式折算；&lt;60%计0分。</div>
        <div class="info-tip blue"><strong>说明：</strong>此维度数据由相关部门年底统一调取核算，支部可上传阶段性佐证材料。</div>

        <div v-if="getRecords(activeSub).length > 0" class="records-list">
          <div v-for="(rec, idx) in getRecords(activeSub)" :key="rec.id" class="record-card">
            <div class="record-header">
              <span class="record-no">第{{ idx + 1 }}条</span>
              <el-tag :type="rec.status === 'passed' ? 'success' : 'warning'" size="small">{{ rec.status === 'passed' ? '已通过' : '待审核' }}</el-tag>
              <span class="record-date">{{ rec.date }}</span>
              <el-button type="danger" text size="small" @click="removeRecord(activeSub, rec.id)">删除</el-button>
            </div>
            <div class="record-body"><span class="record-field">{{ rec.summary }}</span></div>
          </div>
        </div>

        <div v-if="showForm[activeSub]" class="form-card">
          <div class="form-card-header"><span>📝 新增佐证材料</span><el-button text size="small" @click="showForm[activeSub] = false">收起</el-button></div>
          <div class="form-field"><label>说明</label><el-input v-model="forms[activeSub].desc" type="textarea" :rows="4" placeholder="阶段性佐证说明..." /></div>
          <el-upload class="upload-uploader" multiple :auto-upload="false" :on-change="(file) => onFileChange(activeSub, file)" :on-remove="(file) => onFileRemove(activeSub, file)">
            <div class="upload-grid">
              <div class="upload-box"><div class="upload-icon">📊</div><div class="upload-name">Excel批量导入业务数据</div><div class="upload-hint">XLS / XLSX</div></div>
              <div class="upload-box"><div class="upload-icon">💻</div><div class="upload-name">上传核心系统截图</div><div class="upload-hint">支持多张拖拽</div></div>
            </div>
          </el-upload>
          <div class="form-actions">
            <el-button @click="showForm[activeSub] = false">取消</el-button>
            <el-button type="danger" :loading="submitting[activeSub]" @click="submitRecord(activeSub)">提交</el-button>
          </div>
        </div>

        <div v-if="!showForm[activeSub]" class="add-record-btn" @click="openForm(activeSub)">
          <span class="add-icon">＋</span> 新增一条佐证材料
        </div>
      </div>
    </template>

    <!-- ========== 维度三：头雁与先锋 ========== -->
    <template v-if="activeDim === 'dim3'">
      <div class="sub-tabs">
        <div v-for="sub in dim3Subs" :key="sub.id"
          :class="['sub-tab', { active: activeSub === sub.id }]"
          :style="activeSub === sub.id ? { color: '#ca8a04', borderBottomColor: '#ca8a04' } : {}"
          @click="activeSub = sub.id"
        >{{ sub.label }}</div>
      </div>

      <!-- 4.1 书记挂帅 -->
      <div v-if="activeSub === '4.1'" class="sub-section">
        <div class="section-header">
          <h3 class="form-title">4.1 书记挂帅履职</h3>
          <div class="record-count">已提交 <strong>{{ getRecords('4.1').length }}</strong> 条</div>
        </div>
        <div class="rule-box yellow"><strong>评分标准：</strong>支部书记亲自带队拜访核心客户/协调关键部门，每次1分，满分5分。</div>

        <div v-if="getRecords('4.1').length > 0" class="records-list">
          <div v-for="(rec, idx) in getRecords('4.1')" :key="rec.id" class="record-card">
            <div class="record-header">
              <span class="record-no">第{{ idx + 1 }}条</span>
              <el-tag :type="rec.status === 'passed' ? 'success' : 'warning'" size="small">{{ rec.status === 'passed' ? '已通过' : '待审核' }}</el-tag>
              <span class="record-date">{{ rec.date }}</span>
              <el-button type="danger" text size="small" @click="removeRecord('4.1', rec.id)">删除</el-button>
            </div>
            <div class="record-body"><span class="record-field">{{ rec.summary }}</span></div>
          </div>
        </div>

        <div v-if="showForm['4.1']" class="form-card">
          <div class="form-card-header"><span>📝 新增走访记录</span><el-button text size="small" @click="showForm['4.1'] = false">收起</el-button></div>
          <div class="form-field"><label>走访对象 *</label><el-input v-model="forms['4.1'].target" placeholder="核心客户/关键部门名称" /></div>
          <div class="form-field"><label>走访日期</label><el-date-picker v-model="forms['4.1'].date" type="date" placeholder="选择日期" style="width:100%;" /></div>
          <div class="form-field"><label>走访成果</label><el-input v-model="forms['4.1'].result" type="textarea" :rows="4" placeholder="本次走访达成的成果..." /></div>
          <el-upload class="upload-uploader" multiple :auto-upload="false" :on-change="(file) => onFileChange('4.1', file)" :on-remove="(file) => onFileRemove('4.1', file)">
            <div class="upload-grid">
              <div class="upload-box"><div class="upload-icon">📷</div><div class="upload-name">走访记录（含照片）</div></div>
              <div class="upload-box"><div class="upload-icon">📝</div><div class="upload-name">专题会议纪要</div></div>
            </div>
          </el-upload>
          <div class="form-actions">
            <el-button @click="showForm['4.1'] = false">取消</el-button>
            <el-button type="danger" :loading="submitting['4.1']" @click="submitRecord('4.1')">提交</el-button>
          </div>
        </div>

        <div v-if="!showForm['4.1']" class="add-record-btn" @click="openForm('4.1')">
          <span class="add-icon">＋</span> 新增一条走访记录
        </div>
      </div>

      <!-- 4.2 党员先锋突击 -->
      <div v-if="activeSub === '4.2'" class="sub-section">
        <div class="section-header">
          <h3 class="form-title">4.2 党员先锋突击</h3>
          <div class="record-count">已提交 <strong>{{ getRecords('4.2').length }}</strong> 条</div>
        </div>
        <div class="rule-box yellow"><strong>评分标准：</strong>有明确责任分工(2分)；难题攻克取得明显进展(3分)。满分5分。</div>

        <div v-if="getRecords('4.2').length > 0" class="records-list">
          <div v-for="(rec, idx) in getRecords('4.2')" :key="rec.id" class="record-card">
            <div class="record-header">
              <span class="record-no">第{{ idx + 1 }}条</span>
              <el-tag :type="rec.status === 'passed' ? 'success' : 'warning'" size="small">{{ rec.status === 'passed' ? '已通过' : '待审核' }}</el-tag>
              <span class="record-date">{{ rec.date }}</span>
              <el-button type="danger" text size="small" @click="removeRecord('4.2', rec.id)">删除</el-button>
            </div>
            <div class="record-body"><span class="record-field">{{ rec.summary }}</span></div>
          </div>
        </div>

        <div v-if="showForm['4.2']" class="form-card">
          <div class="form-card-header"><span>📝 新增攻坚记录</span><el-button text size="small" @click="showForm['4.2'] = false">收起</el-button></div>
          <div class="form-field"><label>攻坚难题 *</label><el-input v-model="forms['4.2'].problem" type="textarea" :rows="4" placeholder="描述认领的核心难点..." /></div>
          <div class="form-field"><label>攻坚党员</label><el-input v-model="forms['4.2'].member" placeholder="党员姓名" /></div>
          <el-upload class="upload-uploader" multiple :auto-upload="false" :on-change="(file) => onFileChange('4.2', file)" :on-remove="(file) => onFileRemove('4.2', file)">
            <div class="upload-grid">
              <div class="upload-box"><div class="upload-icon">📋</div><div class="upload-name">《党员攻坚责任认领书》</div></div>
              <div class="upload-box"><div class="upload-icon">💻</div><div class="upload-name">业务流转操作记录</div></div>
            </div>
          </el-upload>
          <div class="form-actions">
            <el-button @click="showForm['4.2'] = false">取消</el-button>
            <el-button type="danger" :loading="submitting['4.2']" @click="submitRecord('4.2')">提交</el-button>
          </div>
        </div>

        <div v-if="!showForm['4.2']" class="add-record-btn" @click="openForm('4.2')">
          <span class="add-icon">＋</span> 新增一条攻坚记录
        </div>
      </div>
    </template>

    <!-- ========== 维度四：督导与工作总结 ========== -->
    <template v-if="activeDim === 'dim4'">
      <div class="sub-section">
        <div class="section-header">
          <h3 class="form-title">过程督导响应</h3>
          <div class="record-count">已提交 <strong>{{ getRecords('sup').length }}</strong> 条</div>
        </div>
        <div class="rule-box green">
          <strong>评分标准：</strong>按时提交节点跟踪表、典型案例、工作总结。逾期1天扣1分，逾期3天不得分。获分行阶段性通报表扬（加1分）。满分5分。
        </div>

        <div v-if="getRecords('sup').length > 0" class="records-list">
          <div v-for="(rec, idx) in getRecords('sup')" :key="rec.id" class="record-card">
            <div class="record-header">
              <span class="record-no">第{{ idx + 1 }}条</span>
              <el-tag :type="rec.status === 'passed' ? 'success' : 'warning'" size="small">{{ rec.status === 'passed' ? '已通过' : '待审核' }}</el-tag>
              <span class="record-date">{{ rec.date }}</span>
              <el-button type="danger" text size="small" @click="removeRecord('sup', rec.id)">删除</el-button>
            </div>
            <div class="record-body"><span class="record-field">{{ rec.summary }}</span></div>
          </div>
        </div>

        <div v-if="showForm['sup']" class="form-card">
          <div class="form-card-header"><span>📝 新增督导记录</span><el-button text size="small" @click="showForm['sup'] = false">收起</el-button></div>
          <div class="form-field"><label>工作说明（选填）</label><el-input v-model="forms['sup'].desc" type="textarea" :rows="5" placeholder="本月推进情况..." /></div>
          <el-upload class="upload-uploader" multiple :auto-upload="false" :on-change="(file) => onFileChange('sup', file)" :on-remove="(file) => onFileRemove('sup', file)">
            <div class="upload-grid">
              <div class="upload-box"><div class="upload-icon">📋</div><div class="upload-name">节点跟踪表</div><div class="upload-hint text-red">* 必传</div></div>
              <div class="upload-box"><div class="upload-icon">📖</div><div class="upload-name">典型案例</div><div class="upload-hint">选填</div></div>
              <div class="upload-box"><div class="upload-icon">📃</div><div class="upload-name">工作总结</div><div class="upload-hint">选填</div></div>
            </div>
          </el-upload>
          <div class="form-actions">
            <el-button @click="showForm['sup'] = false">取消</el-button>
            <el-button type="danger" :loading="submitting['sup']" @click="submitRecord('sup')">提交至组织部审核</el-button>
          </div>
        </div>

        <div v-if="!showForm['sup']" class="add-record-btn" @click="openForm('sup')">
          <span class="add-icon">＋</span> 新增一条督导记录
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
// 四大维度材料上报。
// F1：script 内 API 全部改走 @/api/redengine（createSubmit/uploadFile），不再是源系统纯本地
// mock 状态（源 JointView.vue script 完全没有任何网络请求，8 个子表单的"提交"只是 push 进本地
// reactive 数组）。本次移植按简报要求把 submitRecord 接上真实后端：
//   1) 附件先逐个调 governance 通用端点 POST /api/files/upload 拿 fileObjectId
//   2) 再调 red-engine-center POST /api/re/submits（ReSubmitCreateReqDTO）创建即提交
// 字段先读 red-engine-center ReSubmitCreateReqDTO.java 确认：dimension(dim1~dim4)/itemCode/
// itemName/maxScore/projectName/submitType/submitDate/formData/fileObjectIds；orgId/submitterId
// 不由前端传（服务端按当前登录人解析 party org，防止越权指定他人组织）。
// YAGNI：源系统与本次移植均不做草稿态（submitType 固定按"月度=1"上报，源页面本身也无期间选择控件）。
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { createSubmit, uploadFile } from '@/api/redengine'

const dimTabs = [
  { id: 'dim1', label: '一、外联共建（35分）', color: '#dc2626' },
  { id: 'dim2', label: '二、业务提升（50分）', color: '#2563eb' },
  { id: 'dim3', label: '三、头雁与先锋（10分）', color: '#ca8a04' },
  { id: 'dim4', label: '四、督导与工作总结（5分）', color: '#16a34a' },
]

const dim1Subs = [
  { id: '1.1', label: '1.1 联建规范度（6分）' },
  { id: '1.2', label: '1.2 合作契约化（4分）' },
  { id: '1.3', label: '1.3 业务实质转化（25分）' },
]
const dim2Subs = [
  { id: '2.1', label: '2.1 经营机构定量（50分）' },
  { id: '2.2', label: '2.2 中后台定量（50分）' },
]
const dim3Subs = [
  { id: '4.1', label: '4.1 书记挂帅履职（5分）' },
  { id: '4.2', label: '4.2 党员先锋突击（5分）' },
]

const activeDim = ref('dim1')
const activeSub = ref('1.1')

const switchDim = (id) => {
  activeDim.value = id
  const subMap = { dim1: '1.1', dim2: '2.1', dim3: '4.1', dim4: 'sup' }
  activeSub.value = subMap[id] || '1.1'
}

// 考核项元数据：itemCode → 提交 ReSubmitCreateReqDTO 所需的 dimension/itemName/maxScore
const itemMeta = {
  '1.1': { dimension: 'dim1', itemName: '联建规范度', maxScore: 6 },
  '1.2': { dimension: 'dim1', itemName: '合作契约化', maxScore: 4 },
  '1.3': { dimension: 'dim1', itemName: '业务实质转化', maxScore: 25 },
  '2.1': { dimension: 'dim2', itemName: '经营机构定量考核', maxScore: 50 },
  '2.2': { dimension: 'dim2', itemName: '中后台定量考核', maxScore: 50 },
  '4.1': { dimension: 'dim3', itemName: '书记挂帅履职', maxScore: 5 },
  '4.2': { dimension: 'dim3', itemName: '党员先锋突击', maxScore: 5 },
  sup: { dimension: 'dim4', itemName: '过程督导响应', maxScore: 5 },
}

// ========== 多条记录管理 ==========
let nextId = 100

// 已提交记录，按子项分组（保留源系统种子演示数据，新提交的记录会 push 进对应数组）
const records = reactive({
  '1.1': [
    { id: 1, summary: '与XX产业链单位联建 — 供应链金融痛点研讨', date: '2026-03-22', status: 'passed' },
    { id: 2, summary: '与YY科技集团联建 — 数字化转型合作', date: '2026-03-26', status: 'pending' },
  ],
  '1.2': [
    { id: 3, summary: '与XX产业链上下游单位签署合作框架协议', date: '2026-03-18', status: 'passed' },
  ],
  '1.3': [
    { id: 4, summary: '落地级 — 客户CUST-2026-03881 对公存款 ¥3,200,000', date: '2026-03-28', status: 'pending' },
  ],
  '2.1': [],
  '2.2': [],
  '4.1': [
    { id: 5, summary: '走访XX核心客户 — 达成合作意向', date: '2026-03-15', status: 'passed' },
  ],
  '4.2': [],
  sup: [
    { id: 6, summary: '3月节点跟踪表 + 工作总结', date: '2026-03-25', status: 'pending' },
  ],
})

// 每个子项是否展示新增表单
const showForm = reactive({
  '1.1': false, '1.2': false, '1.3': false,
  '2.1': false, '2.2': false,
  '4.1': false, '4.2': false,
  sup: false,
})

// 每个子项的表单数据
const forms = reactive({
  '1.1': { unit: '', date: null, conclusion: '' },
  '1.2': { name: '' },
  '1.3': { level: '', customer: '', txn: '', amount: '' },
  '2.1': { desc: '' },
  '2.2': { desc: '' },
  '4.1': { target: '', date: null, result: '' },
  '4.2': { problem: '', member: '' },
  sup: { desc: '' },
})

// 每个子项已选待上传的原始文件（el-upload :auto-upload="false" 收集，提交时才逐个调用
// uploadFile 换成 fileObjectId；源系统的 upload-box 纯装饰 div、无真实 file input，本次为真实
// 提交能力新增，不再逐个 box 区分文件角色——后端 ReSubmitCreateReqDTO.fileObjectIds 本就是扁平列表）
const rawFiles = reactive({
  '1.1': [], '1.2': [], '1.3': [],
  '2.1': [], '2.2': [],
  '4.1': [], '4.2': [],
  sup: [],
})

// 提交中状态：禁用重复点击
const submitting = reactive({
  '1.1': false, '1.2': false, '1.3': false,
  '2.1': false, '2.2': false,
  '4.1': false, '4.2': false,
  sup: false,
})

const getRecords = (subId) => records[subId] || []

const openForm = (subId) => {
  showForm[subId] = true
}

function onFileChange(subId, file) {
  rawFiles[subId].push(file.raw)
}

function onFileRemove(subId, file) {
  const idx = rawFiles[subId].findIndex((f) => f === file.raw)
  if (idx >= 0) rawFiles[subId].splice(idx, 1)
}

const submitRecord = async (subId) => {
  const f = forms[subId]
  let summary = ''

  // 构建摘要（与源系统一致的前端必填校验，原样保真）
  if (subId === '1.1') {
    if (!f.unit) { ElMessage.warning('请填写共建单位'); return }
    summary = `与${f.unit}联建 — ${f.conclusion || '（详见附件）'}`
  } else if (subId === '1.2') {
    if (!f.name) { ElMessage.warning('请填写协议名称'); return }
    summary = f.name
  } else if (subId === '1.3') {
    if (!f.level || !f.customer) { ElMessage.warning('请填写转化级别和客户号'); return }
    summary = `${f.level} — 客户${f.customer} ${f.amount || ''}`
  } else if (subId === '2.1' || subId === '2.2') {
    summary = f.desc || '阶段性佐证材料'
  } else if (subId === '4.1') {
    if (!f.target) { ElMessage.warning('请填写走访对象'); return }
    summary = `走访${f.target} — ${f.result || '（详见附件）'}`
  } else if (subId === '4.2') {
    if (!f.problem) { ElMessage.warning('请填写攻坚难题'); return }
    summary = `${f.problem}${f.member ? '（' + f.member + '）' : ''}`
  } else if (subId === 'sup') {
    summary = f.desc || '督导响应材料'
  }

  const today = new Date().toISOString().split('T')[0]
  const meta = itemMeta[subId]

  submitting[subId] = true
  try {
    // 1) 附件先逐个上传拿 fileObjectId（无附件时数组为空，后端字段本就是可选）
    const fileObjectIds = []
    for (const raw of rawFiles[subId]) {
      const fd = new FormData()
      fd.append('file', raw)
      const dto = await uploadFile(fd)
      if (dto?.id) fileObjectIds.push(dto.id)
    }

    // 2) 创建即提交
    await createSubmit({
      dimension: meta.dimension,
      itemCode: subId,
      itemName: meta.itemName,
      maxScore: meta.maxScore,
      projectName: summary,
      submitType: 1,
      submitDate: today,
      formData: JSON.stringify(f),
      fileObjectIds,
    })

    records[subId].push({
      id: nextId++,
      summary,
      date: today,
      status: 'pending',
    })

    // 重置表单
    Object.keys(f).forEach((k) => { f[k] = typeof f[k] === 'string' ? '' : null })
    rawFiles[subId] = []
    showForm[subId] = false
    ElMessage.success('✅ 上报成功，待审核')
  } catch (e) {
    ElMessage.error(e?.message || '上报失败')
  } finally {
    submitting[subId] = false
  }
}

const removeRecord = (subId, id) => {
  // 仅移除本页面本地展示列表：red-engine-center 当前 6 个 Controller 未提供
  // DELETE /api/re/submits/{id}（已知能力缺口），与源系统行为一致——源系统同样只 splice
  // 本地数组，不调用任何删除接口
  const idx = records[subId].findIndex((r) => r.id === id)
  if (idx >= 0) {
    records[subId].splice(idx, 1)
    ElMessage.success('已删除')
  }
}
</script>

<style scoped lang="scss">
.report-container { padding: 0; }

.page-title { font-size: 20px; font-weight: 700; color: #1e293b; margin: 0 0 4px 0; }
.page-desc { font-size: 12px; color: #64748b; margin: 0 0 16px 0; }

/* Dimension Tabs */
.dim-tabs {
  display: flex;
  border-bottom: 2px solid #e2e8f0;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.dim-tab {
  padding: 10px 16px;
  cursor: pointer;
  font-size: 13px;
  color: #64748b;
  border-bottom: 3px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
  font-weight: 400;

  &.active { font-weight: 600; }
  &:hover { color: #1e293b; }
}

/* Sub Tabs */
.sub-tabs {
  display: flex;
  border-bottom: 1px solid #e2e8f0;
  margin-bottom: 14px;
}

.sub-tab {
  padding: 7px 14px;
  cursor: pointer;
  font-size: 12px;
  color: #64748b;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  font-weight: 400;

  &.active { font-weight: 600; color: #b91c1c; border-bottom-color: #b91c1c; }
}

/* Sub Section */
.sub-section {
  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  .record-count {
    font-size: 13px;
    color: #64748b;

    strong {
      color: #dc2626;
      font-size: 16px;
    }
  }
}

/* Form Card */
.form-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #e2e8f0;
  margin-bottom: 12px;
}

.form-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.form-title { font-size: 15px; font-weight: 600; color: #1e293b; margin: 0; }

/* Rule boxes */
.rule-box {
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 14px;
  font-size: 12px;
  line-height: 1.7;

  &.yellow { background: #fef9c3; border: 1px solid rgba(234,179,8,.3); color: #374151; }
  &.blue { background: #dbeafe; border: 1px solid rgba(59,130,246,.3); color: #1e40af; }
  &.green { background: #dcfce7; border: 1px solid rgba(22,163,74,.3); color: #166534; }
}

.info-tip {
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 14px;
  font-size: 12px;

  &.blue { background: #eff6ff; color: #1e40af; }
}

.text-red { color: #dc2626; font-weight: 600; }
.text-green { color: #16a34a; }

/* Records list */
.records-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.record-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }
}

.record-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;

  .record-no {
    font-size: 12px;
    font-weight: 700;
    color: #475569;
    background: #e2e8f0;
    padding: 1px 8px;
    border-radius: 4px;
  }

  .record-date {
    font-size: 12px;
    color: #94a3b8;
    margin-left: auto;
  }
}

.record-body {
  .record-field {
    font-size: 13px;
    color: #334155;
    line-height: 1.6;
  }
}

/* Add record button */
.add-record-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 14px;
  border: 2px dashed #cbd5e1;
  border-radius: 10px;
  cursor: pointer;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
  background: #fafbfc;
  margin-top: 8px;

  &:hover {
    border-color: #dc2626;
    color: #dc2626;
    background: #fef2f2;
  }

  .add-icon {
    font-size: 18px;
    font-weight: 700;
  }
}

/* Form fields */
.form-field {
  margin-bottom: 16px;

  label {
    display: block;
    font-size: 13px;
    font-weight: 600;
    color: #374151;
    margin-bottom: 6px;
  }

  // All inputs full width
  :deep(.el-input),
  :deep(.el-select),
  :deep(.el-date-editor),
  :deep(.el-textarea) {
    width: 100% !important;
    display: block !important;
  }

  :deep(.el-input__wrapper) {
    border-radius: 8px;
    padding: 4px 12px;
    box-shadow: 0 0 0 1px #d1d5db inset;
    transition: box-shadow 0.2s;

    &:hover {
      box-shadow: 0 0 0 1px #94a3b8 inset;
    }

    &.is-focus {
      box-shadow: 0 0 0 2px #dc2626 inset;
    }
  }

  // Textarea styling
  :deep(.el-textarea__inner) {
    border-radius: 8px;
    padding: 10px 14px;
    font-size: 13px;
    line-height: 1.6;
    resize: none !important;
    min-height: 120px !important;
    border: 1px solid #d1d5db;
    transition: border-color 0.2s;

    &:hover {
      border-color: #94a3b8;
    }

    &:focus {
      border-color: #dc2626;
      box-shadow: 0 0 0 1px rgba(220,38,38,0.2);
    }
  }

  // Date picker popper near input
  :deep(.el-date-editor) {
    .el-input__wrapper {
      padding: 4px 12px;
    }
  }

  // Select dropdown
  :deep(.el-select) {
    .el-input__wrapper {
      padding: 4px 12px;
    }
  }
}

/* Upload boxes（el-upload 默认包裹为 inline-block，本次接入真实上传须撑满宽度以保留原网格布局） */
.upload-uploader {
  display: block;
  width: 100%;

  :deep(.el-upload) {
    display: block;
    width: 100%;
  }
}

.upload-label {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.upload-grid {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.upload-box {
  padding: 16px;
  border: 2px dashed #d1d5db;
  border-radius: 10px;
  text-align: center;
  cursor: pointer;
  background: #f9fafb;
  transition: all 0.2s;
  flex: 1;
  min-width: 140px;

  &:hover { border-color: #94a3b8; background: #f1f5f9; }

  .upload-icon { font-size: 24px; margin-bottom: 4px; }
  .upload-name { font-size: 12px; color: #475569; font-weight: 500; }
  .upload-hint { font-size: 10px; color: #94a3b8; margin-top: 2px; }
}

/* Actions */
.form-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  border-top: 1px solid #e2e8f0;
  padding-top: 12px;
}
</style>
