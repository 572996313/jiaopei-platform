<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <article v-if="listConfigSectionError" class="education-empty">
        {{ listConfigSectionError }}
      </article>
      <template v-else>
        <div class="education-table-head">
          <div class="panel-title">
            列表配置<span class="panel-count">共 {{ listConfigRecords.length }} 条</span>
          </div>
          <div class="table-actions">
            <span class="education-context-text"
              >维护列表的展示列与查询条件,保存后立即生效,无需发版。</span
            >
            <el-button @click="guideDialogVisible = true">使用教程</el-button>
            <el-button
              v-if="permissions.canManageListConfigs"
              type="primary"
              @click="openCreateListConfigDialog"
              >新增配置</el-button
            >
          </div>
        </div>

        <el-table
          :data="listConfigRecords"
          border
          v-loading="listConfigLoading"
          :height="TABLE_FILL_HEIGHT"
        >
          <el-table-column
            prop="resourceCode"
            label="资源编码"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="resourceName"
            label="资源名称"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column prop="endpoint" label="分页接口" min-width="190" show-overflow-tooltip />
          <el-table-column label="关联数据源" min-width="220">
            <template #default="{ row }">
              <template v-if="referencedLoaders(row).length">
                <el-tooltip
                  v-for="code in referencedLoaders(row)"
                  :key="code"
                  :content="dataSourceEndpointHint(code)"
                  placement="top"
                >
                  <el-tag size="small" class="source-ref-tag" :type="sourceRefTagType(code)">
                    {{ code }}
                  </el-tag>
                </el-tooltip>
              </template>
              <span v-else class="education-context-text">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="pageSize" label="每页条数" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{
                statusLabel(row.status, statusLabelMap)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
          <el-table-column
            v-if="permissions.canManageListConfigs"
            label="操作"
            width="150"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="system-operation-group">
                <el-button text type="primary" @click="openEditListConfigDialog(row)"
                  >编辑</el-button
                >
                <el-button
                  text
                  type="danger"
                  :disabled="seedListConfigIds.has(row.id)"
                  @click="handleDeleteListConfig(row)"
                  >删除</el-button
                >
              </div>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </article>

    <el-dialog
      v-model="listConfigDialogVisible"
      :title="listConfigDialogMode === 'create' ? '新增列表配置' : '编辑列表配置'"
      fullscreen
      class="list-config-dialog"
    >
      <el-alert
        v-if="listConfigFormError"
        :title="listConfigFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <!-- 从现有配置复制(仅新增模式) -->
      <div v-if="listConfigDialogMode === 'create'" class="copy-source">
        <span class="copy-source-label">从现有配置复制(推荐新手直接复制改):</span>
        <el-select
          v-model="copySourceCode"
          clearable
          filterable
          placeholder="选择要复制的页面配置"
          style="flex: 1; min-width: 260px"
        >
          <el-option
            v-for="item in listConfigRecords"
            :key="item.id"
            :label="`${item.resourceName}(${item.resourceCode})`"
            :value="item.resourceCode"
          />
        </el-select>
        <el-button :disabled="!copySourceCode" @click="copyFromExisting">复制</el-button>
      </div>

      <el-form label-position="top" class="education-form">
        <!-- 基本信息:横排紧凑条,节省纵向空间给配置编辑 -->
        <div class="config-meta-bar">
          <el-form-item
            v-if="listConfigDialogMode === 'create'"
            label="资源编码"
            class="meta-item meta-code"
          >
            <el-input
              v-model="listConfigForm.resourceCode"
              placeholder="如 system-users,建后不可改"
            />
          </el-form-item>
          <el-form-item label="资源名称" class="meta-item">
            <el-input
              v-model="listConfigForm.resourceName"
              maxlength="64"
              placeholder="如 用户管理"
            />
          </el-form-item>
          <el-form-item label="分页接口路径" class="meta-item meta-endpoint">
            <el-input
              v-model="listConfigForm.endpoint"
              maxlength="128"
              placeholder="如 /system/users/page"
            />
          </el-form-item>
          <el-form-item label="每页条数" class="meta-item meta-small">
            <el-input-number v-model="listConfigForm.pageSize" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="状态" class="meta-item meta-small">
            <el-select v-model="listConfigForm.status">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="备注" class="meta-item meta-remark">
            <el-input v-model="listConfigForm.remark" maxlength="500" placeholder="选填" />
          </el-form-item>
        </div>

        <!-- 配置分区编辑:各自独立页签,一次只渲染一个分区 -->
        <el-tabs v-model="activeEditTab" class="editor-tabs" @tab-change="onEditTabChange">
          <el-tab-pane label="表格列" name="columns">
            <div class="editor-block-desc tab-pane-desc">
              列表显示哪几列。表格里一行代表一列,可增删、排序、复制。
            </div>
            <ColumnEditor v-model="formState.columns" />
          </el-tab-pane>

          <el-tab-pane label="查询条件" name="query">
            <div class="editor-block-desc tab-pane-desc">
              列表上方的筛选控件,不填则列表不带筛选栏。
            </div>
            <QueryEditor v-model="formState.query" />
          </el-tab-pane>

          <el-tab-pane label="按钮" name="actions">
            <div class="editor-block-desc tab-pane-desc">
              页面的操作入口,不填则使用页面自带按钮。
            </div>
            <ActionEditor v-model="formState.actions" />
          </el-tab-pane>

          <el-tab-pane label="弹窗表单" name="form">
            <div class="editor-block-desc tab-pane-desc">
              新增/编辑弹窗里的输入项,不填则弹窗不可用,需先配置字段。
            </div>
            <FormEditor v-model="formState.form" />
          </el-tab-pane>

          <el-tab-pane label="页面与弹窗" name="shell">
            <div class="editor-block-desc tab-pane-desc">
              页面标题、计数、弹窗按钮文案等,留空走默认。
            </div>
            <ShellEditor v-model:page="formState.page" v-model:dialog="formState.dialog" />
          </el-tab-pane>

          <el-tab-pane label="JSON 编辑" name="json">
            <JsonModePanel
              v-model:columns="jsonText.columns"
              v-model:query="jsonText.query"
              v-model:actions="jsonText.actions"
              v-model:form="jsonText.form"
              v-model:page="jsonText.page"
              v-model:dialog="jsonText.dialog"
            />
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <el-button @click="listConfigDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="listConfigSubmitting" @click="submitListConfig">
          {{ listConfigDialogMode === 'create' ? '创建配置' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 使用教程弹窗 -->
    <el-dialog v-model="guideDialogVisible" title="列表配置使用教程" width="820px" top="6vh">
      <el-tabs>
        <el-tab-pane label="常见操作">
          <div class="guide-section">
            <h4 class="guide-title">这个功能是什么?</h4>
            <p class="guide-text">
              「列表配置」把页面的 <b>表格列</b>、<b>查询条件</b>、<b>按钮</b>、<b>新增/编辑弹窗</b>
              收敛成一条配置。 改完保存立即生效,不用改代码、不用重新发版。系统设置里的「用户管理 /
              角色管理 / 系统参数」就是用它搭的(内置配置)。
            </p>
          </div>
          <div v-for="step in guideSteps" :key="step.title" class="guide-section">
            <h4 class="guide-title">{{ step.title }}</h4>
            <ol class="guide-list">
              <li v-for="(line, i) in step.lines" :key="i">{{ line }}</li>
            </ol>
          </div>
          <div class="guide-section">
            <h4 class="guide-title">常见提醒</h4>
            <ul class="guide-list">
              <li>
                所有「字段名 prop / field /
                action」都必须跟后端/页面代码对得上,写错该列不显示、该按钮不渲染。
              </li>
              <li>
                状态列想显示彩色标签:列类型选 <code>dict</code>,填字典类型(如
                <code>common_status</code>),再在标签颜色里给值配颜色。
              </li>
              <li>不确定怎么做时,先点「新增配置 → 从现有配置复制」,复制一条改,最省事。</li>
            </ul>
          </div>
        </el-tab-pane>

        <el-tab-pane label="字段速查">
          <div v-for="group in fieldGuideGroups" :key="group.title" class="guide-section">
            <h4 class="guide-title">{{ group.title }}</h4>
            <table class="guide-table">
              <thead>
                <tr>
                  <th>字段</th>
                  <th>含义</th>
                  <th>可选值 / 示例</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in group.rows" :key="row.field">
                  <td>
                    <code>{{ row.field }}</code>
                  </td>
                  <td>{{ row.meaning }}</td>
                  <td>{{ row.values }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="学习资料">
          <div class="guide-section">
            <h4 class="guide-title">前端控件长什么样、有哪些属性?</h4>
            <p class="guide-text">
              本系统的页面是基于 <b>Element Plus</b>(一个 Vue
              组件库)搭的。表格、输入框、下拉、按钮、表单的所有显示属性, 都以 Element Plus
              官方中文文档为准。按需点进对应组件页查:
            </p>
            <ul class="guide-list">
              <li>
                <a href="https://element-plus.org/zh-CN/" target="_blank" rel="noopener"
                  >Element Plus 官方中文文档(总入口)↗</a
                >
              </li>
              <li>
                <a
                  href="https://element-plus.org/zh-CN/component/table.html"
                  target="_blank"
                  rel="noopener"
                  >表格 Table(查列的宽度/省略/对齐等)↗</a
                >
              </li>
              <li>
                <a
                  href="https://element-plus.org/zh-CN/component/form.html"
                  target="_blank"
                  rel="noopener"
                  >表单 Form(查字段布局、校验)↗</a
                >
              </li>
              <li>
                <a
                  href="https://element-plus.org/zh-CN/component/button.html"
                  target="_blank"
                  rel="noopener"
                  >按钮 Button(查颜色/禁用)↗</a
                >
              </li>
              <li>
                <a
                  href="https://element-plus.org/zh-CN/component/select.html"
                  target="_blank"
                  rel="noopener"
                  >下拉选择 Select↗</a
                >
              </li>
              <li>
                <a
                  href="https://element-plus.org/zh-CN/component/date-picker.html"
                  target="_blank"
                  rel="noopener"
                  >日期选择 DatePicker↗</a
                >
              </li>
              <li>
                <a
                  href="https://element-plus.org/zh-CN/component/tag.html"
                  target="_blank"
                  rel="noopener"
                  >标签 Tag(查颜色取值)↗</a
                >
              </li>
            </ul>
          </div>
          <div class="guide-section">
            <h4 class="guide-title">完全不懂 JSON?</h4>
            <p class="guide-text">表单编辑模式下你不需要写 JSON。但如果想了解 JSON 是什么:</p>
            <ul class="guide-list">
              <li>
                <a
                  href="https://www.runoob.com/json/json-tutorial.html"
                  target="_blank"
                  rel="noopener"
                  >JSON 教程(菜鸟教程,含语法与示例)↗</a
                >
              </li>
            </ul>
          </div>
          <div class="guide-section">
            <h4 class="guide-title">完整配置契约文档</h4>
            <p class="guide-text">
              仓库内
              <code>docs/config-contract.md</code>
              有更详细的配置结构说明,交给后端/前端同事对细节即可。
            </p>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { TABLE_FILL_HEIGHT } from '@cornerstone/core/utils/table-view'
import { onMounted, reactive, ref } from 'vue'
import { useListConfigStore } from '@cornerstone/core/stores/listConfig'
import {
  createListConfig,
  deleteListConfig,
  fetchListConfigs,
  updateListConfig,
} from '@cornerstone/core/api/system'
import type { ListConfigSummary } from '@cornerstone/core/api/system'
import ColumnEditor from '@cornerstone/core/components/ListConfigEditor/ColumnEditor.vue'
import QueryEditor from '@cornerstone/core/components/ListConfigEditor/QueryEditor.vue'
import ActionEditor from '@cornerstone/core/components/ListConfigEditor/ActionEditor.vue'
import FormEditor from '@cornerstone/core/components/ListConfigEditor/FormEditor.vue'
import ShellEditor from '@cornerstone/core/components/ListConfigEditor/ShellEditor.vue'
import JsonModePanel from '@cornerstone/core/components/ListConfigEditor/JsonModePanel.vue'
import {
  actionsToJson,
  columnsToJson,
  createBlankFormState,
  dialogShellToJson,
  formToJson,
  pageShellToJson,
  parseActions,
  parseColumns,
  parseDialogShell,
  parseForm,
  parsePageShell,
  parseQuery,
  queryToJson,
  type ListConfigFormState,
} from '@cornerstone/core/components/ListConfigEditor/types'
import {
  ensureStatusDictLoaded,
  statusLabel,
  statusTagType,
  toMessage,
  trimToUndefined,
  useStatusLabelMap,
  useStatusOptions,
  useSystemPermissions,
} from './shared'
import { useDataSourceStore } from '@cornerstone/core/stores/dataSource'

const listConfigStore = useListConfigStore()
const dataSourceStore = useDataSourceStore()
const permissions = useSystemPermissions()
const statusOptions = useStatusOptions()
const statusLabelMap = useStatusLabelMap()

const listConfigSectionError = ref('')
const listConfigLoading = ref(false)
const listConfigRecords = ref<ListConfigSummary[]>([])
const listConfigDialogVisible = ref(false)
const listConfigDialogMode = ref<'create' | 'edit'>('create')
const listConfigSubmitting = ref(false)
const listConfigFormError = ref('')
const selectedListConfig = ref<ListConfigSummary | null>(null)
// V20 种子的三条内置配置(id 1-3)支撑系统设置三个列表页,后端禁删,前端同步禁用删除按钮
const seedListConfigIds = new Set(['1', '2', '3'])

/** 当前激活的编辑分区:columns/query/actions/form/shell/json */
const activeEditTab = ref<'columns' | 'query' | 'actions' | 'form' | 'shell' | 'json'>('columns')
/** 可视化状态(表单模式的工作副本;JSON 模式是权威数据) */
const formState = ref<ListConfigFormState>(createBlankFormState())
/** JSON 文本(提交数据的权威来源;form → json 切换时由 formState 序列化) */
const jsonText = reactive({
  columns: '',
  query: '',
  actions: '',
  form: '',
  page: '',
  dialog: '',
})

const listConfigForm = reactive({
  resourceCode: '',
  resourceName: '',
  endpoint: '',
  pageSize: 10,
  status: 'active',
  remark: '',
})

const guideDialogVisible = ref(false)
const copySourceCode = ref('')

/* ---------------- 教程弹窗内容 ---------------- */

const guideSteps = [
  {
    title: '怎么加一列?',
    lines: [
      '进入「编辑」→ 保持「表单编辑」模式 → 找到「表格列」区块,点「添加列」。',
      '「数据字段名 prop」填后端返回的字段名(如 username);「列标题 label」填表头中文(如 账号)。',
      '想显示成日期、字典标签、开关样式,在「渲染类型 type」里选对应类型。',
      '调完点「创建配置 / 保存编辑」,保存后回到列表页刷新即可看到效果。',
    ],
  },
  {
    title: '怎么加一个查询条件(筛选)?',
    lines: [
      '进「编辑」→「查询条件」区块 → 点「添加查询条件」。',
      '「查询参数名 field」填查询参数(如 status);「标签 label」填筛选框前的文字。',
      '「控件类型 type」选输入框 / 下拉 / 字典下拉 / 日期。下拉可手填选项或引用现成数据源。',
      '注意:field 必须是后端分页接口支持的参数,否则填了不生效(详见字段速查)。',
    ],
  },
  {
    title: '怎么加一个按钮(比如「新增」)?',
    lines: [
      '进「编辑」→「按钮」区块 → 点「添加按钮」。',
      '「动作名 action」填页面已注册的动作名(如 user-create);「按钮文案 label」填显示文字。',
      '「位置 position」选 toolbar(表格上方)或 row(行内)。',
      '「权限码 permission」选要授权的权限(如 system:user:manage),不选则所有人可见。',
      'action 写错会不渲染;不确定动作名时,看同页面已有按钮是怎么填的。',
    ],
  },
  {
    title: '怎么给「新增/编辑」弹窗加一个输入框?',
    lines: [
      '进「编辑」→「弹窗表单」区块 → 如果还没有点「添加字段」。',
      '「字段名 field」填保存接口的字段名(如 displayName);「标签 label」填弹窗里的文案。',
      '「控件类型 type」选输入框 / 下拉 / 开关等;要必填就打开「必填」开关。',
      '弹窗整页没配置字段时,页面上点新增/编辑会提示先配置。',
    ],
  },
  {
    title: '想改页面的标题、或弹窗的标题/宽度?',
    lines: [
      '进「编辑」→「页面与弹窗显示」区块。',
      '「页面大标题 title」改列表页顶部大字;「新增标题 createTitle」改新增弹窗的标题;「弹窗宽度 width」改宽度。',
      '留空即为默认效果,不用全部填。',
    ],
  },
]

interface FieldGuideRow {
  field: string
  meaning: string
  values: string
}

interface FieldGuideGroup {
  title: string
  rows: FieldGuideRow[]
}

const fieldGuideGroups: FieldGuideGroup[] = [
  {
    title: '表格列(columns)',
    rows: [
      { field: 'prop', meaning: '数据字段名,对应后端返回的字段', values: '如 username、status' },
      { field: 'label', meaning: '列标题,表头显示的中文', values: '如 账号、状态' },
      {
        field: 'type',
        meaning: '渲染类型',
        values: 'text(默认) / datetime / dict / boolean / tags / map',
      },
      { field: 'width', meaning: '列固定宽度', values: '数字,如 150' },
      { field: 'minWidth', meaning: '列最小宽度', values: '数字,如 140' },
      {
        field: 'showOverflowTooltip',
        meaning: '内容过长时显示省略 + 悬浮全文',
        values: 'true / false',
      },
      {
        field: 'dictType',
        meaning: 'type=dict 时的字典类型编码',
        values: '如 common_status(启用/停用)',
      },
      {
        field: 'tagTypeMap',
        meaning: '值 → 标签颜色(配色板见图册)',
        values: '如 active→success(绿)、disabled→info(灰)',
      },
      {
        field: 'trueLabel / falseLabel',
        meaning: 'type=boolean 的是/否文案',
        values: '如 是 / 否',
      },
      { field: 'valueMap', meaning: 'type=map 的值翻译映射', values: '如 auth→认证、system→系统' },
      {
        field: 'renderer',
        meaning: '特殊渲染器(普通列不要填)',
        values: 'institutionName / campusName / permissionTags / passwordStatus',
      },
    ],
  },
  {
    title: '查询条件(query)',
    rows: [
      {
        field: 'field',
        meaning: '查询参数名,必须后端支持',
        values: '如 keyword、status、institutionId',
      },
      { field: 'label', meaning: '筛选框前的文字', values: '如 关键字、状态' },
      { field: 'type', meaning: '控件类型', values: 'input / select / dict-select / date' },
      { field: 'placeholder', meaning: '输入框内的灰色提示', values: '如 请输入账号' },
      { field: 'defaultValue', meaning: '进入页面时默认带上的值', values: '如 active' },
      {
        field: 'options / optionsLoader',
        meaning: '下拉选项:手填列表 / 引用现成数据源',
        values: '数据源:institutions(机构)/ campuses(校区)/ roles / status',
      },
      { field: 'dictType', meaning: 'type=dict-select 时的字典类型', values: '如 common_status' },
    ],
  },
  {
    title: '按钮(actions)',
    rows: [
      {
        field: 'action',
        meaning: '页面代码里注册的动作名',
        values: '如 user-create、role-delete (写错不渲染)',
      },
      { field: 'label', meaning: '按钮显示文字', values: '如 新增用户、删除' },
      { field: 'position', meaning: '按钮位置', values: 'toolbar(表格上方) / row(行内)' },
      { field: 'buttonType', meaning: '按钮样式', values: 'primary / default / text / danger' },
      {
        field: 'permission',
        meaning: '可见所需权限码(不填=所有人)',
        values: '如 system:user:manage',
      },
      {
        field: 'actionType',
        meaning: '点击后默认行为(不填自动推断)',
        values: 'create / edit / delete / toggle-status / reset-password / assign-roles / custom',
      },
    ],
  },
  {
    title: '弹窗表单(form)',
    rows: [
      {
        field: 'field',
        meaning: '表单字段名(与保存接口字段一致)',
        values: '如 displayName、phone',
      },
      { field: 'label', meaning: '字段标题', values: '如 显示名称' },
      {
        field: 'type',
        meaning: '控件类型',
        values:
          'input / password / textarea / number / select / dict-select / switch / checkbox-group / radio / permission-groups',
      },
      { field: 'placeholder', meaning: '输入提示', values: '如 请输入姓名' },
      { field: 'tip', meaning: '字段下方的辅助说明', values: '可空' },
      { field: 'gridSpan', meaning: '占一行 / 半行', values: '1(半行,默认) / 2(整行)' },
      {
        field: 'showOn / disabledOn',
        meaning: '新增/编辑时的显示与禁用',
        values: '["create"] / ["edit"] / ["create","edit"]',
      },
      {
        field: 'options / optionsLoader',
        meaning: '下拉/单选/多选的选项来源',
        values: '同查询条件',
      },
      { field: 'dictType', meaning: 'type=dict-select 的字典类型', values: '如 common_status' },
      { field: 'required', meaning: '是否必填', values: 'true / false' },
      { field: 'defaultValue', meaning: '新增时的初始值', values: '开关填 true/false,数字填数字' },
      {
        field: 'maxlength / min / max / rows',
        meaning: '输入限制(按控件类型显示)',
        values: '数字',
      },
      { field: 'clearable', meaning: '是否可一键清空', values: 'true / false' },
      { field: 'onChange', meaning: '字段联动钩子(普通配置不用填)', values: '页面注册的钩子名' },
    ],
  },
  {
    title: '页面显示(page)',
    rows: [
      { field: 'title', meaning: '页面大标题(缺省=资源名)', values: '如 用户管理' },
      { field: 'description', meaning: '标题右侧说明', values: '如 维护系统用户与绑定' },
      { field: 'showCount', meaning: '是否显示"共 X 条"', values: 'true / false' },
      { field: 'countFormat', meaning: '计数文案模板', values: '共 {total} 条' },
      { field: 'emptyText', meaning: '表格空数据文案', values: '暂无数据' },
    ],
  },
  {
    title: '弹窗显示(dialog)',
    rows: [
      { field: 'width', meaning: '弹窗宽度', values: '如 680px' },
      { field: 'createTitle / editTitle', meaning: '新增/编辑标题', values: '如 新增用户' },
      { field: 'submitText / cancelText', meaning: '底部按钮文案', values: '保存 / 取消' },
      { field: 'createMessage / updateMessage', meaning: '成功提示', values: '如 用户已创建' },
      { field: 'reloadAfterSubmit', meaning: '提交后是否刷新列表', values: 'true / false' },
    ],
  },
]

/* ---------------- 数据加载 ---------------- */

/**
 * 提取某条列表配置引用的全部 optionsLoader 数据源编码(列渲染/查询条件/弹窗表单)。
 * 用于「关联数据源」列:让用户看清这个页面用到了哪些下拉数据接口。
 */
function referencedLoaders(row: ListConfigSummary): string[] {
  const codes = new Set<string>()
  const scan = (raw: string | null | undefined) => {
    if (!raw || !raw.trim()) {
      return
    }
    try {
      const parsed: unknown = JSON.parse(raw)
      if (!Array.isArray(parsed)) {
        return
      }
      for (const item of parsed) {
        if (item && typeof item === 'object') {
          const loader = (item as Record<string, unknown>).optionsLoader
          if (typeof loader === 'string' && loader.trim()) {
            codes.add(loader.trim())
          }
        }
      }
    } catch {
      // 忽略单条 JSON 解析失败,不阻断整页
    }
  }
  scan(row.columnsJson)
  scan(row.queryJson)
  try {
    const form = row.formJson ? JSON.parse(row.formJson) : null
    const fields =
      form && Array.isArray((form as { fields?: unknown }).fields)
        ? (form as { fields: unknown[] }).fields
        : []
    for (const field of fields) {
      if (field && typeof field === 'object') {
        const loader = (field as Record<string, unknown>).optionsLoader
        if (typeof loader === 'string' && loader.trim()) {
          codes.add(loader.trim())
        }
      }
    }
  } catch {
    // 表单 JSON 解析失败忽略
  }
  return Array.from(codes).sort()
}

/** 数据源编码 → 接口路径/字典类型提示(来自已配置的数据源定义) */
function dataSourceEndpointHint(code: string): string {
  const definition = dataSourceStore.getDefinition(code)
  if (definition) {
    return definition.sourceType === 'dict'
      ? `${code}:字典 ${definition.dictType || ''}`
      : `${code}:${definition.endpoint || ''}`
  }
  return code
}

function sourceRefTagType(code: string): 'primary' | 'warning' | 'info' {
  if (dataSourceStore.getDefinition(code)) {
    return 'primary'
  }
  // 已请求但不存在的数据源(负缓存)标灰;未请求的暂作 info
  return 'info'
}

async function loadListConfigs() {
  listConfigLoading.value = true
  listConfigSectionError.value = ''
  try {
    listConfigRecords.value = await fetchListConfigs()
    // 预加载全部引用的数据源定义,供「关联数据源」列展示接口路径
    const loaderCodes = listConfigRecords.value
      .flatMap((row) => referencedLoaders(row))
      .filter((code) => !dataSourceStore.getDefinition(code))
    if (loaderCodes.length) {
      await dataSourceStore.preloadDefinitions(loaderCodes)
    }
  } catch (error) {
    listConfigRecords.value = []
    listConfigSectionError.value = toMessage(error, '列表配置加载失败')
  } finally {
    listConfigLoading.value = false
  }
}

/* ---------------- JSON 工具 ---------------- */

function prettyJson(raw: string) {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function validateJsonArray(raw: string, name: string) {
  const trimmed = raw.trim()
  try {
    const parsed: unknown = JSON.parse(trimmed)
    if (!Array.isArray(parsed)) {
      throw new Error(`${name} 必须是 JSON 数组`)
    }
  } catch (error) {
    throw new Error(toMessage(error, `${name} 不是合法的 JSON`))
  }
  return trimmed
}

/** form_json 校验:JSON 对象 + 非空 fields 数组 + 每个字段 field/label/type 必填 */
function validateFormObject(raw: string, name: string) {
  const trimmed = raw.trim()
  let parsed: unknown
  try {
    parsed = JSON.parse(trimmed)
  } catch (error) {
    throw new Error(toMessage(error, `${name} 不是合法的 JSON`))
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${name} 必须是 JSON 对象,如 {"fields":[...]}`)
  }
  const fields = (parsed as { fields?: unknown }).fields
  if (!Array.isArray(fields) || fields.length === 0) {
    throw new Error(`${name} 的 fields 必须是非空数组`)
  }
  for (let i = 0; i < fields.length; i++) {
    const field = fields[i]
    if (!field || typeof field !== 'object' || Array.isArray(field)) {
      throw new Error(`${name} fields[${i}] 必须是 JSON 对象`)
    }
    const f = field as Record<string, unknown>
    if (typeof f.field !== 'string' || !f.field.trim()) {
      throw new Error(`${name} fields[${i}] 缺少必填字段 field`)
    }
    if (typeof f.label !== 'string' || !f.label.trim()) {
      throw new Error(`${name} fields[${i}] 缺少必填字段 label`)
    }
    if (typeof f.type !== 'string' || !f.type.trim()) {
      throw new Error(`${name} fields[${i}] 缺少必填字段 type`)
    }
  }
  return trimmed
}

/** page_json / dialog_json 校验:JSON 对象(拒绝数组),结构权威校验在后端,此处只挡明显错误 */
function validateJsonObject(raw: string, name: string) {
  const trimmed = raw.trim()
  let parsed: unknown
  try {
    parsed = JSON.parse(trimmed)
  } catch (error) {
    throw new Error(toMessage(error, `${name} 不是合法的 JSON`))
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${name} 必须是 JSON 对象,如 {"title":"系统参数"}`)
  }
  return trimmed
}

/* ---------------- 可视化状态 ↔ JSON 文本 ---------------- */

/** 由可视化状态生成全部 JSON 文本(表单模式提交/切 JSON 模式前调用) */
function serializeFormStateToJson(state: ListConfigFormState) {
  jsonText.columns = columnsToJson(state.columns)
  jsonText.query = queryToJson(state.query)
  jsonText.actions = actionsToJson(state.actions) ?? ''
  jsonText.form = formToJson(state.form) ?? ''
  jsonText.page = pageShellToJson(state.page) ?? ''
  jsonText.dialog = dialogShellToJson(state.dialog) ?? ''
}

/** 由 JSON 文本解析为可视化状态(切表单模式前调用,调用方需确保 JSON 合法) */
function parseJsonToFormState() {
  formState.value.columns = parseColumns(jsonText.columns)
  formState.value.query = parseQuery(jsonText.query)
  formState.value.actions = parseActions(jsonText.actions)
  formState.value.form = parseForm(jsonText.form)
  formState.value.page = parsePageShell(jsonText.page)
  formState.value.dialog = parseDialogShell(jsonText.dialog)
}

/** 校验 jsonText 全部字段合法,返回 JSON 内容;非法则抛错 */
function validateJsonText() {
  const columnsJson = validateJsonArray(jsonText.columns, '列配置 columnsJson')
  const queryJson = validateJsonArray(jsonText.query, '查询条件 queryJson')
  const actionsJson = jsonText.actions.trim() || null
  if (actionsJson) {
    validateJsonArray(actionsJson, '按钮配置 actionsJson')
  }
  const formJson = jsonText.form.trim() || null
  if (formJson) {
    validateFormObject(formJson, '弹窗表单 formJson')
  }
  const pageJson = jsonText.page.trim() || null
  if (pageJson) {
    validateJsonObject(pageJson, '页面壳配置 pageJson')
  }
  const dialogJson = jsonText.dialog.trim() || null
  if (dialogJson) {
    validateJsonObject(dialogJson, '弹窗壳配置 dialogJson')
  }
  return { columnsJson, queryJson, actionsJson, formJson, pageJson, dialogJson }
}

/* ---------------- 打开 / 切换 / 复制 ---------------- */

function loadFormStateFromKeys(
  columnsJson: string,
  queryJson: string,
  actionsJson: string | null,
  formJson: string | null,
  pageJson: string | null,
  dialogJson: string | null
) {
  jsonText.columns = prettyJson(columnsJson)
  jsonText.query = prettyJson(queryJson)
  jsonText.actions = actionsJson ? prettyJson(actionsJson) : ''
  jsonText.form = formJson ? prettyJson(formJson) : ''
  jsonText.page = pageJson ? prettyJson(pageJson) : ''
  jsonText.dialog = dialogJson ? prettyJson(dialogJson) : ''
  formState.value.columns = parseColumns(columnsJson)
  formState.value.query = parseQuery(queryJson)
  formState.value.actions = parseActions(actionsJson)
  formState.value.form = parseForm(formJson)
  formState.value.page = parsePageShell(pageJson)
  formState.value.dialog = parseDialogShell(dialogJson)
}

function openCreateListConfigDialog() {
  listConfigDialogMode.value = 'create'
  selectedListConfig.value = null
  copySourceCode.value = ''
  listConfigForm.resourceCode = ''
  listConfigForm.resourceName = ''
  listConfigForm.endpoint = ''
  listConfigForm.pageSize = 10
  listConfigForm.status = 'active'
  listConfigForm.remark = ''
  activeEditTab.value = 'columns'
  const blank = createBlankFormState()
  formState.value = blank
  serializeFormStateToJson(blank)
  listConfigFormError.value = ''
  listConfigDialogVisible.value = true
}

function openEditListConfigDialog(row: ListConfigSummary) {
  listConfigDialogMode.value = 'edit'
  selectedListConfig.value = row
  listConfigForm.resourceCode = row.resourceCode
  listConfigForm.resourceName = row.resourceName
  listConfigForm.endpoint = row.endpoint
  listConfigForm.pageSize = row.pageSize ?? 10
  listConfigForm.status = row.status || 'active'
  listConfigForm.remark = row.remark || ''
  activeEditTab.value = 'columns'
  loadFormStateFromKeys(
    row.columnsJson,
    row.queryJson,
    row.actionsJson ?? null,
    row.formJson ?? null,
    row.pageJson ?? null,
    row.dialogJson ?? null
  )
  listConfigFormError.value = ''
  listConfigDialogVisible.value = true
}

function copyFromExisting() {
  const source = listConfigRecords.value.find((item) => item.resourceCode === copySourceCode.value)
  if (!source) {
    return
  }
  listConfigForm.resourceName = source.resourceName
  listConfigForm.endpoint = source.endpoint
  listConfigForm.pageSize = source.pageSize ?? 10
  listConfigForm.status = source.status || 'active'
  listConfigForm.remark = source.remark || ''
  loadFormStateFromKeys(
    source.columnsJson,
    source.queryJson,
    source.actionsJson ?? null,
    source.formJson ?? null,
    source.pageJson ?? null,
    source.dialogJson ?? null
  )
  ElMessage.success(`已复制「${source.resourceName}」的配置,改完记得重新起资源编码`)
}

/** 分区切换:进入 JSON 分区前先把表单状态序列化成 JSON;从 JSON 切回先校验再解析,非法则拦在 JSON 分区 */
function onEditTabChange(tab: string | number) {
  const next = String(tab)
  if (next === activeEditTab.value) {
    return
  }
  if (next === 'json') {
    serializeFormStateToJson(formState.value)
    activeEditTab.value = 'json'
    return
  }
  try {
    validateJsonText()
    parseJsonToFormState()
    activeEditTab.value = next as 'columns' | 'query' | 'actions' | 'form' | 'shell'
  } catch (error) {
    listConfigFormError.value = toMessage(error, '切回表单分区失败,请先修正 JSON')
    activeEditTab.value = 'json'
  }
}

/* ---------------- 提交 ---------------- */

async function submitListConfig() {
  if (listConfigDialogMode.value === 'create' && !trimToUndefined(listConfigForm.resourceCode)) {
    listConfigFormError.value = '请填写资源编码'
    return
  }
  if (!trimToUndefined(listConfigForm.resourceName)) {
    listConfigFormError.value = '请填写资源名称'
    return
  }
  if (!trimToUndefined(listConfigForm.endpoint)) {
    listConfigFormError.value = '请填写分页接口路径'
    return
  }

  // 不在 JSON 分区时:先把可视化状态序列化成 JSON,再统一校验(JSON 分区已是最新文本)
  if (activeEditTab.value !== 'json') {
    serializeFormStateToJson(formState.value)
  }
  let payload: {
    columnsJson: string
    queryJson: string
    actionsJson: string | null
    formJson: string | null
    pageJson: string | null
    dialogJson: string | null
  }
  try {
    payload = validateJsonText()
  } catch (error) {
    listConfigFormError.value = toMessage(error, 'JSON 校验失败')
    return
  }

  listConfigSubmitting.value = true
  listConfigFormError.value = ''
  try {
    const savePayload = {
      resourceName: listConfigForm.resourceName.trim(),
      endpoint: listConfigForm.endpoint.trim(),
      pageSize: listConfigForm.pageSize,
      columnsJson: payload.columnsJson,
      queryJson: payload.queryJson,
      actionsJson: payload.actionsJson,
      formJson: payload.formJson,
      pageJson: payload.pageJson,
      dialogJson: payload.dialogJson,
      status: listConfigForm.status,
      remark: trimToUndefined(listConfigForm.remark),
    }
    if (listConfigDialogMode.value === 'create') {
      await createListConfig({ ...savePayload, resourceCode: listConfigForm.resourceCode.trim() })
      ElMessage.success('列表配置已创建')
    } else if (selectedListConfig.value) {
      await updateListConfig(selectedListConfig.value.id, savePayload)
      ElMessage.success('列表配置已更新')
    }
    listConfigDialogVisible.value = false
    listConfigStore.invalidate()
    await loadListConfigs()
  } catch (error) {
    listConfigFormError.value = toMessage(
      error,
      listConfigDialogMode.value === 'create' ? '创建列表配置失败' : '保存列表配置失败'
    )
  } finally {
    listConfigSubmitting.value = false
  }
}

async function handleDeleteListConfig(row: ListConfigSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除列表配置「${row.resourceName || row.resourceCode}」吗?使用该配置的列表页将无法展示数据。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await deleteListConfig(row.id)
    ElMessage.success('列表配置已删除')
    listConfigStore.invalidate()
    await loadListConfigs()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除列表配置失败'))
  }
}

onMounted(async () => {
  await Promise.allSettled([ensureStatusDictLoaded(), loadListConfigs()])
})
</script>

<style scoped>
.panel-count {
  margin-left: 8px;
  font-size: 13px;
  font-weight: 400;
  color: var(--text-secondary);
}

.source-ref-tag {
  margin: 0 4px 4px 0;
}

/* 新增弹窗顶部「从现有配置复制」 */
.copy-source {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 10px 12px;
  border-radius: var(--radius-block);
  background: rgba(35, 76, 119, 0.06);
}

.copy-source-label {
  font-size: 13px;
  color: #234c77;
  flex: none;
}

/* 配置分区编辑 */
.editor-tabs :deep(.el-tabs__content) {
  padding-top: 8px;
  overflow-x: hidden;
}

/* 全屏弹窗:header/body/footer 纵向排布,body 占满剩余高度并滚动 */
.list-config-dialog :deep(.el-dialog--fullscreen) {
  display: flex;
  flex-direction: column;
}

.list-config-dialog :deep(.el-dialog__body) {
  flex: 1;
  overflow-y: auto;
  padding-top: 8px;
}

/* 基本信息横排条 */
.config-meta-bar {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 0 16px;
  margin-bottom: 4px;
}

.config-meta-bar .el-form-item {
  margin-bottom: 10px;
}

.config-meta-bar .el-form-item__label {
  padding-bottom: 2px;
  font-size: 12px;
  line-height: 1.4;
  color: #64748b;
  height: auto;
}

.meta-item {
  width: 180px;
  min-width: 150px;
}

.meta-code {
  width: 220px;
}

.meta-endpoint {
  width: 300px;
  flex: 1 1 260px;
}

.meta-remark {
  width: 240px;
  flex: 1 1 200px;
}

.meta-small {
  width: 110px;
  min-width: 100px;
}

.config-meta-bar .el-input-number {
  width: 100%;
}

/* 各分区顶部的一句话说明 */
.tab-pane-desc {
  margin-bottom: 10px;
}

.editor-block-desc {
  font-size: 12px;
  font-weight: 400;
  color: #94a3b8;
}

/* 教程弹窗 */
.guide-section {
  margin-bottom: 18px;
}

.guide-title {
  margin: 0 0 6px;
  font-size: 14px;
  color: #234c77;
}

.guide-text {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-primary);
}

.guide-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-primary);
}

.guide-list a {
  color: #234c77;
}

.guide-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.guide-table th,
.guide-table td {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
  vertical-align: top;
  line-height: 1.6;
}

.guide-table th {
  background: rgba(35, 76, 119, 0.06);
  font-weight: 600;
  white-space: nowrap;
}

.guide-table code {
  background: rgba(35, 76, 119, 0.08);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 12px;
  white-space: nowrap;
}
</style>
