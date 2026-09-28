<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 这一页只管班型本身：课程与课程分类在「课程档案」，用班型的地方是「班级档案」 -->
    <div class="table-toolbar">
      <el-button v-if="canManage" type="primary" @click="openDialog('create')">新增班型</el-button>
      <el-button :icon="Refresh" circle :loading="loading" @click="loadClassTypes" />
      <el-button @click="goCourses">课程档案</el-button>
      <span class="education-context-text">
        班型在「班级档案」建班时选择；人数上限走班级自己的容量字段，改班型不会回写已建班级。
      </span>
    </div>

    <article class="panel-card education-panel">
      <!-- 班型是机构级小档案,一次性全量加载,排序走 Element Plus 本地比较、不发请求 -->
      <TableFillBox v-slot="{ height }">
        <el-table
          :data="classTypes"
          border
          row-key="id"
          highlight-current-row
          v-loading="loading"
          :height="height"
        >
          <el-table-column prop="typeCode" label="班型编码" width="150" fixed="left" sortable />
          <el-table-column
            prop="typeName"
            label="班型名称"
            min-width="180"
            show-overflow-tooltip
            fixed="left"
            sortable
          />
          <el-table-column label="授课方式" width="140" prop="teachingMethod" sortable>
            <template #default="{ row }">{{ teachingMethodLabel(row.teachingMethod) }}</template>
          </el-table-column>
          <el-table-column label="人数区间" width="120" align="right" sortable>
            <template #default="{ row }"
              >{{ row.minStudents ?? '-' }} ~ {{ row.maxStudents ?? '-' }}</template
            >
          </el-table-column>
          <el-table-column label="状态" width="100" prop="status" sortable>
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{
                enabledStatusLabel(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column v-if="canManage" label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDialog('edit', row)">编辑</el-button>
              <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div v-if="!loading && classTypes.length === 0" class="education-empty">
        当前机构还没有班型，点击「新增班型」录入。
      </div>
    </article>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增班型' : '编辑班型'"
      width="560px"
    >
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="班型编码">
          <CodeInput v-model="form.typeCode" resource="class_type" :mode="dialogMode" />
        </el-form-item>
        <el-form-item label="班型名称"><el-input v-model="form.typeName" /></el-form-item>
        <el-form-item label="授课方式"
          ><DictSelect v-model="form.teachingMethod" type-code="teaching_method"
        /></el-form-item>
        <el-form-item label="最少人数"
          ><el-input-number v-model="form.minStudents" :min="0" style="width: 100%"
        /></el-form-item>
        <el-form-item label="最多人数"
          ><el-input-number v-model="form.maxStudents" :min="0" style="width: 100%"
        /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"
          ><el-input v-model="form.remark" type="textarea" :rows="2"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">{{
          dialogMode === 'create' ? '提交班型' : '保存修改'
        }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import CodeInput from '@cornerstone/core/components/CodeInput.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  createClassType,
  deleteClassType,
  fetchClassTypes,
  updateClassType,
  type ClassTypeRecord,
} from '@cornerstone/module-education/api/education'

const router = useRouter()
const institutionStore = useInstitutionStore()
const dictStore = useDictStore()
const auth = useAuthStore()
const institutionId = computed(() => institutionStore.currentInstitutionId)

// 与课程、课程分类共用同一个写码（后端 @RequiresPermission("education:catalog:write")）
const canManage = computed(() => auth.hasPermission('education:catalog:write'))

const classTypes = ref<ClassTypeRecord[]>([])
const loading = ref(false)
const pageError = ref('')

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<string | null>(null)
const submitting = ref(false)
const formError = ref('')

const form = reactive({
  typeCode: '',
  typeName: '',
  teachingMethod: '',
  minStudents: 1,
  maxStudents: 12,
  status: 'active',
  remark: '',
})

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function statusTagType(value?: string | null) {
  return value === 'active' ? 'success' : 'info'
}

function enabledStatusLabel(value?: string | null) {
  if (value === 'inactive') {
    return '停用'
  }
  if (value === 'active') {
    return '启用'
  }
  return value || '-'
}

function teachingMethodLabel(value?: string | null) {
  return dictStore.displayLabel('teaching_method', value)
}

function resetForm() {
  form.typeCode = ''
  form.typeName = ''
  form.teachingMethod = ''
  form.minStudents = 1
  form.maxStudents = 12
  form.status = 'active'
  form.remark = ''
  formError.value = ''
}

async function loadClassTypes() {
  if (!institutionId.value) {
    classTypes.value = []
    return
  }
  loading.value = true
  pageError.value = ''
  try {
    classTypes.value = await fetchClassTypes({ institutionId: institutionId.value })
  } catch (error) {
    classTypes.value = []
    pageError.value = toMessage(error, '班型加载失败')
  } finally {
    loading.value = false
  }
}

function openDialog(mode: 'create' | 'edit', row?: ClassTypeRecord) {
  dialogMode.value = mode
  if (mode === 'edit' && row) {
    editingId.value = row.id
    form.typeCode = row.typeCode ?? ''
    form.typeName = row.typeName ?? ''
    form.teachingMethod = row.teachingMethod ?? ''
    form.minStudents = row.minStudents ?? 1
    form.maxStudents = row.maxStudents ?? 12
    form.status = row.status ?? 'active'
    form.remark = row.remark ?? ''
  } else {
    editingId.value = null
    resetForm()
  }
  formError.value = ''
  dialogVisible.value = true
}

async function submit() {
  if (!institutionId.value) {
    formError.value = '请先选择机构范围'
    return
  }
  if (!trimToUndefined(form.typeCode) || !trimToUndefined(form.typeName)) {
    formError.value = '请完整填写班型编码和名称'
    return
  }
  submitting.value = true
  formError.value = ''
  const payload = {
    typeCode: form.typeCode.trim(),
    typeName: form.typeName.trim(),
    teachingMethod: trimToUndefined(form.teachingMethod),
    minStudents: form.minStudents,
    maxStudents: form.maxStudents,
    status: form.status,
    remark: trimToUndefined(form.remark),
  }
  try {
    if (dialogMode.value === 'create') {
      await createClassType({ institutionId: institutionId.value, ...payload })
      ElMessage.success('班型已新增')
    } else if (editingId.value) {
      await updateClassType(editingId.value, payload)
      ElMessage.success('班型已更新')
    }
    dialogVisible.value = false
    await loadClassTypes()
  } catch (error) {
    formError.value = toMessage(
      error,
      dialogMode.value === 'create' ? '新增班型失败' : '更新班型失败'
    )
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(row: ClassTypeRecord) {
  try {
    await ElMessageBox.confirm(
      `确定删除班型「${row.typeName || row.typeCode || row.id}」吗？已绑定该班型的班级不会改动，但会显示成"班型 #编号"。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      }
    )
  } catch {
    return
  }
  try {
    await deleteClassType(row.id)
    ElMessage.success('班型已删除')
    await loadClassTypes()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除班型失败'))
  }
}

function goCourses() {
  void router.push('/courses')
}

watch(institutionId, () => {
  void loadClassTypes()
})

onMounted(async () => {
  // 列表回显要用的字典在进页面时就拉取（表单里的 DictSelect 会自己拉，表格列不会）
  void dictStore.ensureLoaded(['teaching_method']).catch(() => undefined)
  await institutionStore.loadInstitutions()
  await loadClassTypes()
})
</script>
