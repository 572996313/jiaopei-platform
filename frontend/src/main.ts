import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import { router } from './router'
import { store } from '@cornerstone/core/stores'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { permissionDirective } from '@cornerstone/core/directives/permission'
import { installDialogResize } from '@cornerstone/core/utils/dialog-resize'
// 只为副作用 import:模块加载即把 html.dark 类刷上,首帧外观就正确,不等 AppLayout onMounted
import '@cornerstone/core/composables/use-theme'
import '@cornerstone/core/styles/index.css'

async function bootstrap() {
  const app = createApp(App)
  app.use(store)
  app.use(router)
  // 弹窗全局可拖动：Element Plus 只认全局配置的 dialog.draggable，逐个弹窗写 draggable 容易漏
  app.use(ElementPlus, { locale: zhCn, dialog: { draggable: true } })
  // 右下角拖拽改尺寸（Element Plus 无此能力，见 utils/dialog-resize.ts）
  installDialogResize()
  app.directive('permission', permissionDirective)

  const auth = useAuthStore(store)
  await auth.bootstrap()
  await router.isReady()

  if (auth.isAuthenticated && router.currentRoute.value.path === '/login') {
    const redirect = auth.mustChangePassword
      ? '/change-password'
      : typeof router.currentRoute.value.query.redirect === 'string'
        ? router.currentRoute.value.query.redirect
        : '/dashboard'
    await router.replace(redirect)
  }

  app.mount('#app')
}

bootstrap()
