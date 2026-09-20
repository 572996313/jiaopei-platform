import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

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
          patterns: [{ group: ['@/*'], message: '@/ 是壳工程别名，业务包内请用本包别名引用。' }],
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
