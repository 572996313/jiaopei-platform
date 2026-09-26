<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'list' ? '公告模板' : editingId ? '编辑模板' : '新增模板'"
    width="860px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @open="onDialogOpen"
  >
    <!-- 列表态：模板是"新建公告时预填"的小体量数据，量不会大，所以整页只有一张不分页的表 -->
    <template v-if="mode === 'list'">
      <div class="education-context-text">
        模板只在新建公告时<b>预填表单</b>，套完还能改；公告与模板之间没有关联字段，
        <b>改模板或删模板都不会动已经发出去的公告</b>。
      </div>

      <div class="table-toolbar">
        <el-button v-if="canWrite" type="primary" @click="openCreate">新增模板</el-button>
      </div>

      <el-table v-loading="loading" :data="templates" border stripe row-key="id" max-height="380">
        <el-table-column
          label="模板名称"
          min-width="150"
          fixed="left"
          show-overflow-tooltip
          prop="name"
          sortable
        >
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column
          label="预填标题"
          min-width="200"
          show-overflow-tooltip
          prop="title"
          sortable
        >
          <template #default="{ row }">{{ row.title }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110" prop="typeCode" sortable>
          <template #default="{ row }">{{ typeLabel(row.typeCode) }}</template>
        </el-table-column>
        <el-table-column label="接收对象" min-width="150">
          <template #default="{ row }">
            <span v-if="!row.audienceCodes?.length" class="tpl-muted">套用时再选</span>
            <span v-else>{{ audienceText(row.audienceCodes) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="范围" width="130">
          <template #default="{ row }">{{ scopeText(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <template v-if="canWrite">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
            </template>
            <span v-else class="tpl-muted">只读</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && templates.length === 0" class="education-empty">
        还没有模板。建一条把常用的标题、正文和接收范围填进去，之后新建公告直接套用。
      </div>
    </template>

    <!-- 编辑态在同一个弹窗里换内容，而不是弹窗套弹窗：叠两层后遮罩和 esc 都会关掉外层 -->
    <template v-else>
      <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form tpl-form-body">
        <el-form-item label="模板名称" required>
          <el-input v-model="form.name" maxlength="60" show-word-limit placeholder="例如：节假日放假通知" />
        </el-form-item>
        <el-form-item label="预填公告标题" required>
          <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="例如：国庆放假安排" />
        </el-form-item>
        <el-form-item label="公告类型" required>
          <DictSelect v-model="form.typeCode" type-code="announcement_type" placeholder="选择公告类型" />
        </el-form-item>
        <el-form-item label="预填正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="2000"
            show-word-limit
            placeholder="套用时原样填进公告正文，不做变量渲染"
          />
        </el-form-item>
        <el-form-item label="预填接收对象">
          <el-checkbox-group v-model="form.audienceCodes">
            <el-checkbox v-for="item in AUDIENCE_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
          <div class="tpl-field-hint">留空也可以：套用时再由操作员选，模板只负责标题与正文。</div>
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
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="模板使用说明，不会带进公告正文" />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="form.top" />
          <span class="tpl-field-hint">套用时带进公告表单，仍可取消</span>
        </el-form-item>
      </el-form>
    </template>

    <!-- footer 必须是 el-dialog 的直接插槽，写在 v-if 分支里编译不过 -->
    <template #footer>
      <el-button v-if="mode === 'list'" @click="close">关闭</el-button>
      <template v-else>
        <el-button @click="backToList">返回列表</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存模板</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { fetchCampuses, type CampusSummary } from '@cornerstone/module-organization/api/organization'
import { fetchTeachingClasses, type TeachingClassSummary } from '@cornerstone/module-education/api/education'
import {
  AUDIENCE_OPTIONS,
  createAnnouncementTemplate,
  deleteAnnouncementTemplate,
  fetchAnnouncementTemplates,
  updateAnnouncementTemplate,
  type AnnouncementTemplate,
  type AnnouncementTemplatePayload,
} from '@cornerstone/module-education/api/announcement'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const auth = useAuthStore()
const dictStore = useDictStore()
const institutionStore = useInstitutionStore()

// 写操作仍由后端 @RequiresPermission 硬拦，这里只是不给没有写权限的人看按钮
const canWrite = computed(() => auth.hasPermission('announcement:write'))

const mode = ref<'list' | 'form'>('list')
const loading = ref(false)
const saving = ref(false)
const templates = ref<AnnouncementTemplate[]>([])
const editingId = ref<string | null>(null)
const formError = ref('')
const campuses = ref<CampusSummary[]>([])
const classes = ref<TeachingClassSummary[]>([])

const form = reactive<AnnouncementTemplatePayload>({
  name: '',
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

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function typeLabel(value?: string | null) {
  return dictStore.displayLabel('announcement_type', value)
}

function audienceText(values: string[]) {
  return values.map((value) => AUDIENCE_OPTIONS.find((item) => item.value === value)?.label ?? value).join('、')
}

function scopeText(row: AnnouncementTemplate) {
  const parts = [row.campusScope === 'part' ? `${row.campusIds?.length ?? 0} 个校区` : '全校区']
  if (row.classIds?.length) {
    parts.push(`${row.classIds.length} 个班级`)
  }
  return parts.join(' · ')
}

function close() {
  emit('update:modelValue', false)
}

/** 每次打开都重新拉：模板可能刚在别的窗口改过，列表也要跟着最新 */
async function onDialogOpen() {
  mode.value = 'list'
  await dictStore.ensureLoaded(['announcement_type']).catch(() => undefined)
  await Promise.all([loadTemplates(), loadLookups()])
}

async function loadTemplates() {
  loading.value = true
  try {
    templates.value = await fetchAnnouncementTemplates()
  } catch (error) {
    templates.value = []
    ElMessage.error(toMessage(error, '模板列表加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadLookups() {
  const institutionId = institutionStore.currentInstitutionId
  if (!institutionId) {
    campuses.value = []
    classes.value = []
    return
  }
  try {
    const [campusList, classList] = await Promise.all([
      fetchCampuses(institutionId),
      fetchTeachingClasses({ institutionId }),
    ])
    campuses.value = campusList
    classes.value = classList
  } catch {
    // 范围下拉拉不到只影响新建/编辑，模板列表照样能看，所以这里不弹错
    campuses.value = []
    classes.value = []
  }
}

function resetForm(row?: AnnouncementTemplate) {
  form.name = row?.name ?? ''
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
}

function openCreate() {
  editingId.value = null
  resetForm()
  mode.value = 'form'
}

function openEdit(row: AnnouncementTemplate) {
  editingId.value = row.id
  resetForm(row)
  mode.value = 'form'
}

function backToList() {
  mode.value = 'list'
  editingId.value = null
  formError.value = ''
}

function validate() {
  if (!form.name.trim()) {
    formError.value = '请填写模板名称'
    return false
  }
  if (!form.title.trim()) {
    formError.value = '请填写预填公告标题'
    return false
  }
  if (!form.content.trim()) {
    formError.value = '请填写预填正文：空正文的模板套出来还要重写，等于没省时间'
    return false
  }
  if (!form.typeCode) {
    formError.value = '请选择公告类型'
    return false
  }
  if (form.campusScope === 'part' && !form.campusIds?.length) {
    formError.value = '指定校区范围至少要选一个校区'
    return false
  }
  formError.value = ''
  return true
}

async function submit() {
  if (!validate()) {
    return
  }
  saving.value = true
  try {
    const payload: AnnouncementTemplatePayload = {
      name: form.name.trim(),
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
    if (editingId.value) {
      await updateAnnouncementTemplate(editingId.value, payload)
    } else {
      await createAnnouncementTemplate(payload)
    }
    ElMessage.success('模板已保存')
    backToList()
    await loadTemplates()
  } catch (error) {
    // 校验被拒（类型停用、校区不属于本机构）时停在表单里改，不丢已写的正文
    formError.value = toMessage(error, '模板保存失败')
  } finally {
    saving.value = false
  }
}

async function confirmDelete(row: AnnouncementTemplate) {
  try {
    await ElMessageBox.confirm(
      `确定删除模板「${row.name}」吗？已按它发过的公告不受影响。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteAnnouncementTemplate(row.id)
    ElMessage.success('模板已删除')
    await loadTemplates()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除失败'))
  }
}
</script>

<style scoped>
/* 表单比视口高时只滚表单，错误提示与底部按钮都留在原位（:deep(.el-dialog__body) 是无效的，弹窗面板不带本组件的 scope 属性） */
.tpl-form-body {
  max-height: 66vh;
  overflow-y: auto;
}

.tpl-muted {
  color: var(--text-muted);
}

.tpl-field-hint {
  margin-left: 12px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}
</style>
