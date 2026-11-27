/**
 * 教务模块接口入口：按功能域拆到同目录下，这里只做转出。
 *
 * <p>拆分前是 1400 余行的单文件（80 个函数 + 83 个类型），改一处排课要在整份文件里
 * 找位置。现在按域拆开，与后端 `module-education` 的功能域一一对应。页面照旧从
 * `@cornerstone/module-education/api/education` 导入，路径不变。</p>
 */
export * from './student'
export * from './teacher'
export * from './course'
export * from './teachingclass'
export * from './attendance'
export * from './schedule'
