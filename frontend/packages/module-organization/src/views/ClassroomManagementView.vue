<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 主操作行:按钮靠左、当前范围回显靠右。校区不在本页选,统一在横幅那颗芯片 -->
    <div class="table-toolbar">
      <el-button
        v-permission="'organization:write'"
        type="primary"
        @click="openClassroomDialog('create')"
        >新增教室</el-button
      >
      <el-button :icon="Refresh" circle :loading="lookupLoading" @click="refreshAll" />
      <div class="education-context-text">
        当前机构：{{ formatInstitution(institutionId) }}，当前校区：{{
          scopeStore.campusName(campusId)
        }}；教室编码与名称在同一校区内唯一
      </div>
    </div>

    <article class="panel-card education-panel">
      <TableFillBox v-slot="{ height }">
        <el-table
          :data="classrooms"
          border
          row-key="id"
          highlight-current-row
          :height="height"
          v-loading="tableLoading"
          :empty-text="tableLoading ? '' : '该范围下暂无教室数据。'"
        >
          <!-- 收成「全部校区」时整表混着几个校区的教室,靠这一列分得开;单选一个校区时整列同值,就收掉 -->
          <el-table-column v-if="!campusId" label="校区" width="130" prop="campusId" sortable>
            <template #default="{ row }">{{ scopeStore.campusName(row.campusId) }}</template>
          </el-table-column>
          <el-table-column
            prop="classroomCode"
            label="教室编码"
            width="150"
            fixed="left"
            sortable
          />
          <el-table-column
            prop="classroomName"
            label="教室名称"
            min-width="180"
            show-overflow-tooltip
            sortable
          />
          <el-table-column label="类型" width="120" prop="roomType" sortable>
            <template #default="{ row }">{{ roomTypeLabel(row.roomType) }}</template>
          </el-table-column>
          <el-table-column prop="floorNo" label="楼层" width="100" align="right" sortable />
          <el-table-column prop="seatCapacity" label="容量" width="100" align="right" sortable />
          <el-table-column prop="status" label="状态" width="120" sortable>
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{
                classroomStatusLabel(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="可排课" width="100" prop="allowScheduling" sortable>
            <template #default="{ row }">{{ row.allowScheduling ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column
            prop="remark"
            label="备注"
            min-width="180"
            show-overflow-tooltip
            sortable
          />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button
                v-permission="'organization:write'"
                link
                type="primary"
                @click="openClassroomDialog('edit', row)"
                >编辑</el-button
              >
              <el-button
                v-permission="'organization:write'"
                link
                type="danger"
                @click="confirmDeleteClassroom(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

    </article>

    <el-dialog
      v-model="classroomDialogVisible"
      :title="classroomDialogMode === 'create' ? '新增教室' : '编辑教室'"
      width="560px"
    >
      <el-alert
        v-if="classroomFormError"
        :title="classroomFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form
        ref="classroomFormRef"
        :model="classroomForm"
        :rules="classroomRules"
        label-position="top"
        class="education-form"
        scroll-to-error
        @submit.prevent
      >
        <el-form-item label="归属校区" prop="campusId">
          <el-select v-model="classroomForm.campusId" placeholder="选择校区" filterable>
            <el-option
              v-for="item in campuses"
              :key="item.id"
              :label="item.campusName || item.campusCode || `校区 #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教室编码" prop="classroomCode">
          <CodeInput
            v-model="classroomForm.classroomCode"
            resource="classroom"
            :mode="classroomDialogMode"
          />
        </el-form-item>
        <el-form-item label="教室名称" prop="classroomName"
          ><el-input v-model="classroomForm.classroomName"
        /></el-form-item>
        <el-form-item label="教室类型"
          ><DictSelect v-model="classroomForm.roomType" type-code="room_type"
        /></el-form-item>
        <el-form-item label="楼层"><el-input v-model="classroomForm.floorNo" /></el-form-item>
        <el-form-item label="座位数"
          ><el-input-number v-model="classroomForm.seatCapacity" :min="0" style="width: 100%"
        /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="classroomForm.status">
            <el-option label="空闲" value="idle" />
            <el-option label="占用" value="occupied" />
            <el-option label="维护" value="maintenance" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classroomDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classroomSubmitting" @click="submitClassroom">{{
          classroomDialogMode === 'create' ? '提交教室' : '保存修改'
        }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import CodeInput from '@cornerstone/core/components/CodeInput.vue'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  createClassroom,
  deleteClassroom,
  fetchClassrooms,
  updateClassroom,
  type ClassroomSummary,
} from '@cornerstone/module-organization/api/organization'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'

const institutionStore = useInstitutionStore()
const scopeStore = useEducationScopeStore()
const dictStore = useDictStore()

const institutionId = computed(() => institutionStore.currentInstitutionId)
/** 校区范围是全站那一份：横幅芯片改，本页只读 */
const { campusId, campuses, prefillCampusId } = storeToRefs(scopeStore)
const classrooms = ref<ClassroomSummary[]>([])

const pageError = ref('')
const lookupLoading = ref(false)
const tableLoading = ref(false)

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function formatInstitution(id?: string | null) {
  return institutionStore.currentInstitutionName || (id ? `机构 #${id}` : '未选择')
}

function roomTypeLabel(value?: string | null) {
  return dictStore.displayLabel('room_type', value)
}

function classroomStatusLabel(value?: string | null) {
  switch (value) {
    case 'occupied':
      return '占用'
    case 'maintenance':
      return '维护'
    case 'idle':
    default:
      return '空闲'
  }
}

function statusTagType(value?: string | null) {
  switch (value) {
    case 'active':
      return 'success'
    case 'suspended':
    case 'maintenance':
      return 'warning'
    default:
      // 空闲/占用这类常态用中性色,不占警示位
      return 'info'
  }
}

// ---- 弹窗 ----
const classroomDialogVisible = ref(false)
const classroomDialogMode = ref<'create' | 'edit'>('create')
const classroomSubmitting = ref(false)
const classroomFormError = ref('')
const editingClassroomId = ref<string | null>(null)

const classroomFormRef = ref<FormInstance | null>(null)

/** 必填校验统一交给 el-form：标签红星 + 输入框红边 + 框下行内提示，不靠顶部一条总文案 */
function requiredText(label: string): FormRules[string] {
  return [{ required: true, whitespace: true, message: `请填写${label}`, trigger: 'blur' }]
}

const classroomRules: FormRules = {
  campusId: [{ required: true, message: '请选择归属校区', trigger: 'change' }],
  classroomCode: requiredText('教室编码'),
  classroomName: requiredText('教室名称'),
}

const classroomForm = reactive({
  campusId: '' as string | null,
  classroomCode: '',
  classroomName: '',
  roomType: '',
  floorNo: '',
  seatCapacity: 20,
  status: 'idle',
})

function resetClassroomForm() {
  // 「全部校区」时没有唯一归属校区，留空让操作员自己选，不替他猜
  classroomForm.campusId = prefillCampusId.value
  classroomForm.classroomCode = ''
  classroomForm.classroomName = ''
  classroomForm.roomType = ''
  classroomForm.floorNo = ''
  classroomForm.seatCapacity = 20
  classroomForm.status = 'idle'
  classroomFormError.value = ''
}

function openClassroomDialog(mode: 'create' | 'edit', row?: ClassroomSummary) {
  classroomDialogMode.value = mode
  if (mode === 'edit' && row) {
    editingClassroomId.value = row.id
    classroomForm.campusId = row.campusId ?? null
    classroomForm.classroomCode = row.classroomCode ?? ''
    classroomForm.classroomName = row.classroomName ?? ''
    classroomForm.roomType = row.roomType ?? ''
    classroomForm.floorNo = row.floorNo ?? ''
    classroomForm.seatCapacity = row.seatCapacity ?? 20
    classroomForm.status = row.status ?? 'idle'
  } else {
    editingClassroomId.value = null
    resetClassroomForm()
  }
  classroomFormError.value = ''
  classroomDialogVisible.value = true
  // 上一次弹窗留下的红框不能带给新一次填写
  void nextTick(() => classroomFormRef.value?.clearValidate())
}

async function loadClassrooms() {
  tableLoading.value = true
  try {
    // 传 null 就是「全部校区」，后端按本机构校区集合收窄
    classrooms.value = await fetchClassrooms(campusId.value)
  } catch (error) {
    classrooms.value = []
    pageError.value = toMessage(error, '教室列表加载失败')
  } finally {
    tableLoading.value = false
  }
}

async function submitClassroom() {
  // 校验失败时 el-form 已经在各字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await classroomFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  // 纯类型守卫：required 校验已保证非空，这里只为 campusId 收窄出 string 类型
  if (!classroomForm.campusId) {
    return
  }

  classroomSubmitting.value = true
  classroomFormError.value = ''
  try {
    if (classroomDialogMode.value === 'create') {
      await createClassroom({
        campusId: classroomForm.campusId,
        classroomCode: classroomForm.classroomCode.trim(),
        classroomName: classroomForm.classroomName.trim(),
        roomType: trimToUndefined(classroomForm.roomType),
        floorNo: trimToUndefined(classroomForm.floorNo),
        seatCapacity: classroomForm.seatCapacity,
        status: classroomForm.status,
      })
      ElMessage.success('教室已新增')
    } else if (editingClassroomId.value) {
      await updateClassroom(editingClassroomId.value, {
        campusId: classroomForm.campusId,
        classroomCode: classroomForm.classroomCode.trim(),
        classroomName: classroomForm.classroomName.trim(),
        roomType: trimToUndefined(classroomForm.roomType),
        floorNo: trimToUndefined(classroomForm.floorNo),
        seatCapacity: classroomForm.seatCapacity,
        status: classroomForm.status,
      })
      ElMessage.success('教室已更新')
    }
    classroomDialogVisible.value = false
    // 范围已收窄到某个校区、保存的却是另一个校区时，把全站范围跟过去，否则刚保存的那条被挡在列表外
    if (campusId.value && campusId.value !== classroomForm.campusId) {
      scopeStore.setCampus(classroomForm.campusId)
    }
    await loadClassrooms()
  } catch (error) {
    classroomFormError.value = toMessage(
      error,
      classroomDialogMode.value === 'create' ? '新增教室失败' : '更新教室失败'
    )
  } finally {
    classroomSubmitting.value = false
  }
}

async function confirmDeleteClassroom(row: ClassroomSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除教室「${row.classroomName || row.classroomCode || row.id}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteClassroom(row.id)
    ElMessage.success('教室已删除')
    await loadClassrooms()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除教室失败'))
  }
}

/** 弹窗的归属校区下拉要用的清单在 store 里，进页面时确保拉过（同一机构只拉一次） */
async function refreshAll() {
  lookupLoading.value = true
  pageError.value = ''
  try {
    await scopeStore.loadCampuses(institutionId.value)
  } finally {
    lookupLoading.value = false
  }
  await loadClassrooms()
}

async function initializePage() {
  try {
    await institutionStore.loadInstitutions()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  }
  await refreshAll()
}

onMounted(() => {
  // 列表回显要用的字典在进页面时就拉取（表单里的 DictSelect 会自己拉，表格列不会）
  void dictStore.ensureLoaded(['room_type']).catch(() => undefined)
  initializePage()
})
</script>
