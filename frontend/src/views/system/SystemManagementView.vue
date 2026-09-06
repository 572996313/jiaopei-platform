<template>
  <section class="education-page">
    <div class="education-hero">
      <div>
        <div class="page-kicker">系统 · 权限 · 字典</div>
        <h2 class="panel-headline">系统管理</h2>
        <p class="panel-subtitle">
          当前页面承接用户、角色、菜单和字典的最小可用链，并接入真实改密与菜单可见性能力。
        </p>
      </div>

      <div class="education-hero-actions">
        <el-button :loading="pageLoading" @click="refreshAll">刷新数据</el-button>
        <el-button v-if="activeTab === 'users' && canManageUsers" type="primary" @click="openCreateUserDialog">新增用户</el-button>
        <el-button v-else-if="activeTab === 'roles' && canManageRoles" type="primary" @click="openCreateRoleDialog">新增角色</el-button>
        <el-button v-else-if="activeTab === 'menus' && canManageMenus" type="primary" @click="openCreateMenuDialog">新增菜单</el-button>
        <el-button v-else-if="activeTab === 'configs' && canWriteConfigs" type="primary" @click="openCreateConfigDialog">新增参数</el-button>
      </div>
    </div>

    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <div class="dashboard-grid education-stat-grid">
      <article class="stat-card">
        <div class="stat-label">用户数</div>
        <div class="stat-value">{{ users.length }}</div>
        <div class="stat-hint">数据来自用户管理接口</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">角色数</div>
        <div class="stat-value">{{ roles.length }}</div>
        <div class="stat-hint">数据来自角色管理接口</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">菜单节点</div>
        <div class="stat-value">{{ flattenedMenus.length }}</div>
        <div class="stat-hint">数据来自当前用户菜单接口</div>
      </article>
      <article class="stat-card">
        <div class="stat-label">字典类型</div>
        <div class="stat-value">{{ dictTypes.length }}</div>
        <div class="stat-hint">当前已接「通用状态」字典最小使用链</div>
      </article>
    </div>

    <article class="panel-card education-panel">
      <div class="panel-title">当前用户态</div>
      <div class="system-summary-grid">
        <div class="system-summary-item">
          <strong>当前账号：</strong>{{ auth.currentUser?.username || '未登录' }}
        </div>
        <div class="system-summary-item">
          <strong>显示名称：</strong>{{ auth.currentUser?.displayName || '-' }}
        </div>
        <div class="system-summary-item">
          <strong>机构：</strong>{{ currentInstitutionTitle }}
        </div>
        <div class="system-summary-item">
          <strong>角色：</strong>{{ auth.currentUser?.roles?.join(' / ') || '-' }}
        </div>
        <div class="system-summary-item">
          <strong>机构编码：</strong>{{ currentInstitution?.orgCode || '-' }}
        </div>
        <div class="system-summary-item">
          <strong>联系电话：</strong>{{ currentInstitution?.contactPhone || '-' }}
        </div>
      </div>
      <div class="education-context-text">
        {{
          auth.mustChangePassword
            ? '当前账号已被强制改密标记锁定，只能先完成改密流程。'
            : '菜单显示由当前用户菜单接口驱动，状态枚举优先读取「通用状态」字典。'
        }}
      </div>
    </article>

    <article class="panel-card education-panel">
      <el-tabs v-model="activeTab" class="system-tabs">
        <el-tab-pane
          v-for="tab in availableTabs"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        >
          <template v-if="tab.name === 'institution'">
            <div class="education-table-head">
              <div>
                <div class="panel-title">机构资料</div>
                <div class="education-context-text">
                  维护当前登录账号所属机构的基础档案；机构 ID 和机构编码只读，不提供多机构切换。
                </div>
              </div>
              <div class="education-hero-actions">
                <el-button :loading="institutionLoading" @click="loadCurrentInstitutionProfile">刷新机构资料</el-button>
                <el-button v-if="canWriteOrganization" type="primary" :loading="institutionSubmitting" @click="submitInstitutionProfile">保存机构资料</el-button>
              </div>
            </div>

            <el-alert
              v-if="institutionSectionError"
              :title="institutionSectionError"
              type="error"
              show-icon
              :closable="false"
              class="education-alert"
            />

            <div v-loading="institutionLoading" class="institution-profile-shell">
              <div class="dashboard-grid education-stat-grid">
                <article class="stat-card">
                  <div class="stat-label">当前机构</div>
                  <div class="stat-value institution-stat-text">{{ currentInstitutionTitle }}</div>
                  <div class="stat-hint">{{ currentInstitution?.shortName || '未设置简称' }}</div>
                </article>
                <article class="stat-card">
                  <div class="stat-label">机构编码</div>
                  <div class="stat-value institution-stat-text">{{ currentInstitution?.orgCode || '-' }}</div>
                  <div class="stat-hint">只读，由后端机构档案返回</div>
                </article>
                <article class="stat-card">
                  <div class="stat-label">联系人</div>
                  <div class="stat-value institution-stat-text">{{ currentInstitution?.contactName || '-' }}</div>
                  <div class="stat-hint">{{ currentInstitution?.contactPhone || '未设置联系电话' }}</div>
                </article>
                <article class="stat-card">
                  <div class="stat-label">机构状态</div>
                  <div class="stat-value institution-stat-text">{{ statusLabel(currentInstitution?.status) }}</div>
                  <div class="stat-hint">用于当前机构基础启停展示</div>
                </article>
              </div>

              <el-form label-position="top" class="education-form">
                <div class="system-form-grid">
                  <el-form-item label="机构 ID">
                    <el-input v-model="institutionForm.id" disabled placeholder="后端返回机构 ID" />
                  </el-form-item>
                  <el-form-item label="机构编码">
                    <el-input v-model="institutionForm.orgCode" disabled placeholder="后端返回机构编码" />
                  </el-form-item>
                  <el-form-item label="机构名称">
                    <el-input v-model="institutionForm.name" maxlength="120" placeholder="请输入机构名称" />
                  </el-form-item>
                  <el-form-item label="机构简称">
                    <el-input v-model="institutionForm.shortName" maxlength="80" placeholder="请输入机构简称" />
                  </el-form-item>
                  <el-form-item label="联系人">
                    <el-input v-model="institutionForm.contactName" maxlength="80" placeholder="请输入联系人" />
                  </el-form-item>
                  <el-form-item label="联系电话">
                    <el-input v-model="institutionForm.contactPhone" maxlength="40" placeholder="请输入联系电话" />
                  </el-form-item>
                  <el-form-item label="机构状态">
                    <el-select v-model="institutionForm.status" placeholder="选择状态">
                      <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </el-form-item>
                </div>

                <el-form-item label="机构地址">
                  <el-input v-model="institutionForm.address" maxlength="200" placeholder="请输入机构地址" />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input v-model="institutionForm.remark" type="textarea" :rows="3" maxlength="500" placeholder="补充机构说明" />
                </el-form-item>
              </el-form>
            </div>
          </template>

          <template v-if="tab.name === 'users'">
            <div class="education-table-head">
              <div class="panel-title">用户列表</div>
              <div class="education-context-text">支持创建、编辑、启停、重置密码、分配角色。</div>
            </div>

            <el-table :data="users" border v-loading="pageLoading">
              <el-table-column prop="username" label="账号" width="150" />
              <el-table-column prop="displayName" label="显示名称" min-width="140" />
              <el-table-column label="机构" min-width="140">
                <template #default="{ row }">{{ formatInstitution(row.institutionId) }}</template>
              </el-table-column>
              <el-table-column label="默认校区" min-width="160">
                <template #default="{ row }">{{ formatCampus(row.defaultCampusId) }}</template>
              </el-table-column>
              <el-table-column prop="phone" label="手机号" width="140" />
              <el-table-column label="角色" min-width="160">
                <template #default="{ row }">
                  <div class="system-chip-list">
                    <el-tag v-for="code in row.roleCodes" :key="code" size="small">{{ code }}</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="密码状态" width="140">
                <template #default="{ row }">
                  <el-tag :type="row.forceChangePassword || row.passwordResetRequired ? 'warning' : 'success'">
                    {{ row.forceChangePassword || row.passwordResetRequired ? '需强制改密' : '正常' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="lastLoginAt" label="最近登录" width="180" />
              <el-table-column prop="remark" label="备注" min-width="180" />
              <el-table-column label="操作" width="320" fixed="right">
                <template #default="{ row }">
                  <div class="system-operation-group">
                    <el-button v-if="canManageUsers" text type="primary" @click="openEditUserDialog(row)">编辑</el-button>
                    <el-button v-if="canManageUsers" text @click="toggleUserStatus(row)">
                      {{ row.status === 'active' ? '停用' : '启用' }}
                    </el-button>
                    <el-button v-if="canManageUsers" text @click="handleResetPassword(row)">重置密码</el-button>
                    <el-button v-if="canManageUsers" text @click="openRoleAssignDialog(row)">分配角色</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template v-else-if="tab.name === 'roles'">
            <div class="education-table-head">
              <div class="panel-title">角色列表</div>
              <div class="education-context-text">支持角色维护，并可进一步分配菜单树。</div>
            </div>

            <el-table :data="roles" border v-loading="pageLoading">
              <el-table-column prop="roleCode" label="角色编码" width="180" />
              <el-table-column prop="roleName" label="角色名称" min-width="180" />
              <el-table-column label="权限" min-width="300">
                <template #default="{ row }">
                  <div class="system-chip-list">
                    <el-tag v-for="code in row.permissionCodes" :key="code" size="small">
                      <el-tooltip :content="code" placement="top">
                        <span>{{ permissionLabel(code) }}</span>
                      </el-tooltip>
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="200" />
              <el-table-column label="操作" width="260" fixed="right">
                <template #default="{ row }">
                  <div class="system-operation-group">
                    <el-button v-if="canManageRoles" text type="primary" @click="openEditRoleDialog(row)">编辑</el-button>
                    <el-button v-if="canManageMenus" text @click="openRoleMenuDialog(row)">分配菜单</el-button>
                    <el-button v-if="canManageRoles" text @click="toggleRoleStatus(row)">
                      {{ row.status === 'active' ? '停用' : '启用' }}
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template v-else-if="tab.name === 'menus'">
            <article v-if="menuSectionError" class="education-empty">
              {{ menuSectionError }}
            </article>
            <template v-else>
              <div class="education-table-head">
                <div class="panel-title">菜单与按钮</div>
                <div class="education-context-text">
                  菜单节点决定页面可见性，按钮节点承载接口权限码；在角色管理的「分配菜单」里勾选按钮即授权。
                </div>
              </div>

              <el-table
                :data="flattenedMenus"
                border
                row-key="id"
                :tree-props="{ children: 'children' }"
                v-loading="pageLoading"
              >
                <el-table-column prop="menuName" label="名称" min-width="180" />
                <el-table-column prop="menuCode" label="编码" width="180" />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">{{ menuTypeLabel(row.menuType) }}</template>
                </el-table-column>
                <el-table-column prop="path" label="路径" min-width="160" />
                <el-table-column label="权限码" min-width="170">
                  <template #default="{ row }">
                    <el-tooltip v-if="row.permissionCode" :content="row.permissionCode" placement="top">
                      <span>{{ permissionLabel(row.permissionCode) }}</span>
                    </el-tooltip>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="可见" width="80">
                  <template #default="{ row }">{{ row.visible === false ? '否' : '是' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="remark" label="备注" min-width="160" />
                <el-table-column v-if="canManageMenus" label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <div class="system-operation-group">
                      <el-button text type="primary" @click="openEditMenuDialog(row)">编辑</el-button>
                      <el-button text type="danger" @click="handleDeleteMenu(row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </template>

          <template v-else-if="tab.name === 'configs'">
            <article v-if="configSectionError" class="education-empty">
              {{ configSectionError }}
            </article>
            <template v-else>
              <div class="education-table-head">
                <div class="panel-title">系统参数</div>
                <div class="education-context-text">维护全局键值参数（token 有效期等），支持新增、编辑与删除。</div>
              </div>

              <el-table :data="configs" border v-loading="pageLoading">
                <el-table-column prop="configKey" label="配置键" min-width="200" />
                <el-table-column prop="displayName" label="显示名称" min-width="140" />
                <el-table-column prop="configValue" label="配置值" min-width="160" show-overflow-tooltip />
                <el-table-column prop="valueType" label="值类型" width="100" />
                <el-table-column prop="configGroup" label="分组" width="120" />
                <el-table-column label="系统内置" width="100">
                  <template #default="{ row }">{{ row.isSystem ? '是' : '否' }}</template>
                </el-table-column>
                <el-table-column prop="sortOrder" label="排序" width="80" />
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
                <el-table-column label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <div class="system-operation-group">
                      <el-button v-if="canWriteConfigs" text type="primary" @click="openEditConfigDialog(row)">编辑</el-button>
                      <el-button v-if="canWriteConfigs" text type="danger" :disabled="row.isSystem === true" @click="handleDeleteConfig(row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </template>

          <template v-else-if="tab.name === 'dicts'">
            <article v-if="dictSectionError" class="education-empty">
              {{ dictSectionError }}
            </article>
            <template v-else>
              <div class="education-table-head">
                <div class="panel-title">字典最小接入</div>
                <div class="education-context-text">当前页面只读展示字典类型与条目，并让状态下拉/标签改为字典驱动。</div>
              </div>

              <div class="system-dict-grid">
                <div class="panel-card">
                  <div class="panel-title">字典类型</div>
                  <el-table :data="dictTypes" border highlight-current-row @current-change="handleDictTypeChange">
                    <el-table-column prop="typeCode" label="类型编码" min-width="160" />
                    <el-table-column prop="typeName" label="类型名称" min-width="180" />
                    <el-table-column label="状态" width="110">
                      <template #default="{ row }">
                        <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>

                <div class="panel-card">
                  <div class="panel-title">字典条目</div>
                  <div class="education-context-text system-dict-current">
                    当前类型：{{ selectedDictTypeCode || '未选择' }}
                  </div>
                  <el-table :data="selectedDictItems" border>
                    <el-table-column prop="itemCode" label="编码" width="140" />
                    <el-table-column prop="itemName" label="名称" min-width="140" />
                    <el-table-column prop="itemValue" label="值" min-width="140" />
                    <el-table-column label="前端显示" width="120">
                      <template #default="{ row }">{{ localizedDictLabel(row.itemValue, row.itemName) }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="110">
                      <template #default="{ row }">
                        <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </template>
          </template>
        </el-tab-pane>
      </el-tabs>
    </article>

    <el-dialog v-model="userDialogVisible" :title="userDialogMode === 'create' ? '新增用户' : '编辑用户'" width="620px">
      <el-alert v-if="userFormError" :title="userFormError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <div class="system-form-grid">
          <el-form-item label="机构">
            <el-select v-model="userForm.institutionId" filterable @change="handleUserInstitutionChange">
              <el-option
                v-for="item in institutions"
                :key="item.id"
                :label="item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="默认校区">
            <el-select v-model="userForm.defaultCampusId" clearable filterable>
              <el-option
                v-for="item in userDialogCampuses"
                :key="item.id"
                :label="item.campusName || item.campusCode || `校区 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="账号">
            <el-input v-model="userForm.username" :disabled="userDialogMode === 'edit'" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="显示名称">
            <el-input v-model="userForm.displayName" placeholder="请输入显示名称" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="userForm.phone" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="userForm.status">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item v-if="userDialogMode === 'create'" label="初始化密码">
          <el-input v-model="userForm.initialPassword" type="password" show-password placeholder="留空则使用 Init@123456" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="userForm.remark" type="textarea" :rows="3" placeholder="补充用户说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userSubmitting" @click="submitUser">
          {{ userDialogMode === 'create' ? '创建用户' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" :title="roleDialogMode === 'create' ? '新增角色' : '编辑角色'" width="640px">
      <el-alert v-if="roleFormError" :title="roleFormError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <div class="system-form-grid">
          <el-form-item label="角色编码">
            <el-input v-model="roleForm.roleCode" :disabled="roleDialogMode === 'edit'" placeholder="请输入角色编码" />
          </el-form-item>
          <el-form-item label="角色名称">
            <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="roleForm.status">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="预设权限">
          <el-checkbox-group v-model="roleForm.permissionCodes">
            <div v-for="group in permissionGroupData" :key="group.group" class="system-permission-group">
              <div class="system-permission-group-title">{{ group.group }}</div>
              <div class="system-permission-grid">
                <el-checkbox v-for="item in group.items" :key="item.code" :label="item.code">
                  {{ item.label }}
                </el-checkbox>
              </div>
            </div>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="补充权限码">
          <el-input
            v-model="roleForm.customPermissionCodes"
            type="textarea"
            :rows="3"
            placeholder="可输入额外权限码，使用逗号或换行分隔"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="roleForm.remark" type="textarea" :rows="3" placeholder="补充角色说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="submitRole">
          {{ roleDialogMode === 'create' ? '创建角色' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleAssignDialogVisible" title="分配角色" width="520px">
      <div class="education-dialog-context">
        <div><strong>账号：</strong>{{ selectedUser?.username || '-' }}</div>
        <div><strong>显示名称：</strong>{{ selectedUser?.displayName || '-' }}</div>
      </div>

      <el-alert v-if="roleAssignError" :title="roleAssignError" type="error" show-icon :closable="false" class="education-alert" />

      <el-checkbox-group v-model="selectedRoleIds" class="system-role-checks">
        <el-checkbox v-for="item in roles" :key="item.id" :label="item.id">
          {{ item.roleName }}（{{ item.roleCode }}）
        </el-checkbox>
      </el-checkbox-group>

      <template #footer>
        <el-button @click="roleAssignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleAssignSubmitting" @click="submitRoleAssignment">保存角色</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleMenuDialogVisible" title="分配菜单" width="620px">
      <div class="education-dialog-context">
        <div><strong>角色：</strong>{{ selectedMenuRole?.roleName || '-' }}</div>
        <div><strong>角色编码：</strong>{{ selectedMenuRole?.roleCode || '-' }}</div>
      </div>

      <el-alert v-if="roleMenuError" :title="roleMenuError" type="error" show-icon :closable="false" class="education-alert" />

      <div class="education-context-text">勾选按钮节点即授予该角色对应接口权限，保存后需重新登录生效。</div>

      <el-tree
        ref="roleMenuTreeRef"
        node-key="id"
        show-checkbox
        check-on-click-node
        default-expand-all
        :data="menuTree"
        :props="{ label: 'menuName', children: 'children' }"
        class="system-menu-tree"
      />

      <template #footer>
        <el-button @click="roleMenuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleMenuSubmitting" @click="submitRoleMenus">保存菜单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="configDialogVisible" :title="configDialogMode === 'create' ? '新增系统参数' : '编辑系统参数'" width="620px">
      <el-alert v-if="configFormError" :title="configFormError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <div class="system-form-grid">
          <el-form-item label="配置键">
            <el-input v-model="configForm.configKey" :disabled="configDialogMode === 'edit'" placeholder="如 token.expire-minutes" />
          </el-form-item>
          <el-form-item label="显示名称">
            <el-input v-model="configForm.displayName" placeholder="请输入显示名称" />
          </el-form-item>
          <el-form-item label="配置值">
            <el-input v-model="configForm.configValue" placeholder="请输入配置值" />
          </el-form-item>
          <el-form-item label="值类型">
            <el-select v-model="configForm.valueType" :disabled="configDialogMode === 'edit'">
              <el-option v-for="item in configValueTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="配置分组">
            <el-input v-model="configForm.configGroup" :disabled="configDialogMode === 'edit'" placeholder="如 general / auth" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="configForm.sortOrder" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item v-if="configDialogMode === 'create'" label="是否系统内置">
            <el-switch v-model="configForm.isSystem" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="configForm.status">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="默认值">
          <el-input v-model="configForm.defaultValue" placeholder="选填" />
        </el-form-item>
        <el-form-item label="配置说明">
          <el-input v-model="configForm.description" type="textarea" :rows="3" maxlength="500" placeholder="补充参数用途说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSubmitting" @click="submitConfig">
          {{ configDialogMode === 'create' ? '创建参数' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menuDialogVisible" :title="menuDialogMode === 'create' ? '新增菜单节点' : '编辑菜单节点'" width="620px">
      <el-alert v-if="menuFormError" :title="menuFormError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <div class="system-form-grid">
          <el-form-item label="父节点">
            <el-tree-select
              v-model="menuForm.parentId"
              :data="menuTree"
              node-key="id"
              check-strictly
              default-expand-all
              :props="{ label: 'menuName', children: 'children' }"
              placeholder="不选则为顶级节点"
              clearable
            />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="menuForm.menuType">
              <el-option v-for="item in menuTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="编码">
            <el-input v-model="menuForm.menuCode" :disabled="menuDialogMode === 'edit'" placeholder="如 system_users_manage" />
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="menuForm.menuName" placeholder="按钮节点请填写中文权限名" />
          </el-form-item>
          <el-form-item label="权限码">
            <el-select
              v-model="menuForm.permissionCode"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="按钮节点必填，可从已知码中选择"
            >
              <el-option v-for="item in permissionOptions" :key="item.value" :label="`${item.label}（${item.value}）`" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="menuForm.sortNo" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item label="路径">
            <el-input v-model="menuForm.path" :disabled="menuForm.menuType === 'button'" placeholder="如 /system/users" />
          </el-form-item>
          <el-form-item label="前端组件">
            <el-input v-model="menuForm.component" :disabled="menuForm.menuType === 'button'" placeholder="如 system/users" />
          </el-form-item>
          <el-form-item label="图标">
            <el-input v-model="menuForm.icon" placeholder="选填" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="menuForm.status">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="侧边栏可见">
            <el-switch v-model="menuForm.visible" />
          </el-form-item>
        </div>

        <el-form-item label="备注">
          <el-input v-model="menuForm.remark" type="textarea" :rows="3" maxlength="500" placeholder="补充节点用途说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="menuSubmitting" @click="submitMenu">
          {{ menuDialogMode === 'create' ? '创建节点' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchCampuses, fetchInstitutions, type CampusSummary, type InstitutionSummary } from '@/api/organization'
import {
  createSystemConfig,
  createSystemRole,
  createSystemUser,
  fetchCurrentInstitution,
  deleteSystemConfig,
  fetchSystemConfigs,
  fetchSystemDictItems,
  fetchSystemDictTypes,
  fetchSystemMenusTree,
  createSystemMenu,
  deleteSystemMenu,
  updateSystemMenu,
  fetchSystemRoleMenuIds,
  fetchSystemRoles,
  fetchSystemUsers,
  resetSystemUserPassword,
  updateSystemConfig,
  updateCurrentInstitution,
  updateSystemRole,
  updateSystemRoleMenus,
  updateSystemUser,
  updateSystemUserRoles,
  type CurrentInstitutionProfile,
  type DictItemSummary,
  type DictTypeSummary,
  type SystemMenuTreeNode,
  type SystemConfigSummary,
  type SystemRoleSummary,
  type SystemUserSummary,
} from '@/api/system'
import { useAuthStore } from '@/stores/auth'
import { permissionGroups, permissionLabel } from '@/constants/permissionLabels'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const pageLoading = ref(false)
const pageError = ref('')
const menuSectionError = ref('')
const configSectionError = ref('')
const dictSectionError = ref('')
const institutionSectionError = ref('')

const institutions = ref<InstitutionSummary[]>([])
const campusDirectory = ref<CampusSummary[]>([])
const users = ref<SystemUserSummary[]>([])
const roles = ref<SystemRoleSummary[]>([])
const menuTree = ref<SystemMenuTreeNode[]>([])
const configs = ref<SystemConfigSummary[]>([])
const dictTypes = ref<DictTypeSummary[]>([])
const dictItemsMap = ref<Record<string, DictItemSummary[]>>({})
const selectedDictTypeCode = ref('common_status')
const currentInstitution = ref<CurrentInstitutionProfile | null>(null)

const institutionLoading = ref(false)
const institutionSubmitting = ref(false)

const userDialogVisible = ref(false)
const userDialogMode = ref<'create' | 'edit'>('create')
const userSubmitting = ref(false)
const userFormError = ref('')
const userDialogCampuses = ref<CampusSummary[]>([])
const selectedUser = ref<SystemUserSummary | null>(null)

const roleDialogVisible = ref(false)
const roleDialogMode = ref<'create' | 'edit'>('create')
const roleSubmitting = ref(false)
const roleFormError = ref('')
const selectedRole = ref<SystemRoleSummary | null>(null)

const roleAssignDialogVisible = ref(false)
const roleAssignSubmitting = ref(false)
const roleAssignError = ref('')
const selectedRoleIds = ref<string[]>([])

const roleMenuDialogVisible = ref(false)
const roleMenuSubmitting = ref(false)
const roleMenuError = ref('')
const selectedMenuRole = ref<SystemRoleSummary | null>(null)
const roleMenuTreeRef = ref<any>(null)

const configDialogVisible = ref(false)
const configDialogMode = ref<'create' | 'edit'>('create')
const configSubmitting = ref(false)
const configFormError = ref('')
const selectedConfig = ref<SystemConfigSummary | null>(null)

const configValueTypeOptions = [
  { value: 'string', label: 'string' },
  { value: 'int', label: 'int' },
  { value: 'boolean', label: 'boolean' },
  { value: 'json', label: 'json' },
]

const configForm = reactive({
  configKey: '',
  configValue: '',
  valueType: 'string',
  configGroup: 'general',
  displayName: '',
  description: '',
  defaultValue: '',
  isSystem: false,
  sortOrder: 0,
  status: 'active',
})

const menuDialogVisible = ref(false)
const menuDialogMode = ref<'create' | 'edit'>('create')
const menuSubmitting = ref(false)
const menuFormError = ref('')
const selectedMenuNode = ref<SystemMenuTreeNode | null>(null)

const menuTypeOptions = [
  { value: 'catalog', label: '目录' },
  { value: 'menu', label: '菜单' },
  { value: 'button', label: '按钮' },
]

const menuForm = reactive({
  parentId: '' as string | null,
  menuCode: '',
  menuName: '',
  menuType: 'menu',
  path: '',
  component: '',
  permissionCode: '',
  icon: '',
  sortNo: 0,
  visible: true,
  status: 'active',
  remark: '',
})

const permissionGroupData = permissionGroups()
const permissionOptions = permissionGroupData.flatMap((group) =>
  group.items.map((item) => ({ value: item.code, label: item.label })),
)

const systemTabs = [
  {
    name: 'institution',
    label: '机构资料',
    routePath: '/system/institution',
    menuSignals: ['system_institution', '/system/institution'],
    permissionSignals: ['organization:read', 'organization:write', 'system:user:manage'],
  },
  {
    name: 'users',
    label: '用户管理',
    routePath: '/system/users',
    menuSignals: ['system_users', '/system/users'],
    permissionSignals: ['system:user:manage'],
  },
  {
    name: 'roles',
    label: '角色管理',
    routePath: '/system/roles',
    menuSignals: ['system_roles', '/system/roles'],
    permissionSignals: ['system:role:manage'],
  },
  {
    name: 'menus',
    label: '菜单管理',
    routePath: '/system/menus',
    menuSignals: ['system_menus', '/system/menus'],
    permissionSignals: ['system:menu:manage'],
  },
  {
    name: 'configs',
    label: '系统参数',
    routePath: '/system/config',
    menuSignals: ['system_config', '/system/config'],
    permissionSignals: ['system:config:read', 'system:config:write'],
  },
  {
    name: 'dicts',
    label: '字典使用',
    routePath: '/system/dicts',
    menuSignals: ['system_dicts', '/system/dicts'],
    permissionSignals: ['system:dict:manage'],
  },
] as const

const fallbackStatusOptions = [
  { value: 'active', label: '启用' },
  { value: 'disabled', label: '停用' },
]

const userForm = reactive({
  institutionId: '',
  defaultCampusId: '' as string | null,
  username: '',
  displayName: '',
  phone: '',
  initialPassword: '',
  status: 'active',
  remark: '',
})

const roleForm = reactive({
  roleCode: '',
  roleName: '',
  permissionCodes: [] as string[],
  customPermissionCodes: '',
  status: 'active',
  remark: '',
})

const institutionForm = reactive({
  id: '',
  orgCode: '',
  name: '',
  shortName: '',
  contactName: '',
  contactPhone: '',
  address: '',
  status: 'active',
  remark: '',
})

const activeTab = computed({
  get: () => {
    const matched = systemTabs.find((item) => item.routePath === route.path)
    return matched?.name || availableTabs.value[0]?.name || 'users'
  },
  set: (value) => {
    const matched = systemTabs.find((item) => item.name === value)
    if (matched && matched.routePath !== route.path) {
      router.push(matched.routePath)
    }
  },
})

const availableTabs = computed(() => {
  return systemTabs.filter((item) => {
    return auth.hasMenuAccess([...item.menuSignals]) || item.permissionSignals.some((permission) => auth.hasPermission(permission))
  })
})

const canManageMenus = computed(() => auth.hasPermission('system:menu:manage'))
const canManageUsers = computed(() => auth.hasPermission('system:user:manage'))
const canManageRoles = computed(() => auth.hasPermission('system:role:manage'))
const canWriteConfigs = computed(() => auth.hasPermission('system:config:write'))
const canWriteOrganization = computed(() => auth.hasPermission('organization:write'))

const commonStatusItems = computed(() => dictItemsMap.value.common_status || [])

const statusOptions = computed(() => {
  if (!commonStatusItems.value.length) {
    return fallbackStatusOptions
  }
  return commonStatusItems.value
    .filter((item) => item.status !== 'disabled')
    .map((item) => ({
      value: item.itemValue,
      label: localizedDictLabel(item.itemValue, item.itemName),
    }))
})

const statusLabelMap = computed(() => {
  return new Map(statusOptions.value.map((item) => [item.value, item.label]))
})

const institutionMap = computed(() => {
  return new Map(
    institutions.value.map((item) => [item.id, item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`]),
  )
})

const currentInstitutionTitle = computed(() => {
  const profile = currentInstitution.value
  if (!profile) {
    return formatInstitution(auth.currentUser?.institutionId)
  }
  return profile.name || profile.shortName || profile.orgCode || `机构 #${profile.id}`
})

const campusMap = computed(() => {
  return new Map(
    campusDirectory.value.map((item) => [item.id, item.campusName || item.campusCode || `校区 #${item.id}`]),
  )
})

const flattenedMenus = computed(() => flattenMenuTree(menuTree.value))
const selectedDictItems = computed(() => dictItemsMap.value[selectedDictTypeCode.value] || [])

function flattenMenuTree(items: SystemMenuTreeNode[]): SystemMenuTreeNode[] {
  return items.map((item) => ({
    ...item,
    children: item.children ? flattenMenuTree(item.children) : [],
  }))
}

function normalizeRoleMenuKeys(nodes: SystemMenuTreeNode[], selectedIds: string[]): string[] {
  const selectedSet = new Set(selectedIds)

  const visit = (node: SystemMenuTreeNode): string[] => {
    const childResults: string[] = (node.children || []).flatMap((child) => visit(child))
    const nodeId = String(node.id)

    if (!selectedSet.has(nodeId)) {
      return childResults
    }

    if (!node.children?.length) {
      return [nodeId]
    }

    const childIds = node.children.map((child) => String(child.id))
    const allChildrenSelected = childIds.every((childId) => selectedSet.has(childId))

    if (allChildrenSelected) {
      return [nodeId]
    }

    return childResults
  }

  const normalized: string[] = []
  for (const node of nodes) {
    normalized.push(...visit(node))
  }
  return [...new Set(normalized)]
}

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function localizedDictLabel(value?: string | null, fallback?: string | null) {
  if (value === 'active') {
    return '启用'
  }
  if (value === 'disabled') {
    return '停用'
  }
  return fallback || value || '-'
}

function formatInstitution(id?: string | null) {
  if (!id) {
    return '未设置'
  }
  return institutionMap.value.get(id) || `机构 #${id}`
}

function formatCampus(id?: string | null) {
  if (!id) {
    return '未设置'
  }
  return campusMap.value.get(id) || `校区 #${id}`
}

function statusLabel(status?: string | null) {
  if (!status) {
    return '未设置'
  }
  return statusLabelMap.value.get(status) || localizedDictLabel(status, status)
}

function statusTagType(status?: string | null) {
  return status === 'active' ? 'success' : 'info'
}

function menuTypeLabel(type?: string | null) {
  if (type === 'catalog') {
    return '目录'
  }
  if (type === 'button') {
    return '按钮'
  }
  return '菜单'
}

function parseCustomPermissionCodes(raw: string) {
  return raw
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
}

async function loadCampusDirectory() {
  const allCampuses = await Promise.all(institutions.value.map((item) => fetchCampuses(item.id)))
  campusDirectory.value = allCampuses.flat()
}

function populateInstitutionForm(profile: CurrentInstitutionProfile | null) {
  institutionForm.id = profile?.id || ''
  institutionForm.orgCode = profile?.orgCode || ''
  institutionForm.name = profile?.name || ''
  institutionForm.shortName = profile?.shortName || ''
  institutionForm.contactName = profile?.contactName || ''
  institutionForm.contactPhone = profile?.contactPhone || ''
  institutionForm.address = profile?.address || ''
  institutionForm.status = profile?.status || statusOptions.value[0]?.value || 'active'
  institutionForm.remark = profile?.remark || ''
}

function syncInstitutionDirectory(profile: CurrentInstitutionProfile) {
  const nextItem: InstitutionSummary = {
    id: profile.id,
    orgCode: profile.orgCode || null,
    orgName: profile.name || null,
    shortName: profile.shortName || null,
    status: profile.status || null,
  }
  const exists = institutions.value.some((item) => item.id === profile.id)
  institutions.value = exists
    ? institutions.value.map((item) => (item.id === profile.id ? { ...item, ...nextItem } : item))
    : [nextItem, ...institutions.value]
}

async function loadCurrentInstitutionProfile() {
  institutionLoading.value = true
  institutionSectionError.value = ''
  try {
    const profile = await fetchCurrentInstitution()
    currentInstitution.value = profile
    populateInstitutionForm(profile)
    syncInstitutionDirectory(profile)
  } catch (error) {
    currentInstitution.value = null
    populateInstitutionForm(null)
    institutionSectionError.value = toMessage(error, '当前机构资料加载失败，请确认账号权限')
  } finally {
    institutionLoading.value = false
  }
}

async function loadDictItems(typeCode: string) {
  dictItemsMap.value = {
    ...dictItemsMap.value,
    [typeCode]: await fetchSystemDictItems(typeCode),
  }
}

async function loadPageData() {
  pageLoading.value = true
  pageError.value = ''
  menuSectionError.value = ''
  configSectionError.value = ''
  dictSectionError.value = ''
  institutionSectionError.value = ''

  try {
    const [institutionList, userList, roleList] = await Promise.all([
      fetchInstitutions(),
      fetchSystemUsers(),
      fetchSystemRoles(),
    ])
    institutions.value = institutionList
    users.value = userList
    roles.value = roleList
    await loadCampusDirectory()
    await loadCurrentInstitutionProfile()

    if (canManageMenus.value) {
      try {
        menuTree.value = await fetchSystemMenusTree()
      } catch (error) {
        menuTree.value = []
        menuSectionError.value = toMessage(error, '菜单树加载失败')
      }
    } else {
      menuTree.value = []
    }

    if (availableTabs.value.some((item) => item.name === 'configs')) {
      try {
        configs.value = await fetchSystemConfigs()
      } catch (error) {
        configs.value = []
        configSectionError.value = toMessage(error, '系统参数加载失败')
      }
    } else {
      configs.value = []
    }

    if (availableTabs.value.some((item) => item.name === 'dicts')) {
      try {
        dictTypes.value = await fetchSystemDictTypes()
        await loadDictItems('common_status')
        if (selectedDictTypeCode.value && !dictItemsMap.value[selectedDictTypeCode.value]) {
          await loadDictItems(selectedDictTypeCode.value)
        }
      } catch (error) {
        dictTypes.value = []
        dictSectionError.value = toMessage(error, '字典数据加载失败')
      }
    } else {
      dictTypes.value = []
      dictItemsMap.value = {}
    }
  } catch (error) {
    pageError.value = toMessage(error, '系统管理数据加载失败')
  } finally {
    pageLoading.value = false
  }
}

async function refreshAll() {
  await auth.loadCurrentMenus()
  await loadPageData()
}

async function submitInstitutionProfile() {
  const name = trimToUndefined(institutionForm.name)
  if (!name) {
    institutionSectionError.value = '请填写机构名称'
    return
  }

  institutionSubmitting.value = true
  institutionSectionError.value = ''
  try {
    const profile = await updateCurrentInstitution({
      name,
      shortName: trimToUndefined(institutionForm.shortName),
      contactName: trimToUndefined(institutionForm.contactName),
      contactPhone: trimToUndefined(institutionForm.contactPhone),
      address: trimToUndefined(institutionForm.address),
      status: institutionForm.status || 'active',
      remark: trimToUndefined(institutionForm.remark),
    })
    currentInstitution.value = profile
    populateInstitutionForm(profile)
    syncInstitutionDirectory(profile)
    ElMessage.success('机构资料已保存')
  } catch (error) {
    institutionSectionError.value = toMessage(error, '机构资料保存失败，请确认账号权限')
  } finally {
    institutionSubmitting.value = false
  }
}

function resetUserForm() {
  userForm.institutionId = auth.currentUser?.institutionId || institutions.value[0]?.id || ''
  userForm.defaultCampusId = auth.currentUser?.defaultCampusId || null
  userForm.username = ''
  userForm.displayName = ''
  userForm.phone = ''
  userForm.initialPassword = ''
  userForm.status = statusOptions.value[0]?.value || 'active'
  userForm.remark = ''
  userFormError.value = ''
}

function resetRoleForm() {
  roleForm.roleCode = ''
  roleForm.roleName = ''
  roleForm.permissionCodes = []
  roleForm.customPermissionCodes = ''
  roleForm.status = statusOptions.value[0]?.value || 'active'
  roleForm.remark = ''
  roleFormError.value = ''
}

async function syncUserDialogCampuses() {
  if (!userForm.institutionId) {
    userDialogCampuses.value = []
    userForm.defaultCampusId = null
    return
  }
  try {
    userDialogCampuses.value = await fetchCampuses(userForm.institutionId)
    if (userForm.defaultCampusId && !userDialogCampuses.value.some((item) => item.id === userForm.defaultCampusId)) {
      userForm.defaultCampusId = null
    }
  } catch (error) {
    userDialogCampuses.value = []
    userFormError.value = toMessage(error, '校区选项加载失败')
  }
}

async function handleUserInstitutionChange() {
  await syncUserDialogCampuses()
}

async function openCreateUserDialog() {
  userDialogMode.value = 'create'
  selectedUser.value = null
  resetUserForm()
  await syncUserDialogCampuses()
  userDialogVisible.value = true
}

async function openEditUserDialog(user: SystemUserSummary) {
  userDialogMode.value = 'edit'
  selectedUser.value = user
  userForm.institutionId = user.institutionId
  userForm.defaultCampusId = user.defaultCampusId || null
  userForm.username = user.username
  userForm.displayName = user.displayName
  userForm.phone = user.phone || ''
  userForm.initialPassword = ''
  userForm.status = user.status || 'active'
  userForm.remark = user.remark || ''
  userFormError.value = ''
  await syncUserDialogCampuses()
  userDialogVisible.value = true
}

async function submitUser() {
  if (!userForm.institutionId) {
    userFormError.value = '请选择机构'
    return
  }
  if (!trimToUndefined(userForm.username) && userDialogMode.value === 'create') {
    userFormError.value = '请填写账号'
    return
  }
  if (!trimToUndefined(userForm.displayName)) {
    userFormError.value = '请填写显示名称'
    return
  }

  userSubmitting.value = true
  userFormError.value = ''
  try {
    if (userDialogMode.value === 'create') {
      await createSystemUser({
        institutionId: userForm.institutionId,
        defaultCampusId: userForm.defaultCampusId || null,
        username: userForm.username.trim(),
        displayName: userForm.displayName.trim(),
        phone: trimToUndefined(userForm.phone),
        initialPassword: trimToUndefined(userForm.initialPassword),
        status: userForm.status,
        remark: trimToUndefined(userForm.remark),
      })
      ElMessage.success('用户已创建')
    } else if (selectedUser.value) {
      await updateSystemUser(selectedUser.value.id, {
        institutionId: userForm.institutionId,
        defaultCampusId: userForm.defaultCampusId || null,
        displayName: userForm.displayName.trim(),
        phone: trimToUndefined(userForm.phone),
        status: userForm.status,
        remark: trimToUndefined(userForm.remark),
      })
      ElMessage.success('用户已更新')
    }
    userDialogVisible.value = false
    await loadPageData()
  } catch (error) {
    userFormError.value = toMessage(error, userDialogMode.value === 'create' ? '创建用户失败' : '编辑用户失败')
  } finally {
    userSubmitting.value = false
  }
}

async function toggleUserStatus(user: SystemUserSummary) {
  try {
    await updateSystemUser(user.id, {
      institutionId: user.institutionId,
      defaultCampusId: user.defaultCampusId || null,
      displayName: user.displayName,
      phone: user.phone || undefined,
      status: user.status === 'active' ? 'disabled' : 'active',
      remark: user.remark || undefined,
    })
    ElMessage.success(user.status === 'active' ? '用户已停用' : '用户已启用')
    await loadPageData()
  } catch (error) {
    ElMessage.error(toMessage(error, '用户状态更新失败'))
  }
}

async function handleResetPassword(user: SystemUserSummary) {
  try {
    const result = await resetSystemUserPassword(user.id)
    await ElMessageBox.alert(
      `初始密码：${result.initialPassword}\n登录后请尽快修改密码。`,
      '密码已重置',
      {
        confirmButtonText: '知道了',
      },
    )
    await loadPageData()
  } catch (error) {
    ElMessage.error(toMessage(error, '重置密码失败'))
  }
}

function openRoleAssignDialog(user: SystemUserSummary) {
  selectedUser.value = user
  selectedRoleIds.value = [...user.roleIds]
  roleAssignError.value = ''
  roleAssignDialogVisible.value = true
}

async function submitRoleAssignment() {
  if (!selectedUser.value) {
    return
  }

  roleAssignSubmitting.value = true
  roleAssignError.value = ''
  try {
    await updateSystemUserRoles(selectedUser.value.id, {
      roleIds: selectedRoleIds.value,
    })
    ElMessage.success('用户角色已更新')
    roleAssignDialogVisible.value = false
    await loadPageData()
  } catch (error) {
    roleAssignError.value = toMessage(error, '分配角色失败')
  } finally {
    roleAssignSubmitting.value = false
  }
}

function openCreateRoleDialog() {
  roleDialogMode.value = 'create'
  selectedRole.value = null
  resetRoleForm()
  roleDialogVisible.value = true
}

function openEditRoleDialog(role: SystemRoleSummary) {
  roleDialogMode.value = 'edit'
  selectedRole.value = role
  const knownPermissions = new Set(permissionOptions.map((item) => item.value))
  roleForm.roleCode = role.roleCode
  roleForm.roleName = role.roleName
  roleForm.permissionCodes = role.permissionCodes.filter((item) => knownPermissions.has(item))
  roleForm.customPermissionCodes = role.permissionCodes.filter((item) => !knownPermissions.has(item)).join(', ')
  roleForm.status = role.status || 'active'
  roleForm.remark = role.remark || ''
  roleFormError.value = ''
  roleDialogVisible.value = true
}

function collectRolePermissionCodes() {
  return [...new Set([...roleForm.permissionCodes, ...parseCustomPermissionCodes(roleForm.customPermissionCodes)])]
}

async function submitRole() {
  if (!trimToUndefined(roleForm.roleCode) && roleDialogMode.value === 'create') {
    roleFormError.value = '请填写角色编码'
    return
  }
  if (!trimToUndefined(roleForm.roleName)) {
    roleFormError.value = '请填写角色名称'
    return
  }

  const permissionCodes = collectRolePermissionCodes()
  if (permissionCodes.length === 0) {
    roleFormError.value = '请至少选择或输入一个权限码'
    return
  }

  roleSubmitting.value = true
  roleFormError.value = ''
  try {
    if (roleDialogMode.value === 'create') {
      await createSystemRole({
        roleCode: roleForm.roleCode.trim(),
        roleName: roleForm.roleName.trim(),
        permissionCodes,
        status: roleForm.status,
        remark: trimToUndefined(roleForm.remark),
      })
      ElMessage.success('角色已创建')
    } else if (selectedRole.value) {
      await updateSystemRole(selectedRole.value.id, {
        roleName: roleForm.roleName.trim(),
        permissionCodes,
        status: roleForm.status,
        remark: trimToUndefined(roleForm.remark),
      })
      ElMessage.success('角色已更新')
    }
    roleDialogVisible.value = false
    await loadPageData()
  } catch (error) {
    roleFormError.value = toMessage(error, roleDialogMode.value === 'create' ? '创建角色失败' : '编辑角色失败')
  } finally {
    roleSubmitting.value = false
  }
}

async function toggleRoleStatus(role: SystemRoleSummary) {
  try {
    await updateSystemRole(role.id, {
      roleName: role.roleName,
      permissionCodes: role.permissionCodes,
      status: role.status === 'active' ? 'disabled' : 'active',
      remark: role.remark || undefined,
    })
    ElMessage.success(role.status === 'active' ? '角色已停用' : '角色已启用')
    await loadPageData()
  } catch (error) {
    ElMessage.error(toMessage(error, '角色状态更新失败'))
  }
}

async function openRoleMenuDialog(role: SystemRoleSummary) {
  selectedMenuRole.value = role
  roleMenuError.value = ''
  roleMenuSubmitting.value = false

  try {
    if (!menuTree.value.length) {
      menuTree.value = await fetchSystemMenusTree()
    }
    const menuIds = await fetchSystemRoleMenuIds(role.id)
    roleMenuDialogVisible.value = true
    await nextTick()
    roleMenuTreeRef.value?.setCheckedKeys(normalizeRoleMenuKeys(menuTree.value, menuIds))
  } catch (error) {
    roleMenuError.value = toMessage(error, '角色菜单加载失败')
    roleMenuDialogVisible.value = true
  }
}

function collectCheckedMenuIds(): string[] {
  const checked: Array<string | number> = roleMenuTreeRef.value?.getCheckedKeys?.() || []
  return [...new Set(checked.map((item: string | number) => String(item)))]
}

async function submitRoleMenus() {
  if (!selectedMenuRole.value) {
    return
  }

  roleMenuSubmitting.value = true
  roleMenuError.value = ''
  try {
    await updateSystemRoleMenus(selectedMenuRole.value.id, {
      menuIds: collectCheckedMenuIds(),
    })
    ElMessage.success('角色菜单已更新')
    roleMenuDialogVisible.value = false
  } catch (error) {
    roleMenuError.value = toMessage(error, '保存角色菜单失败')
  } finally {
    roleMenuSubmitting.value = false
  }
}

async function handleDictTypeChange(row?: DictTypeSummary | null) {
  if (!row?.typeCode) {
    return
  }
  selectedDictTypeCode.value = row.typeCode
  if (!dictItemsMap.value[row.typeCode]) {
    try {
      await loadDictItems(row.typeCode)
    } catch (error) {
      dictSectionError.value = toMessage(error, '字典条目加载失败')
    }
  }
}

function openCreateConfigDialog() {
  configDialogMode.value = 'create'
  selectedConfig.value = null
  configForm.configKey = ''
  configForm.configValue = ''
  configForm.valueType = 'string'
  configForm.configGroup = 'general'
  configForm.displayName = ''
  configForm.description = ''
  configForm.defaultValue = ''
  configForm.isSystem = false
  configForm.sortOrder = 0
  configForm.status = statusOptions.value[0]?.value || 'active'
  configFormError.value = ''
  configDialogVisible.value = true
}

function openEditConfigDialog(config: SystemConfigSummary) {
  configDialogMode.value = 'edit'
  selectedConfig.value = config
  configForm.configKey = config.configKey
  configForm.configValue = config.configValue
  configForm.valueType = config.valueType || 'string'
  configForm.configGroup = config.configGroup || ''
  configForm.displayName = config.displayName || ''
  configForm.description = config.description || ''
  configForm.defaultValue = config.defaultValue || ''
  configForm.isSystem = config.isSystem === true
  configForm.sortOrder = config.sortOrder ?? 0
  configForm.status = config.status || 'active'
  configFormError.value = ''
  configDialogVisible.value = true
}

async function submitConfig() {
  if (!trimToUndefined(configForm.configKey) && configDialogMode.value === 'create') {
    configFormError.value = '请填写配置键'
    return
  }
  if (!trimToUndefined(configForm.configValue)) {
    configFormError.value = '请填写配置值'
    return
  }
  if (!trimToUndefined(configForm.displayName)) {
    configFormError.value = '请填写显示名称'
    return
  }
  if (!trimToUndefined(configForm.configGroup) && configDialogMode.value === 'create') {
    configFormError.value = '请填写配置分组'
    return
  }

  configSubmitting.value = true
  configFormError.value = ''
  try {
    if (configDialogMode.value === 'create') {
      await createSystemConfig({
        configKey: configForm.configKey.trim(),
        configValue: configForm.configValue,
        valueType: configForm.valueType,
        configGroup: configForm.configGroup.trim(),
        displayName: configForm.displayName.trim(),
        description: trimToUndefined(configForm.description),
        defaultValue: trimToUndefined(configForm.defaultValue),
        isSystem: configForm.isSystem,
        sortOrder: configForm.sortOrder ?? 0,
        status: configForm.status,
      })
      ElMessage.success('系统参数已创建')
    } else if (selectedConfig.value) {
      await updateSystemConfig(selectedConfig.value.id, {
        configValue: configForm.configValue,
        displayName: configForm.displayName.trim(),
        description: trimToUndefined(configForm.description),
        sortOrder: configForm.sortOrder ?? 0,
        status: configForm.status,
      })
      ElMessage.success('系统参数已更新')
    }
    configDialogVisible.value = false
    await loadPageData()
  } catch (error) {
    configFormError.value = toMessage(error, configDialogMode.value === 'create' ? '创建系统参数失败' : '编辑系统参数失败')
  } finally {
    configSubmitting.value = false
  }
}

async function handleDeleteConfig(config: SystemConfigSummary) {
  try {
    await ElMessageBox.confirm(`确定删除系统参数「${config.displayName || config.configKey}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    await deleteSystemConfig(config.id)
    ElMessage.success('系统参数已删除')
    await loadPageData()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除系统参数失败'))
  }
}

function openCreateMenuDialog() {
  menuDialogMode.value = 'create'
  selectedMenuNode.value = null
  menuForm.parentId = ''
  menuForm.menuCode = ''
  menuForm.menuName = ''
  menuForm.menuType = 'menu'
  menuForm.path = ''
  menuForm.component = ''
  menuForm.permissionCode = ''
  menuForm.icon = ''
  menuForm.sortNo = 0
  menuForm.visible = true
  menuForm.status = statusOptions.value[0]?.value || 'active'
  menuForm.remark = ''
  menuFormError.value = ''
  menuDialogVisible.value = true
}

function openEditMenuDialog(node: SystemMenuTreeNode) {
  menuDialogMode.value = 'edit'
  selectedMenuNode.value = node
  menuForm.parentId = node.parentId || ''
  menuForm.menuCode = node.menuCode
  menuForm.menuName = node.menuName
  menuForm.menuType = node.menuType
  menuForm.path = node.path || ''
  menuForm.component = node.component || ''
  menuForm.permissionCode = node.permissionCode || ''
  menuForm.icon = node.icon || ''
  menuForm.sortNo = node.sortNo ?? 0
  menuForm.visible = node.visible !== false
  menuForm.status = node.status || 'active'
  menuForm.remark = node.remark || ''
  menuFormError.value = ''
  menuDialogVisible.value = true
}

async function reloadMenuTree() {
  menuTree.value = await fetchSystemMenusTree()
}

async function submitMenu() {
  if (menuDialogMode.value === 'create' && !trimToUndefined(menuForm.menuCode)) {
    menuFormError.value = '请填写菜单编码'
    return
  }
  if (!trimToUndefined(menuForm.menuName)) {
    menuFormError.value = '请填写菜单名称'
    return
  }
  if (menuForm.menuType === 'button' && !trimToUndefined(menuForm.permissionCode)) {
    menuFormError.value = '按钮节点必须绑定权限码'
    return
  }

  menuSubmitting.value = true
  menuFormError.value = ''
  try {
    if (menuDialogMode.value === 'create') {
      await createSystemMenu({
        parentId: menuForm.parentId || null,
        menuCode: menuForm.menuCode.trim(),
        menuName: menuForm.menuName.trim(),
        menuType: menuForm.menuType,
        path: trimToUndefined(menuForm.path),
        component: trimToUndefined(menuForm.component),
        permissionCode: trimToUndefined(menuForm.permissionCode),
        icon: trimToUndefined(menuForm.icon),
        sortNo: menuForm.sortNo ?? 0,
        visible: menuForm.visible,
        status: menuForm.status,
        remark: trimToUndefined(menuForm.remark),
      })
      ElMessage.success('菜单已创建')
    } else if (selectedMenuNode.value) {
      await updateSystemMenu(selectedMenuNode.value.id, {
        parentId: menuForm.parentId || null,
        menuName: menuForm.menuName.trim(),
        menuType: menuForm.menuType,
        path: trimToUndefined(menuForm.path),
        component: trimToUndefined(menuForm.component),
        permissionCode: trimToUndefined(menuForm.permissionCode),
        icon: trimToUndefined(menuForm.icon),
        sortNo: menuForm.sortNo ?? 0,
        visible: menuForm.visible,
        status: menuForm.status,
        remark: trimToUndefined(menuForm.remark),
      })
      ElMessage.success('菜单已更新')
    }
    menuDialogVisible.value = false
    await reloadMenuTree()
    await auth.loadCurrentMenus()
  } catch (error) {
    menuFormError.value = toMessage(error, menuDialogMode.value === 'create' ? '创建菜单失败' : '编辑菜单失败')
  } finally {
    menuSubmitting.value = false
  }
}

async function handleDeleteMenu(node: SystemMenuTreeNode) {
  try {
    await ElMessageBox.confirm(`确定删除菜单「${node.menuName}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    await deleteSystemMenu(node.id)
    ElMessage.success('菜单已删除')
    await reloadMenuTree()
    await auth.loadCurrentMenus()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除菜单失败'))
  }
}

watch(
  availableTabs,
  (tabs) => {
    if (!tabs.length) {
      if (route.path.startsWith('/system')) {
        router.replace('/dashboard')
      }
      return
    }
    if (!tabs.some((item) => item.name === activeTab.value)) {
      router.replace(tabs[0].routePath)
    }
  },
  { immediate: true },
)

onMounted(() => {
  loadPageData()
})
</script>
