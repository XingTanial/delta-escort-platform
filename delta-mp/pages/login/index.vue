<template>
  <view class="login-page">
    <view class="logo-area">
      <image class="logo" :src="siteStore.logo || '/static/images/logo.png'" mode="aspectFit" />
      <text class="title">{{ siteStore.siteName }}</text>
      <text class="subtitle">{{ siteStore.subtitle }}</text>
    </view>

    <!-- 登录方式切换 -->
    <view class="tab-bar">
      <view class="tab-item" :class="{ active: loginMode === 'wechat' }" @click="switchWechatMode">{{ quickLoginTitle }}</view>
      <view class="tab-item" :class="{ active: loginMode === 'account' }" @click="loginMode = 'account'">账号登录</view>
    </view>

    <view class="login-area">
      <!-- 快速登录 -->
      <template v-if="loginMode === 'wechat'">
        <template v-if="canUseWechatLogin">
          <button v-if="!needPhoneAuth" class="login-btn wechat-btn" @click="handleWechatLogin">快速登录</button>
          <button v-else class="login-btn wechat-btn" open-type="getPhoneNumber" @getphonenumber="handlePhoneAuthorize">
            首次登录请授权手机号
          </button>
          <view v-if="needPhoneAuth" class="wechat-tip">检测到是首次登录，授权手机号后即可完成注册</view>
        </template>
        <template v-else>
          <input v-model="phone" type="number" maxlength="11" placeholder="请输入手机号" class="input" />
          <input v-model="verifyCode" type="number" maxlength="6" placeholder="请输入验证码" class="input" />
          <view class="login-btn wechat-btn" @click="handleH5Login">登录</view>
        </template>
      </template>

      <!-- 账号密码登录 -->
      <template v-else>
        <input v-model="username" placeholder="请输入账号" class="input" />
        <input v-model="password" type="password" placeholder="请输入密码" class="input" />
        <view class="login-btn account-btn" @click="handleAccountLogin">登录</view>
      </template>

      <view class="agreement">
        <view class="agree-row">
          <view class="checkbox" :class="{ checked: agreed }" @click="toggleAgree">
            <text v-if="agreed" class="check-icon">✔</text>
          </view>
          <text class="text">我已阅读并同意</text>
          <text class="link" @click="goPage('/pages/agreement/user')">《用户协议》</text>
          <text class="text">和</text>
          <text class="link" @click="goPage('/pages/agreement/privacy')">《隐私政策》</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useSiteStore } from '@/store/site'
import { useChatStore } from '@/store/chat'
import { csLogin } from '@/api/auth'
import { setCsToken, setCsInfo } from '@/utils/auth'
import { isMpWeixin } from '@/utils/platform'

const userStore = useUserStore()
const chatStore = useChatStore()
const appStore = useAppStore()
const siteStore = useSiteStore()

const loginMode = ref('wechat')
const username = ref('')
const password = ref('')
const phone = ref('')
const verifyCode = ref('')
const agreed = ref(false)
const needPhoneAuth = ref(false)
const canUseWechatLogin = isMpWeixin()
const quickLoginTitle = canUseWechatLogin ? '快速登录' : '手机登录'

async function handleWechatLogin() {
  if (!agreed.value) {
    return uni.showToast({ title: '请先阅读并勾选同意协议', icon: 'none' })
  }
  const result = await userStore.login()
  if (result.success) {
    completeWechatLogin()
    return
  }
  if (result.code === 1006) {
    needPhoneAuth.value = true
  }
}

async function handlePhoneAuthorize(e) {
  if (!agreed.value) {
    return uni.showToast({ title: '请先阅读并勾选同意协议', icon: 'none' })
  }
  if (e.detail.errMsg && e.detail.errMsg.indexOf('deny') > -1) {
    return uni.showToast({ title: '首次登录需授权手机号', icon: 'none' })
  }
  const phoneCode = e.detail.code || ''
  const result = await userStore.login(phoneCode)
  if (result.success) completeWechatLogin()
}

async function handleH5Login() {
  if (!agreed.value) {
    return uni.showToast({ title: '请先阅读并勾选同意协议', icon: 'none' })
  }
  if (!/^1\d{10}$/.test(phone.value)) {
    return uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
  }
  if (!verifyCode.value) {
    return uni.showToast({ title: '请输入验证码', icon: 'none' })
  }
  const result = await userStore.loginByPhone(phone.value, verifyCode.value)
  if (result.success) completeWechatLogin()
}

async function handleAccountLogin() {
  if (!agreed.value) {
    return uni.showToast({ title: '请先阅读并勾选同意协议', icon: 'none' })
  }
  if (!username.value || !password.value) {
    return uni.showToast({ title: '请输入账号密码', icon: 'none' })
  }
  try {
    const res = await csLogin({ username: username.value, password: password.value, role: 'cs' })
    setCsToken(res.data.token)
    setCsInfo({ adminId: res.data.adminId, nickname: res.data.nickname, avatar: res.data.avatar, role: res.data.role })
    appStore.switchToCs()
    chatStore.connect()
    chatStore.fetchMessageUnreadCount()
  } catch (e) {
    uni.showToast({ title: '登录失败，请检查账号密码', icon: 'none' })
  }
}

function goPage(url) {
  uni.navigateTo({ url })
}

function switchWechatMode() {
  loginMode.value = 'wechat'
}

function completeWechatLogin() {
  needPhoneAuth.value = false
  chatStore.connect()
  uni.reLaunch({ url: '/pages/index/index' })
}

function toggleAgree() {
  agreed.value = !agreed.value
}
</script>

<style lang="scss" scoped>
.login-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f8fafc;
  padding: 0 60rpx;
  position: relative;
}

.logo-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 80rpx;
  position: relative;
  z-index: 1;

  .logo { width: 160rpx; height: 160rpx; margin-bottom: 24rpx; border-radius: 24rpx; }
  .title { font-size: 44rpx; font-weight: bold; color: #4f46e5; }
  .subtitle { font-size: 26rpx; color: #94a3b8; margin-top: 12rpx; }
}

.tab-bar {
  display: flex;
  width: 100%;
  margin-bottom: 60rpx;
  border-bottom: 2rpx solid #e2e8f0;
  position: relative;
  z-index: 1;

  .tab-item {
    flex: 1;
    text-align: center;
    font-size: 30rpx;
    color: #94a3b8;
    padding-bottom: 20rpx;
    position: relative;
    transition: color 0.2s;

    &.active {
      color: #4f46e5;
      font-weight: bold;

      &::after {
        content: '';
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        width: 48rpx;
        height: 4rpx;
        background: linear-gradient(90deg, #6366f1, #8b5cf6);
        border-radius: 2rpx;
      }
    }
  }
}

.login-area {
  width: 100%;
  position: relative;
  z-index: 1;

  .input {
    height: 88rpx;
    background: #f8fafc;
    border: 1rpx solid #e2e8f0;
    border-radius: 12rpx;
    padding: 0 32rpx;
    font-size: 30rpx;
    color: #1e293b;
    margin-bottom: 32rpx;
  }

  .login-btn {
    width: 100%;
    height: 88rpx;
    line-height: 88rpx;
    text-align: center;
    color: #ffffff;
    font-size: 32rpx;
    font-weight: bold;
    border-radius: 999rpx;
    border: none;

    &::after { border: none; }
  }

  .wechat-btn {
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    color: #ffffff;
  }

  .wechat-tip {
    margin-top: 20rpx;
    font-size: 24rpx;
    line-height: 1.6;
    color: #94a3b8;
    text-align: center;
  }

  .account-btn {
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    margin-top: 8rpx;
  }

  .agreement {
    margin-top: 32rpx;
    font-size: 24rpx;
    display: flex;
    justify-content: center;

    .agree-row {
      display: flex;
      align-items: center;
      gap: 8rpx;
    }

    .checkbox {
      width: 32rpx;
      height: 32rpx;
      border-radius: 6rpx;
      border: 2rpx solid rgba(99, 102, 241, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      box-sizing: border-box;
    }

    .checkbox.checked {
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      border-color: transparent;
    }

    .check-icon {
      font-size: 22rpx;
      color: #ffffff;
    }

    .text { color: #94a3b8; }
    .link { color: #4f46e5; }
  }
}
</style>
