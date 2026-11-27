import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

// 教务 views 的功能域目录：页面与"只服务本域"的弹窗按域归档，
// 跨域复用的组件留在 views/components（该目录对域规则豁免）。
const EDUCATION_DOMAINS = [
  'teacher',
  'course',
  'student',
  'teachingclass',
  'schedule',
  'attendance',
  'announcement',
  'message',
  'fee',
]

const ALIAS_ONLY = { group: ['@/*'], message: '@/ 是壳工程别名，业务包内请用本包别名引用。' }

/** 域间不许横向引用：只放行本域、views/components 与包外依赖 */
function crossDomainPatterns(domain) {
  return EDUCATION_DOMAINS.filter((other) => other !== domain).map((other) => ({
    group: [`../${other}/*`, `@cornerstone/module-education/views/${other}/*`],
    message: `教务页面按功能域分目录，${domain} 不得引用 ${other}；跨域复用的组件放 views/components。`,
  }))
}

export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },
  {
    name: 'app/files-to-ignore',
    ignores: ['dist/**', 'node_modules/**', 'coverage/**'],
  },
  // 包边界：底座不依赖业务包，业务包不横向依赖，@/ 只归壳工程用
  {
    name: 'boundary/core',
    files: ['packages/core/**/*.{ts,vue}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@cornerstone/module-*'],
              message: '底座包不得依赖业务模块包；需要业务数据时走 extension/module-extensions 扩展点。',
            },
            { group: ['@/*'], message: '@/ 是壳工程别名，底座包内请用 @cornerstone/core/... 引用。' },
          ],
        },
      ],
    },
  },
  {
    name: 'boundary/module-organization',
    files: ['packages/module-organization/**/*.{ts,vue}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@cornerstone/module-education*'],
              message: '依赖方向是 education -> organization，组织模块不得反向引用教务。',
            },
            { group: ['@/*'], message: '@/ 是壳工程别名，业务包内请用本包别名引用。' },
          ],
        },
      ],
    },
  },
  {
    name: 'boundary/module-education',
    files: ['packages/module-education/**/*.{ts,vue}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [ALIAS_ONLY],
        },
      ],
    },
  },
  // 教务包内布局：views 按功能域分目录，域间不横向引用
  ...EDUCATION_DOMAINS.map((domain) => ({
    name: `layout/module-education-views-${domain}`,
    files: [`packages/module-education/src/views/${domain}/**/*.{ts,vue}`],
    rules: {
      'no-restricted-imports': [
        'error',
        { patterns: [ALIAS_ONLY, ...crossDomainPatterns(domain)] },
      ],
    },
  })),
  // views/components 是跨域共用层，同样不许反过来依赖任何功能域
  {
    name: 'layout/module-education-views-components',
    files: ['packages/module-education/src/views/components/**/*.{ts,vue}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            ALIAS_ONLY,
            ...EDUCATION_DOMAINS.map((other) => ({
              group: [`../${other}/*`, `@cornerstone/module-education/views/${other}/*`],
              message: `views/components 是跨域共用层，不得依赖功能域 ${other}。`,
            })),
          ],
        },
      ],
    },
  },
  // 公共层（api / composables / utils）不许反向依赖 views，否则环会重新长出来
  {
    name: 'layout/module-education-no-views-from-shared',
    files: [
      'packages/module-education/src/api/**/*.{ts,vue}',
      'packages/module-education/src/composables/**/*.{ts,vue}',
      'packages/module-education/src/utils/**/*.{ts,vue}',
    ],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            ALIAS_ONLY,
            {
              group: ['../views/*', '../../views/*', '@cornerstone/module-education/views/*'],
              message: 'api / composables / utils 不得依赖 views（会形成 composables -> views 的反向依赖）。',
            },
          ],
        },
      ],
    },
  },
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  skipFormatting,
  {
    rules: {
      // 项目视图组件多为单词名（如 DashboardView 之外的 shared 等），不强制多词
      'vue/multi-word-component-names': 'off',
      // 存量代码宽松起步：以下规则先告警不阻塞，清理后逐步收紧
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
)
