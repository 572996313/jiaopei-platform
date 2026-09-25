<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <article class="panel-card education-panel">
      <div class="education-context-text">
        公告是<b>人工撰写、按范围广播</b>的单向通知，与系统自动产生的课次变动提示不是一回事。
        <b>发布后正文冻结</b>：要改内容请撤回后另建一条，已发布与已撤回的记录都不再变动。
        <b>阅读进度的分母在发布时冻结</b>（= 当时解析出的接收人数），之后教师离职、学员退班都不会改分母，
        所以"未读"里可能包含已经收不到消息的人。撤回只让接收端立刻看不见，接收记录仍然留着。
      </div>

      <!-- 本表主操作:表格上方一行、左对齐,排在查询条件上面 -->
      <div class="table-toolbar">
        <el-button v-if="canWrite" type="primary" @click="openForm()">新建公告</el-button>
        <el-button @click="templateVisible = true">模板维护</el-button>
        <el-button :icon="Refresh" circle :loading="loading" @click="loadAnnouncements" />
      </div>

      <div class="table-filters">
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 140px" @change="search">
          <el-option
            v-for="item in ANNOUNCEMENT_STATUS_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <DictSelect
          v-model="filters.typeCode"
          type-code="announcement_type"
          placeholder="公告类型"
          clearable
          style="width: 180px"
          @change="search"
        />
        <el-select
          v-model="filters.audienceCode"
          placeholder="接收对象"
          clearable
          style="width: 150px"
          @change="search"
        >
          <el-option
            v-for="item in AUDIENCE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="标题关键字"
          clearable
          style="width: 220px"
          @keyup.enter="search"
          @clear="search"
        />
      </div>

      <TableFillBox v-slot="{ height }">
        <el-table
          :data="records"
          border
          stripe
          row-key="id"
          highlight-current-row
          v-loading="loading"
          :height="height"
        >
          <el-table-column label="标题" min-width="240" fixed="left" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-if="row.top" type="danger" size="small" class="ann-top-tag">置顶</el-tag>
              <span>{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ typeLabel(row.typeCode) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="接收对象" min-width="180">
            <template #default="{ row }">
              <span v-if="!row.audienceCodes?.length" class="ann-muted">未选择</span>
              <span v-else>{{ audienceText(row.audienceCodes) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="范围" min-width="150">
            <template #default="{ row }">{{ scopeText(row) }}</template>
          </el-table-column>
          <el-table-column label="阅读进度" width="150" align="right">
            <template #default="{ row }">
              <span v-if="row.status === 'draft'" class="ann-muted">未发布</span>
              <span v-else>{{ row.readCount ?? 0 }} / {{ row.receiverTotal ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="发布 / 撤回" min-width="170">
            <template #default="{ row }">
              <div v-if="row.publishedAt">{{ row.publishedByName || '—' }} · {{ formatTime(row.publishedAt) }}</div>
              <div v-if="row.recalledAt" class="ann-muted">
                撤回 {{ row.recalledByName || '—' }} · {{ formatTime(row.recalledAt) }}
              </div>
              <span v-if="!row.publishedAt && !row.recalledAt" class="ann-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">查看</el-button>
              <template v-if="canWrite && row.status === 'draft'">
                <el-button link type="primary" @click="openForm(row)">编辑</el-button>
                <el-button link type="primary" @click="confirmPublish(row)">发布</el-button>
                <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
              </template>
              <el-button
                v-else-if="canWrite && row.status === 'published'"
                link
                type="warning"
                @click="confirmRecall(row)"
                >撤回</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div v-if="!loading && records.length === 0" class="education-empty">
        当前筛选范围下还没有公告。新建一条草稿后，用「发布」才会让接收端看到。
      </div>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        class="panel-pagination"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadAnnouncements"
        @size-change="search"
      />
    </article>

    <el-dialog
      v-model="formVisible"
      :title="formTitle"
      width="760px"
      top="6vh"
      :close-on-click-modal="false"
    >
      <div class="ann-form-body">
        <el-alert
          v-if="formError"
          :title="formError"
          type="error"
          show-icon
          :closable="false"
          class="education-alert"
        />

        <el-form label-position="top" class="education-form">
          <el-form-item label="套用模板">
            <el-select v-model="templateId" clearable filterable placeholder="不套用，从零写" @change="applyTemplate">
              <el-option v-for="item in templates" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <div class="ann-field-hint">
              套用只把模板的标题、正文与范围预填进下面这张表单，套完照样能改；模板备注不会带进公告。
            </div>
          </el-form-item>
          <el-form-item label="公告标题" required>
            <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="例如：国庆放假安排" />
          </el-form-item>
          <el-form-item label="公告类型" required>
            <DictSelect
              v-model="form.typeCode"
              type-code="announcement_type"
              placeholder="选择公告类型"
            />
          </el-form-item>
          <el-form-item label="正文" required>
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="8"
              maxlength="2000"
              show-word-limit
              placeholder="发布时会按原文冻结快照，不做变量渲染"
            />
          </el-form-item>
          <el-form-item label="接收对象" required>
            <el-checkbox-group v-model="form.audienceCodes">
              <el-checkbox v-for="item in AUDIENCE_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </el-checkbox>
            </el-checkbox-group>
            <div class="ann-field-hint">
              教师取已绑定登录账号的档案，家长取在读学员绑定的账号，「全部登录账号」不受校区限制。
            </div>
          </el-form-item>
          <el-form-item label="校区范围">
            <el-radio-group v-model="form.campusScope">
              <el-radio value="all">全部校区</el-radio>
              <el-radio value="part">指定校区</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.campusScope === 'part'" label="指定校区">
            <el-select v-model="form.campusIds" multiple filterable placeholder="选择一个或多个校区">
              <el-option
                v-for="item in campuses"
                :key="item.id"
                :label="item.campusName || item.campusCode || `校区 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="限定班级（留空=按校区全发家长）">
            <el-select v-model="form.classIds" multiple filterable clearable placeholder="不限班级">
              <el-option
                v-for="item in classes"
                :key="item.id"
                :label="item.className || item.classCode || `班级 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="只在管理端可见" />
          </el-form-item>
          <el-form-item label="置顶">
            <el-switch v-model="form.top" />
            <span class="ann-field-hint">置顶的公告在接收端排最前</span>
          </el-form-item>
        </el-form>

        <div class="ann-preview">
          <div class="ann-preview-head">
            <span>预计触达</span>
            <el-button size="small" :loading="previewing" @click="runPreview">按当前范围试算</el-button>
          </div>
          <div v-if="preview" class="ann-preview-body">
            <div class="ann-preview-total">{{ preview.total }} 人</div>
            <div class="ann-preview-split">
              教师 {{ preview.teacherCount }} · 家长 {{ preview.parentCount }} · 全部账号 {{ preview.accountCount }}
            </div>
            <ul v-if="preview.notices.length" class="ann-preview-notices">
              <li v-for="item in preview.notices" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div v-else class="ann-preview-body ann-muted">
            填好接收对象后点「按当前范围试算」。范围为 0 人时发布会被拒绝。
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button :loading="saving" @click="submit(false)">保存草稿</el-button>
        <el-button type="primary" :loading="publishing" @click="submit(true)">保存并发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="公告详情" width="680px">
      <div v-if="detail" class="ann-detail">
        <h3 class="ann-detail-title">
          <el-tag v-if="detail.top" type="danger" size="small" class="ann-top-tag">置顶</el-tag>
          {{ detail.title }}
        </h3>
        <div class="ann-detail-meta">
          {{ typeLabel(detail.typeCode) }} · {{ statusLabel(detail.status) }} ·
          {{ audienceText(detail.audienceCodes ?? []) }} · {{ scopeText(detail) }}
        </div>
        <p class="ann-detail-content">{{ detail.content }}</p>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="接收人数（分母）">{{ detail.receiverTotal ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="已读人数">{{ detail.readCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="发布">
            {{ detail.publishedAt ? `${detail.publishedByName || '—'} · ${formatTime(detail.publishedAt)}` : '未发布' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.recalledAt" label="撤回">
            {{ `${detail.recalledByName || '—'} · ${formatTime(detail.recalledAt)} · ${detail.recallReason || '未填原因'}` }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注">{{ detail.remark }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <AnnouncementTemplateDialog v-model="templateVisible" />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import AnnouncementTemplateDialog from '@cornerstone/module-education/views/announcement/AnnouncementTemplateDialog.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { fetchCampuses, type CampusSummary } from '@cornerstone/module-organization/api/organization'
import { fetchTeachingClasses, type TeachingClassSummary } from '@cornerstone/module-education/api/education'
import {
  ANNOUNCEMENT_STATUS_OPTIONS,
  AUDIENCE_OPTIONS,
  createAnnouncement,
  deleteAnnouncement,
  fetchAnnouncement,
  fetchAnnouncementPage,
  fetchAnnouncementTemplates,
  previewAnnouncementRecipients,
  publishAnnouncement,
  recallAnnouncement,
  updateAnnouncement,
  type AnnouncementPayload,
  type AnnouncementRecipientPreview,
  type AnnouncementRecord,
  type AnnouncementTemplate,
} from '@cornerstone/module-education/api/announcement'

const auth = useAuthStore()
const dictStore = useDictStore()
const institutionStore = useInstitutionStore()

const institutionId = computed(() => institutionStore.currentInstitutionId)
const canWrite = computed(() => auth.hasPermission('announcement:write'))

const records = ref<AnnouncementRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const pageError = ref('')
const campuses = ref<CampusSummary[]>([])
const classes = ref<TeachingClassSummary[]>([])

const filters = reactive({ status: '', typeCode: '', audienceCode: '', keyword: '' })

const formVisible = ref(false)
const templateVisible = ref(false)
const detailVisible = ref(false)
const detail = ref<AnnouncementRecord | null>(null)
const editingId = ref<string | null>(null)
const saving = ref(false)
const publishing = ref(false)
const previewing = ref(false)
const formError = ref('')
const preview = ref<AnnouncementRecipientPreview | null>(null)
const templates = ref<AnnouncementTemplate[]>([])
const templateId = ref<string | null>(null)

const form = reactive<AnnouncementPayload>({
  title: '',
  content: '',
  typeCode: '',
  audienceCodes: [],
  campusScope: 'all',
  campusIds: [],
  classIds: [],
  top: false,
  remark: '',
})

const formTitle = computed(() => (editingId.value ? '编辑公告草稿' : '新建公告'))

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function typeLabel(value?: string | null) {
  return dictStore.displayLabel('announcement_type', value)
}

function statusOption(value?: string | null) {
  return ANNOUNCEMENT_STATUS_OPTIONS.find((item) => item.value === value)
}

function statusLabel(value?: string | null) {
  return statusOption(value)?.label ?? value ?? '—'
}

function statusTag(value?: string | null) {
  return statusOption(value)?.tag ?? 'info'
}

function audienceText(values: string[]) {
  return values.map((value) => AUDIENCE_OPTIONS.find((item) => item.value === value)?.label ?? value).join('、')
}

function scopeText(row: AnnouncementRecord) {
  const parts: string[] = []
  parts.push(
    row.campusScope === 'part' ? `${row.campusIds?.length ?? 0} 个校区` : '全校区'
  )
  if (row.classIds?.length) {
    parts.push(`${row.classIds.length} 个班级`)
  }
  return parts.join(' · ')
}

async function loadAnnouncements() {
  loading.value = true
  pageError.value = ''
  try {
    const result = await fetchAnnouncementPage({
      status: filters.status || undefined,
      typeCode: filters.typeCode || undefined,
      audienceCode: filters.audienceCode || undefined,
      keyword: filters.keyword.trim() || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    records.value = result.records
    total.value = result.total
  } catch (error) {
    records.value = []
    total.value = 0
    pageError.value = toMessage(error, '公告列表加载失败')
  } finally {
    loading.value = false
  }
}

/** 筛选条件变化后回到第一页，否则会停在旧页码上看到空表 */
function search() {
  page.value = 1
  return loadAnnouncements()
}

async function loadLookups() {
  if (!institutionId.value) {
    campuses.value = []
    classes.value = []
    return
  }
  try {
    const [campusList, classList] = await Promise.all([
      fetchCampuses(institutionId.value),
      fetchTeachingClasses({ institutionId: institutionId.value }),
    ])
    campuses.value = campusList
    classes.value = classList
  } catch (error) {
    pageError.value = toMessage(error, '校区与班级数据加载失败')
  }
}

function resetForm(row?: AnnouncementRecord) {
  templateId.value = null
  form.title = row?.title ?? ''
  form.content = row?.content ?? ''
  form.typeCode = row?.typeCode ?? ''
  form.audienceCodes = [...(row?.audienceCodes ?? [])]
  form.campusScope = row?.campusScope === 'part' ? 'part' : 'all'
  form.campusIds = [...(row?.campusIds ?? [])]
  form.classIds = [...(row?.classIds ?? [])]
  form.top = Boolean(row?.top)
  form.remark = row?.remark ?? ''
  formError.value = ''
  preview.value = null
}

function openForm(row?: AnnouncementRecord) {
  editingId.value = row?.id ?? null
  resetForm(row)
  formVisible.value = true
  // 模板可能刚在「模板维护」里改过，每次开表单重拉一次，套用的下拉才不会列出已删的模板
  loadTemplates()
}

async function loadTemplates() {
  try {
    templates.value = await fetchAnnouncementTemplates()
  } catch {
    // 拉不到模板只是少了预填入口，公告本身照样能写
    templates.value = []
  }
}

/** 套用 = 把模板的范围与正文搬进当前表单，不是绑定：之后表单怎么改都不回写模板 */
function applyTemplate(id: string | null) {
  const template = templates.value.find((item) => item.id === id)
  if (!template) {
    return
  }
  form.title = template.title
  form.content = template.content
  form.typeCode = template.typeCode ?? ''
  form.audienceCodes = [...(template.audienceCodes ?? [])]
  form.campusScope = template.campusScope === 'part' ? 'part' : 'all'
  form.campusIds = [...(template.campusIds ?? [])]
  form.classIds = [...(template.classIds ?? [])]
  form.top = Boolean(template.top)
  preview.value = null
  formError.value = ''
}

async function openDetail(row: AnnouncementRecord) {
  try {
    detail.value = await fetchAnnouncement(row.id)
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(toMessage(error, '公告详情加载失败'))
  }
}

function payload(): AnnouncementPayload {
  return {
    title: form.title.trim(),
    content: form.content.trim(),
    typeCode: form.typeCode,
    audienceCodes: form.audienceCodes,
    campusScope: form.campusScope,
    campusIds: form.campusScope === 'part' ? form.campusIds : [],
    classIds: form.classIds,
    top: form.top,
    remark: form.remark?.trim() || undefined,
  }
}

function validateForm() {
  if (!form.title.trim()) {
    formError.value = '请填写公告标题'
    return false
  }
  if (!form.content.trim()) {
    formError.value = '请填写公告正文'
    return false
  }
  if (!form.typeCode) {
    formError.value = '请选择公告类型'
    return false
  }
  formError.value = ''
  return true
}

async function runPreview() {
  if (!validateForm()) {
    return
  }
  previewing.value = true
  try {
    preview.value = await previewAnnouncementRecipients(payload())
  } catch (error) {
    preview.value = null
    formError.value = toMessage(error, '触达人数试算失败')
  } finally {
    previewing.value = false
  }
}

async function submit(andPublish: boolean) {
  if (!validateForm()) {
    return
  }
  const busy = andPublish ? publishing : saving
  busy.value = true
  formError.value = ''
  try {
    const saved = editingId.value
      ? await updateAnnouncement(editingId.value, payload())
      : await createAnnouncement(payload())
    // 新建成功后记住这条草稿：发布被拒时再点一次是改范围重发，不是又建一条
    editingId.value = saved.id
    if (andPublish) {
      await publishAnnouncement(saved.id)
      ElMessage.success('公告已发布')
    } else {
      ElMessage.success('草稿已保存')
    }
    formVisible.value = false
    await loadAnnouncements()
  } catch (error) {
    // 发布被拒（例如范围解析出 0 人）时弹窗不关，操作员可以直接改范围重试
    formError.value = toMessage(error, andPublish ? '发布失败' : '保存失败')
  } finally {
    busy.value = false
  }
}

async function confirmPublish(row: AnnouncementRecord) {
  try {
    await ElMessageBox.confirm(
      `确定发布「${row.title}」吗？发布后正文冻结，接收端立即可见。`,
      '发布确认',
      { type: 'warning', confirmButtonText: '发布', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await publishAnnouncement(row.id)
    ElMessage.success('公告已发布')
    await loadAnnouncements()
  } catch (error) {
    ElMessage.error(toMessage(error, '发布失败'))
  }
}

async function confirmRecall(row: AnnouncementRecord) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `撤回后「${row.title}」在接收端立即不可见，接收记录保留。请填写撤回原因。`,
      '撤回公告',
      {
        confirmButtonText: '撤回',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：日期写错，另发一条更正公告',
        inputValidator: (value: string) => (value && value.trim() ? true : '撤回原因不能为空'),
      }
    )
    reason = String(result.value ?? '').trim()
  } catch {
    return
  }
  try {
    await recallAnnouncement(row.id, reason)
    ElMessage.success('公告已撤回')
    await loadAnnouncements()
  } catch (error) {
    ElMessage.error(toMessage(error, '撤回失败'))
  }
}

async function confirmDelete(row: AnnouncementRecord) {
  try {
    await ElMessageBox.confirm(`确定删除草稿「${row.title}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteAnnouncement(row.id)
    ElMessage.success('草稿已删除')
    await loadAnnouncements()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除失败'))
  }
}

watch(
  () => institutionStore.currentInstitutionId,
  async () => {
    filters.status = ''
    filters.typeCode = ''
    filters.audienceCode = ''
    filters.keyword = ''
    await loadLookups()
    await search()
  }
)

onMounted(() => {
  // 表格列的类型中文名要在进页面时就拉好，DictSelect 只会为表单自己拉
  void dictStore.ensureLoaded(['announcement_type']).catch(() => undefined)
  loadLookups()
  loadAnnouncements()
})
</script>

<style scoped>
/* 正文表单比视口高，滚这一层而不是滚整个弹窗：底部三个按钮要一直在视口里。
   写成 :deep(.el-dialog__body) 是无效的——弹窗面板元素由 ElDialog 自己的模板渲染，不带本组件的 scope 属性 */
.ann-form-body {
  max-height: 66vh;
  overflow-y: auto;
}

.ann-top-tag {
  margin-right: 6px;
}

.ann-muted {
  color: var(--text-muted);
}

.ann-field-hint {
  margin-left: 12px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.ann-preview {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px 14px;
  background: var(--subtle-bg);
}

.ann-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: var(--text-primary);
}

.ann-preview-body {
  margin-top: 8px;
}

.ann-preview-total {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
}

.ann-preview-split {
  color: var(--text-secondary);
  font-size: 13px;
}

.ann-preview-notices {
  margin: 8px 0 0;
  padding-left: 18px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.7;
}

.ann-detail-title {
  margin: 0 0 6px;
  font-size: 17px;
  color: var(--text-primary);
}

.ann-detail-meta {
  color: var(--text-secondary);
  font-size: 13px;
}

.ann-detail-content {
  margin: 14px 0 18px;
  padding: 12px 14px;
  border-radius: 6px;
  background: var(--subtle-bg);
  color: var(--text-primary);
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
