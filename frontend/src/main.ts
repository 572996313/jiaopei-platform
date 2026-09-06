import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import { store } from './stores'
import { useAuthStore } from './stores/auth'
import './styles/index.css'

async function bootstrap() {
  const app = createApp(App)
  app.use(store)
  app.use(router)
  app.use(ElementPlus, { locale: zhCn })

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
