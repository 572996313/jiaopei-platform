<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { useBranding } from '@/composables/use-branding'
import { isApiError } from '@/utils/request'
import { takePendingRoute } from '@/utils/auth-session'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const { brandName, brandTagline, brandMark, loginCopy, loginFormTitle, loadBranding } = useBranding()
const loading = ref(false)

/** 导航栏是 custom,内容要自己让出状态栏高度 */
const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 0

loadBranding()

onShow(() => {
  // 已登录用户从分享/扫码等入口回到小程序时,直接回到他本来要看的那一页(没有则首页)
  if (authStore.isLoggedIn()) {
    uni.reLaunch({ url: takePendingRoute() || '/pages/index/index' })
  }
})

async function handleWechatLogin() {
  if (loading.value) {
    return
  }
  loading.value = true
  try {
    const bound = await authStore.loginByWechat()
    if (bound) {
      uni.reLaunch({ url: takePendingRoute() || '/pages/index/index' })
    } else {
      // 首次使用,引导选择身份完成注册;没登录进去的那一页仍留在 storage,注册成功后由注册页取
      uni.navigateTo({ url: '/pages/register/index' })
    }
  } catch (error) {
    const message = isApiError(error)
      ? error.message
      : (error as Error)?.message || '登录失败,请重试'
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 主视觉:一周七天的格子,填充位是固定图案,亮波只是装饰,不表示任何真实数据(与 PC 登录页同一份) */
const WEEK_DAYS = ['一', '二', '三', '四', '五', '六', '日']
const FILLED_ROWS = ['0110100', '1101011', '0100110', '1011001', '0110110']
const LEAD_CELLS = [
  [1, 3],
  [3, 0],
  [2, 4],
]

const motifCells = FILLED_ROWS.flatMap((row, r) =>
  [...row].map((flag, c) => {
    const isLead = LEAD_CELLS.some(([lr, lc]) => lr === r && lc === c)
    return {
      key: `${r}-${c}`,
      cls: isLead ? 'lead' : flag === '1' ? 'on' : 'off',
      // 相位按行列偏移,负延迟让亮波从左上往右下一格一格落
      phase: `${-(r * 0.34 + c * 0.13).toFixed(2)}s`,
    }
  }),
)
</script>

<template>
  <view class="login-page">
    <view class="login-bg">
      <view class="blob blob-1"></view>
      <view class="blob blob-2"></view>
      <view class="blob blob-3"></view>
      <view class="blob blob-4"></view>
      <view class="bg-grid"></view>
    </view>

    <view class="login-body" :style="{ paddingTop: statusBarHeight + 24 + 'px' }">
      <view class="brand">
        <view class="brand-kicker">
          <text class="brand-mark">{{ brandMark }}</text>
          <text class="brand-name">{{ brandName }}</text>
        </view>
        <text class="brand-title">{{ brandTagline }}</text>
        <text v-if="loginCopy" class="brand-copy">{{ loginCopy }}</text>
      </view>

      <view class="motif">
        <view class="motif-head">
          <text v-for="day in WEEK_DAYS" :key="day">{{ day }}</text>
        </view>
        <view class="motif-grid">
          <view
            v-for="cell in motifCells"
            :key="cell.key"
            :class="['motif-cell', cell.cls]"
            :style="{ animationDelay: cell.phase }"
          ></view>
        </view>
      </view>

      <view class="form-card">
        <text class="card-kicker">微信登录</text>
        <text class="card-title">{{ loginFormTitle }}</text>

        <button class="login-btn" :loading="loading" :disabled="loading" @click="handleWechatLogin">
          微信一键登录
        </button>
        <text class="login-hint">首次使用将自动注册,选择学生/家长身份即可</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
/* 配色与 PC 登录页(login-shell 夜晚档)同一套取值,改一边要同步另一边。
   小程序不支持 backdrop-filter,卡片的通透感靠加深实底换可读性。 */
.login-page {
  position: relative;
  min-height: 100vh;
  box-sizing: border-box;
  background: #0f1e35;
}

/* 光斑超出屏幕,由这一层裁掉;页面本身不裁,内容超高时要能正常滚 */
.login-bg {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
}

.blob {
  position: absolute;
  border-radius: 50%;
  animation: blob-drift 26s ease-in-out infinite alternate;
}

.blob-1 {
  width: 900rpx;
  height: 900rpx;
  left: -220rpx;
  top: -260rpx;
  background: radial-gradient(circle, #21456f 0%, rgba(33, 69, 111, 0) 66%);
}

.blob-2 {
  width: 780rpx;
  height: 780rpx;
  right: -240rpx;
  top: 120rpx;
  animation-delay: -9s;
  background: radial-gradient(circle, #1c3a63 0%, rgba(28, 58, 99, 0) 64%);
}

.blob-3 {
  width: 820rpx;
  height: 820rpx;
  left: 120rpx;
  bottom: -360rpx;
  animation-delay: -17s;
  background: radial-gradient(circle, #2c3f6b 0%, rgba(44, 63, 107, 0) 66%);
}

/* 唯一的高光点:小面积金 */
.blob-4 {
  width: 380rpx;
  height: 380rpx;
  left: 330rpx;
  top: 900rpx;
  opacity: 0.3;
  animation-delay: -4s;
  background: radial-gradient(circle, rgba(249, 199, 79, 0.5) 0%, rgba(249, 199, 79, 0) 64%);
}

@keyframes blob-drift {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(40rpx, -50rpx, 0) scale(1.12);
  }
}

.bg-grid {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background-image:
    repeating-linear-gradient(to right, rgba(255, 255, 255, 0.045) 0 1px, rgba(0, 0, 0, 0) 1px 120rpx),
    repeating-linear-gradient(to bottom, rgba(255, 255, 255, 0.045) 0 1px, rgba(0, 0, 0, 0) 1px 104rpx);
}

.login-body {
  position: relative;
  display: flex;
  flex-direction: column;
  /* 卡片靠 margin-top:auto 落到屏底,前提是本列撑满一屏 */
  min-height: 100vh;
  box-sizing: border-box;
  padding-left: 56rpx;
  padding-right: 56rpx;
  padding-bottom: calc(48rpx + env(safe-area-inset-bottom));
}

.brand {
  display: flex;
  flex-direction: column;
  animation: rise 0.6s ease both;
}

.brand-kicker {
  display: flex;
  align-items: center;
}

.brand-mark {
  width: 56rpx;
  height: 56rpx;
  margin-right: 16rpx;
  border: 1px solid rgba(249, 199, 79, 0.45);
  border-radius: 14rpx;
  background: rgba(249, 199, 79, 0.14);
  font-size: 28rpx;
  font-weight: 700;
  line-height: 54rpx;
  text-align: center;
  color: #ffd873;
}

.brand-name {
  font-size: 26rpx;
  letter-spacing: 4rpx;
  color: rgba(232, 240, 252, 0.72);
}

.brand-title {
  margin-top: 30rpx;
  font-size: 60rpx;
  font-weight: 700;
  line-height: 1.3;
  color: #ffffff;
}

.brand-copy {
  margin-top: 20rpx;
  font-size: 27rpx;
  line-height: 1.8;
  color: rgba(232, 240, 252, 0.72);
}

.motif {
  /* 与卡片各占一个 auto 外边距:高屏把空档均分成两段,矮屏自动收拢成紧凑堆叠 */
  margin-top: auto;
  margin-bottom: 48rpx;
  padding: 26rpx 28rpx;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.05);
  animation: rise 0.6s ease 0.12s both;
}

.motif-head {
  display: flex;
  margin-bottom: 14rpx;
}

.motif-head text {
  flex: 1;
  font-size: 21rpx;
  text-align: center;
  color: rgba(232, 240, 252, 0.62);
}

.motif-grid {
  display: flex;
  flex-wrap: wrap;
}

.motif-cell {
  width: calc((100% - 48rpx) / 7);
  height: 28rpx;
  margin: 0 8rpx 8rpx 0;
  border-radius: 8rpx;
}

.motif-cell:nth-child(7n) {
  margin-right: 0;
}

.motif-cell.off {
  background: rgba(255, 255, 255, 0.06);
}

.motif-cell.on {
  background: linear-gradient(180deg, rgba(158, 196, 234, 0.95), rgba(94, 132, 180, 0.8));
  animation: cell-wave 7s ease-in-out infinite;
}

.motif-cell.lead {
  background: linear-gradient(180deg, #ffd873, #f9c74f);
  animation: lead-glow 7s ease-in-out infinite;
}

@keyframes cell-wave {
  0%,
  66%,
  100% {
    opacity: 0.3;
  }
  30% {
    opacity: 1;
  }
}

@keyframes lead-glow {
  0%,
  66%,
  100% {
    opacity: 0.45;
    box-shadow: none;
  }
  30% {
    opacity: 1;
    box-shadow: 0 0 18rpx rgba(249, 199, 79, 0.55);
  }
}

.form-card {
  margin-top: auto;
  padding: 40rpx 40rpx 36rpx;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 28rpx;
  background: rgba(12, 24, 43, 0.72);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.14);
  animation: rise 0.6s ease 0.24s both;
}

.card-kicker {
  font-size: 22rpx;
  letter-spacing: 3rpx;
  color: rgba(232, 240, 252, 0.62);
}

.card-title {
  display: block;
  margin-top: 14rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: #ffffff;
}

.login-btn {
  width: 100%;
  margin-top: 36rpx;
  height: 96rpx;
  line-height: 96rpx;
  border-radius: 48rpx;
  background: linear-gradient(180deg, #4178b0, #234c77);
  box-shadow: 0 16rpx 34rpx -18rpx rgba(0, 0, 0, 0.9);
  font-size: 32rpx;
  font-weight: 500;
  color: #ffffff;
}

/* 去掉小程序 button 自带的描边,否则深色卡上会浮一圈白边 */
.login-btn::after {
  border: none;
}

.login-btn[disabled] {
  opacity: 0.6;
  background: linear-gradient(180deg, #4178b0, #234c77);
  color: #ffffff;
}

.login-hint {
  display: block;
  margin-top: 24rpx;
  font-size: 24rpx;
  text-align: center;
  color: rgba(232, 240, 252, 0.62);
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(16rpx);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
</style>
