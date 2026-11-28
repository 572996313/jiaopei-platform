<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <article class="panel-card education-panel">
      <div class="table-toolbar">
        <el-button v-if="canWrite" type="primary" @click="openCreate">新建事项</el-button>
        <el-button :icon="Refresh" circle :loading="loading" @click="reload" />
        <el-radio-group v-model="scopeMode" @change="search">
          <el-radio-button value="all">全部事件</el-radio-button>
          <el-radio-button value="mine">我关心的</el-radio-button>
        </el-radio-group>
        <NoteHint label="事件流口径">
          事件产生时<b>不筛人</b>：本机构员工先都能看到全部事件。「我关心的」只是按
          <b>个人订阅</b>收窄注意力，不是权限边界；漏勾选不会让人看不到，只是不在这个视图里。
          系统事件（未点名/未结课/欠费）由配置化巡检产生，<b>条件消失会自动消解</b>；任务/反馈/问题由人流转状态。
        </NoteHint>
      </div>

      <FilterBar :loading="loading" @query="search" @reset="resetFilters">
        <el-select
          v-model="filters.typeCode"
          placeholder="事件类型"
          clearable
          filterable
          style="width: 190px"
        >
          <el-option
            v-for="item in typeOptions"
            :key="item.typeCode"
            :label="item.typeName"
            :value="item.typeCode"
          />
        </el-select>
        <DictSelect
          v-model="filters.category"
          type-code="message_category"
          placeholder="分类"
          clearable
          style="width: 130px"
        />
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 160px">
          <el-option
            v-for="item in MESSAGE_STATUS_FILTER_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="filters.level" placeholder="级别" clearable style="width: 110px">
          <el-option
            v-for="item in MESSAGE_LEVEL_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="发生起"
          end-placeholder="发生止"
          style="width: 250px"
        />
        <el-input
          v-model="filters.keyword"
          placeholder="标题/正文关键字"
          clearable
          style="width: 210px"
          @keyup.enter="search"
        />
        <el-checkbox v-model="filters.unreadOnly" @change="search">只看未读</el-checkbox>
      </FilterBar>

      <TableFillBox v-slot="{ height }">
        <el-table
          :data="records"
          border
          row-key="id"
          highlight-current-row
          v-loading="loading"
          :height="height"
          :empty-text="
            loading
              ? ''
              : '当前筛选范围下没有事件。系统巡检产生的事件会在下一轮巡检后出现；人工事项可直接新建。'
          "
          @row-click="openDetail"
        >
          <el-table-column prop="level" label="级别" width="82">
            <template #default="{ row }">
              <el-tag :type="levelTag(row.level)" size="small">{{ levelLabel(row.level) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="!row.read" class="msg-unread-dot" title="未读" aria-label="未读"></span>
              <span :class="{ 'msg-read-title': row.read }">{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ typeLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="分类" width="100">
            <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{
                statusLabel(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发生时间" width="160">
            <template #default="{ row }">{{ formatTime(row.occurredAt) }}</template>
          </el-table-column>
          <el-table-column label="指派人" width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.assigneeName || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        class="panel-pagination"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadEvents"
        @size-change="search"
      />
    </article>

    <el-drawer
      v-model="detailVisible"
      :title="detail?.event.title || '事件详情'"
      size="580px"
      destroy-on-close
    >
      <div v-if="detailLoading" class="msg-detail-loading">加载中…</div>
      <div v-else-if="detail" class="msg-detail">
        <div class="msg-detail-tags">
          <el-tag :type="levelTag(detail.event.level)" size="small">
            {{ levelLabel(detail.event.level) }}
          </el-tag>
          <el-tag :type="statusTag(detail.event.status)" size="small">
            {{ statusLabel(detail.event.status) }}
          </el-tag>
          <span class="msg-detail-tag-plain">{{ typeLabel(detail.event) }}</span>
          <span class="msg-detail-tag-plain">{{ categoryLabel(detail.event.category) }}</span>
        </div>

        <div class="msg-detail-body">{{ detail.event.body }}</div>

        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="发生时间">
            {{ formatTime(detail.event.occurredAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.event.dueAt" label="截止时间">
            {{ formatTime(detail.event.dueAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="指派人">
            {{ detail.event.assigneeName || '未指派' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建">
            {{ detail.event.createdByName || '系统巡检' }} ·
            {{ formatTime(detail.event.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.event.resolvedAt" label="消解/完成">
            {{ detail.event.resolvedByName || '系统自动' }} ·
            {{ formatTime(detail.event.resolvedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="已读">
            {{ detail.event.readAt ? formatTime(detail.event.readAt) : '本次打开已记已读' }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="detail.event.actionUrl" class="msg-detail-action">
          <el-button type="primary" plain @click="openActionUrl(detail.event.actionUrl)">
            打开关联页面
          </el-button>
        </div>

        <section class="msg-timeline">
          <h4>处理时间线</h4>
          <el-empty
            v-if="detail.timeline.length === 0"
            description="系统巡检事件没有人工流转记录；条件消失后会自动消解。"
            :image-size="60"
          />
          <el-timeline v-else>
            <el-timeline-item
              v-for="item in detail.timeline"
              :key="item.id"
              :timestamp="formatTime(item.createdAt)"
              placement="top"
            >
              <div class="msg-timeline-row">
                <strong>{{ item.userName || '系统' }}</strong>
                <span>{{ item.content }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
        </section>

        <div v-if="canWrite" class="msg-detail-footer">
          <el-button v-if="canEdit(detail.event)" type="primary" @click="openEdit(detail.event)">
            编辑事项
          </el-button>
          <el-button @click="openNotify(detail.event)">提醒某人</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '新建事项' : '编辑事项'"
      width="760px"
      top="5vh"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        class="education-form"
        scroll-to-error
        @submit.prevent
      >
        <div class="msg-form-grid">
          <el-form-item label="事项类型" prop="typeCode">
            <el-select
              v-model="form.typeCode"
              filterable
              placeholder="选择任务/反馈/问题"
              :disabled="formMode === 'edit'"
            >
              <el-option
                v-for="item in creatableTypes"
                :key="item.typeCode"
                :label="item.typeName"
                :value="item.typeCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="级别" prop="level">
            <el-select v-model="form.level">
              <el-option
                v-for="item in MESSAGE_LEVEL_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            maxlength="200"
            show-word-limit
            placeholder="一句话说清要做什么"
          />
        </el-form-item>

        <el-form-item label="正文" prop="body">
          <el-input
            v-model="form.body"
            type="textarea"
            :rows="5"
            maxlength="5000"
            show-word-limit
            placeholder="背景、期望结果、需要谁配合；正文按纯文本保存"
          />
        </el-form-item>

        <div class="msg-form-grid">
          <el-form-item label="关联班级（可选）">
            <el-select
              v-model="form.classId"
              filterable
              remote
              clearable
              :remote-method="searchClasses"
              :loading="classLoading"
              placeholder="输入班级名称搜索"
              @change="onClassChange"
            >
              <el-option
                v-for="item in classOptions"
                :key="item.id"
                :label="item.className || item.classCode || item.id"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关联学员（可选）">
            <el-select
              v-model="form.studentId"
              filterable
              remote
              clearable
              :remote-method="searchStudents"
              :loading="studentLoading"
              placeholder="输入学员姓名搜索"
            >
              <el-option
                v-for="item in studentOptions"
                :key="item.id"
                :label="item.studentName || item.studentNo || item.id"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </div>

        <div class="msg-form-grid">
          <el-form-item label="指派人（可选）">
            <el-select v-model="form.assigneeUserId" filterable clearable placeholder="选择负责人">
              <el-option
                v-for="item in assignees"
                :key="item.userId"
                :label="assigneeLabel(item)"
                :value="item.userId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="截止时间（可选）">
            <el-date-picker
              v-model="form.dueAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="选择截止时间"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item v-if="formMode === 'edit'" label="状态">
          <el-select v-model="form.status">
            <el-option
              v-for="item in MESSAGE_MANUAL_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="formMode === 'edit'" label="流转说明">
          <el-input
            v-model="form.comment"
            type="textarea"
            :rows="2"
            maxlength="1000"
            show-word-limit
            placeholder="写清这次改了什么/为什么完成，会进时间线"
          />
        </el-form-item>

        <el-form-item label="深链（可选）">
          <el-input
            v-model="form.actionUrl"
            maxlength="500"
            placeholder="例如 /fee-ledger?ledgerId=xxx；只允许本系统页面路径"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formSaving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="notifyVisible" title="提醒某人查看" width="520px">
      <el-form label-position="top">
        <el-form-item label="被提醒人">
          <el-select
            v-model="notifyUserIds"
            multiple
            filterable
            placeholder="选择本机构员工"
            style="width: 100%"
          >
            <el-option
              v-for="item in assignees"
              :key="item.userId"
              :label="assigneeLabel(item)"
              :value="item.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="留言（可选）">
          <el-input
            v-model="notifyNote"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：这条欠费请今天跟进一下"
          />
        </el-form-item>
        <el-alert
          title="提醒会给每个人生成一条指派给 TA 的提醒事件，不改变原事件状态；对方在「我关心的」里一定能看到。"
          type="info"
          :closable="false"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="notifyVisible = false">取消</el-button>
        <el-button type="primary" :loading="notifySaving" @click="submitNotify">发送提醒</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import NoteHint from '@cornerstone/core/components/NoteHint.vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  fetchClassOptions,
  fetchStudentOptions,
  type ClassOption,
  type StudentOption,
} from '@cornerstone/module-education/api/education'
import {
  MESSAGE_LEVEL_OPTIONS,
  MESSAGE_MANUAL_STATUS_OPTIONS,
  MESSAGE_STATUS_FILTER_OPTIONS,
  createManualMessage,
  fetchMessageAssignees,
  fetchMessageDetail,
  fetchMessageStream,
  fetchMessageTypeOptions,
  fetchMyMessagePage,
  notifyMessageUsers,
  updateManualMessage,
  type MessageAssignee,
  type MessageEventDetail,
  type MessageEventRecord,
  type MessageTypeOption,
} from '@cornerstone/module-education/api/message'

const auth = useAuthStore()
const dictStore = useDictStore()
const institutionStore = useInstitutionStore()
const router = useRouter()

const canWrite = computed(() => auth.hasPermission('message:item:write'))

const scopeMode = ref<'all' | 'mine'>('all')
const records = ref<MessageEventRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const pageError = ref('')

const filters = reactive({
  typeCode: '',
  category: '',
  status: 'active',
  level: '',
  dateRange: [] as string[],
  keyword: '',
  unreadOnly: false,
})

const typeOptions = ref<MessageTypeOption[]>([])
const assignees = ref<MessageAssignee[]>([])
const creatableTypes = computed(() =>
  typeOptions.value.filter((item) => item.sourceMode === 'manual' && item.creatable !== false)
)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<MessageEventDetail | null>(null)

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSaving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: '',
  typeCode: '',
  title: '',
  body: '',
  level: 'info',
  assigneeUserId: '',
  dueAt: '',
  classId: '',
  studentId: '',
  campusId: '',
  actionUrl: '',
  status: 'open',
  comment: '',
})

const formRules: FormRules = {
  typeCode: [{ required: true, message: '请选择事项类型', trigger: 'change' }],
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  body: [{ required: true, message: '请填写正文', trigger: 'blur' }],
}

const classOptions = ref<ClassOption[]>([])
const classLoading = ref(false)
const studentOptions = ref<StudentOption[]>([])
const studentLoading = ref(false)

const notifyVisible = ref(false)
const notifyUserIds = ref<string[]>([])
const notifyNote = ref('')
const notifySaving = ref(false)

onMounted(async () => {
  void dictStore.ensureLoaded(['message_category'])
  await Promise.all([loadTypeOptions(), loadAssignees(), loadEvents()])
})

async function loadTypeOptions() {
  try {
    typeOptions.value = await fetchMessageTypeOptions()
  } catch {
    typeOptions.value = []
  }
}

async function loadAssignees() {
  try {
    assignees.value = await fetchMessageAssignees()
  } catch {
    assignees.value = []
  }
}

function pageParams() {
  return {
    page: page.value,
    pageSize: pageSize.value,
    typeCode: filters.typeCode || undefined,
    category: filters.category || undefined,
    status: filters.status || undefined,
    level: filters.level || undefined,
    keyword: filters.keyword || undefined,
    dateFrom: filters.dateRange?.[0] || undefined,
    dateTo: filters.dateRange?.[1] || undefined,
    unreadOnly: filters.unreadOnly ? true : undefined,
  }
}

async function loadEvents() {
  loading.value = true
  pageError.value = ''
  try {
    const result =
      scopeMode.value === 'mine'
        ? await fetchMyMessagePage(pageParams())
        : await fetchMessageStream(pageParams())
    records.value = result.records
    total.value = result.total
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '事件流加载失败'
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void loadEvents()
}

function reload() {
  void loadEvents()
}

function resetFilters() {
  filters.typeCode = ''
  filters.category = ''
  filters.status = 'active'
  filters.level = ''
  filters.dateRange = []
  filters.keyword = ''
  filters.unreadOnly = false
  search()
}

async function openDetail(row: MessageEventRecord) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const loaded = await fetchMessageDetail(row.id)
    detail.value = loaded
    row.read = true
    row.readAt = loaded.event.readAt
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '事件不存在或已不可见')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function canEdit(event: MessageEventRecord) {
  const option = typeOptions.value.find((item) => item.typeCode === event.typeCode)
  return !!option && option.sourceMode === 'manual' && option.creatable !== false
}

function openCreate() {
  if (creatableTypes.value.length === 0) {
    ElMessage.warning('事件目录里没有可手动新建的类型')
    return
  }
  formMode.value = 'create'
  Object.assign(form, {
    id: '',
    typeCode: creatableTypes.value[0]?.typeCode || '',
    title: '',
    body: '',
    level: 'info',
    assigneeUserId: '',
    dueAt: '',
    classId: '',
    studentId: '',
    campusId: '',
    actionUrl: '',
    status: 'open',
    comment: '',
  })
  classOptions.value = []
  studentOptions.value = []
  formVisible.value = true
}

function openEdit(event: MessageEventRecord) {
  formMode.value = 'edit'
  Object.assign(form, {
    id: event.id,
    typeCode: event.typeCode,
    title: event.title,
    body: event.body,
    level: event.level || 'info',
    assigneeUserId: event.assigneeUserId || '',
    dueAt: event.dueAt || '',
    classId: event.classId || '',
    studentId: event.studentId || '',
    campusId: event.campusId || '',
    actionUrl: event.actionUrl || '',
    status: event.status || 'open',
    comment: '',
  })
  classOptions.value = []
  studentOptions.value = []
  if (event.classId) {
    void fetchClassOptions({
      institutionId: institutionStore.currentInstitutionId,
      ids: event.classId,
    }).then((options) => {
      classOptions.value = options
    })
  }
  if (event.studentId) {
    void fetchStudentOptions({
      institutionId: institutionStore.currentInstitutionId,
      ids: event.studentId,
    }).then((options) => {
      studentOptions.value = options
    })
  }
  formVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  formSaving.value = true
  try {
    if (formMode.value === 'create') {
      await createManualMessage({
        typeCode: form.typeCode,
        title: form.title,
        body: form.body,
        level: form.level,
        assigneeUserId: form.assigneeUserId || undefined,
        dueAt: form.dueAt || undefined,
        classId: form.classId || undefined,
        studentId: form.studentId || undefined,
        campusId: form.campusId || undefined,
        actionUrl: form.actionUrl || undefined,
      })
      ElMessage.success('事项已创建')
    } else {
      await updateManualMessage(form.id, {
        title: form.title,
        body: form.body,
        level: form.level,
        assigneeUserId: form.assigneeUserId || undefined,
        dueAt: form.dueAt || undefined,
        status: form.status,
        comment: form.comment || undefined,
        actionUrl: form.actionUrl || undefined,
      })
      ElMessage.success('事项已更新')
    }
    formVisible.value = false
    detailVisible.value = false
    await loadEvents()
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '保存失败')
  } finally {
    formSaving.value = false
  }
}

function openNotify(event: MessageEventRecord) {
  notifyUserIds.value = []
  notifyNote.value = `请查看并处理：${event.title}`
  notifyVisible.value = true
}

async function submitNotify() {
  if (!detail.value || notifyUserIds.value.length === 0) {
    ElMessage.warning('请选择至少一位被提醒人')
    return
  }
  notifySaving.value = true
  try {
    const count = await notifyMessageUsers(detail.value.event.id, {
      userIds: notifyUserIds.value,
      note: notifyNote.value || undefined,
    })
    ElMessage.success(`已给 ${count} 人生成提醒`)
    notifyVisible.value = false
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '提醒发送失败')
  } finally {
    notifySaving.value = false
  }
}

function openActionUrl(url?: string | null) {
  if (!url) {
    return
  }
  void router.push(url)
}

async function searchClasses(keyword: string) {
  classLoading.value = true
  try {
    classOptions.value = await fetchClassOptions({
      institutionId: institutionStore.currentInstitutionId,
      keyword: keyword || undefined,
    })
  } finally {
    classLoading.value = false
  }
}

async function searchStudents(keyword: string) {
  studentLoading.value = true
  try {
    studentOptions.value = await fetchStudentOptions({
      institutionId: institutionStore.currentInstitutionId,
      keyword: keyword || undefined,
    })
  } finally {
    studentLoading.value = false
  }
}

function onClassChange(classId: string) {
  const option = classOptions.value.find((item) => item.id === classId)
  form.campusId = option?.campusId || ''
}

function assigneeLabel(item: MessageAssignee) {
  return item.username
    ? `${item.displayName || item.username}（${item.username}）`
    : item.displayName || item.userId
}

function typeLabel(record: MessageEventRecord) {
  return (
    record.typeName ||
    typeOptions.value.find((item) => item.typeCode === record.typeCode)?.typeName ||
    record.typeCode
  )
}

function categoryLabel(value?: string | null) {
  return dictStore.displayLabel('message_category', value)
}

function levelLabel(value?: string | null) {
  return MESSAGE_LEVEL_OPTIONS.find((item) => item.value === value)?.label || value || '提示'
}

function levelTag(value?: string | null) {
  return MESSAGE_LEVEL_OPTIONS.find((item) => item.value === value)?.tag || 'info'
}

function statusLabel(value?: string | null) {
  const map: Record<string, string> = {
    active: '待处理',
    open: '待处理',
    in_progress: '处理中',
    done: '已完成',
    closed: '已关闭',
    resolved: '已消解',
  }
  return map[value || ''] || value || '—'
}

function statusTag(value?: string | null) {
  const map: Record<string, string> = {
    active: 'warning',
    open: 'warning',
    in_progress: 'primary',
    done: 'success',
    closed: 'info',
    resolved: 'success',
  }
  return map[value || ''] || 'info'
}

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}
</script>

<style scoped>
.msg-unread-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  background: var(--accent);
  vertical-align: middle;
}

.msg-read-title {
  color: var(--text-secondary);
}

.msg-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.msg-detail-loading {
  padding: 40px 0;
  text-align: center;
  color: var(--text-muted);
}

.msg-detail-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.msg-detail-tag-plain {
  padding: 0 7px;
  border-radius: 4px;
  background: var(--tint-bg);
  color: var(--tint-ink);
  font-size: 12px;
  line-height: 20px;
}

.msg-detail-body {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--subtle-bg);
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--text-secondary);
  white-space: pre-wrap;
}

.msg-detail-action {
  display: flex;
  justify-content: flex-start;
}

.msg-timeline h4 {
  margin: 4px 0 12px;
  font-size: 13px;
  color: var(--text-primary);
}

.msg-timeline-row {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.msg-detail-footer {
  display: flex;
  gap: 8px;
  padding-top: 4px;
}

.msg-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

@media (max-width: 720px) {
  .msg-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
