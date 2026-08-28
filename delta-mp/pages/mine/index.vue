<template>
  <view class="mine-page tab-page">
    <scroll-view scroll-y class="mine-scroll tab-page-scroll" :show-scrollbar="false">
      <view class="profile-card" @click="userStore.isLoggedIn ? go('/pages/mine/profile') : goLogin()" @longtap="onLongPress">
        <view class="profile-main">
          <image class="avatar" :src="userStore.avatar || '/static/images/default-avatar.png'" mode="aspectFill" />
          <view class="profile-info" v-if="userStore.isLoggedIn">
            <text class="name">{{ userStore.nickname }}</text>
            <text class="phone" v-if="userStore.phone">{{ formatPhone(userStore.phone) }}</text>
            <text v-else class="phone">完善手机号，方便接收服务通知</text>
          </view>
          <view class="profile-info" v-else>
            <text class="name">点击登录</text>
            <text class="phone">登录后查看订单和钱包</text>
          </view>
          <text class="profile-arrow">></text>
        </view>
      </view>

      <view class="quick-panel">
        <view class="quick-item" @click="go('/pages/order/list')">
          <image class="quick-icon" src="/static/icons/订单.svg" mode="aspectFit" />
          <text>订单</text>
        </view>
        <view class="quick-item" @click="go('/pages/wallet/index')">
          <image class="quick-icon" src="/static/icons/钞票.svg" mode="aspectFit" />
          <text>钱包</text>
        </view>
        <view class="quick-item" @click="go('/pages/message/index')">
          <view class="quick-icon-wrap">
            <image class="quick-icon" src="/static/icons/提示.svg" mode="aspectFit" />
            <view v-if="systemUnread > 0" class="quick-badge">{{ systemUnread > 99 ? '99+' : systemUnread }}</view>
          </view>
          <text>通知</text>
        </view>
        <view class="quick-item" @click="goCustomerService">
          <image class="quick-icon" src="/static/icons/客服.svg" mode="aspectFit" />
          <text>客服</text>
        </view>
      </view>

      <view class="settle-card" @click="goPlayer">
        <view class="settle-icon-box">
          <image class="settle-icon" src="/static/icons/攻略.svg" mode="aspectFit" />
        </view>
        <view class="settle-info">
          <text class="settle-title">我要入驻接单员端</text>
          <text class="settle-desc">通过审核后可接单、查看收益和提现</text>
        </view>
        <text class="settle-action">进入</text>
      </view>

      <view class="section-card">
        <text class="section-title">服务与支持</text>
        <view class="menu-item" @click="go('/pages/complaint/list')">
          <image class="menu-icon" src="/static/icons/警示.svg" mode="aspectFit" />
          <text>我的投诉工单</text><text class="arrow">></text>
        </view>
        <view class="menu-item" @click="go('/pages/agreement/user')">
          <image class="menu-icon" src="/static/icons/暂无权限.svg" mode="aspectFit" />
          <text>用户协议</text><text class="arrow">></text>
        </view>
        <view class="menu-item" @click="go('/pages/agreement/privacy')">
          <image class="menu-icon" src="/static/icons/安全认证.svg" mode="aspectFit" />
          <text>隐私政策</text><text class="arrow">></text>
        </view>
      </view>

      <view v-if="userStore.isLoggedIn" class="logout" @click="userStore.logout()">退出登录</view>
      <view class="tab-page-bottom-spacer"></view>
    </scroll-view>
    <CustomTabBar :current="4" />
  </view>
</template>
<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { useChatStore } from '@/store/chat'
import { useAppStore } from '@/store/app'
import { usePlayerStore } from '@/store/player'
import { getUserToken, setPlayerToken } from '@/utils/auth'
import { switchToPlayerToken } from '@/api/auth'
import { createCsSession } from '@/api/chat'
import CustomTabBar from '@/components/CustomTabBar.vue'
const userStore = useUserStore()
const appStore = useAppStore()
const playerStore = usePlayerStore()
const chatStore = useChatStore()
const longPressCount = ref(0)
const systemUnread = computed(() => chatStore.messageUnreadCount)

// 每次显示页面时检查登录态并刷新用户信息
onShow(() => {
  const savedToken = getUserToken()
  if (savedToken && !userStore.token) {
    userStore.token = savedToken
  }
  if (userStore.token) {
    userStore.fetchProfile(true)
    useChatStore().fetchMessageUnreadCount()
  }
})

const TAB_PAGES = ['/pages/index/index', '/pages/category/index', '/pages/order/list', '/pages/chat/list', '/pages/mine/index']
function go(url) {
  if (TAB_PAGES.includes(url)) {
    uni.switchTab({ url })
  } else {
    uni.navigateTo({ url })
  }
}
function goLogin() { uni.navigateTo({ url: '/pages/login/index' }) }
function formatPhone(p) {
  if (!p || p.length < 7) return p
  return p.slice(0, 3) + '****' + p.slice(-4)
}
async function goPlayer() {
  if (!userStore.checkLogin()) return
  try {
    const profile = await playerStore.fetchProfile({ role: 'user' })
    if (!profile) {
      uni.navigateTo({ url: '/pages-player/apply/index' })
    } else if (profile.status === 'PENDING') {
      uni.showToast({ title: '审核中，请耐心等待', icon: 'none' })
    } else if (profile.status === 'ACTIVE') {
      const res = await switchToPlayerToken()
      if (res?.data?.token) {
        setPlayerToken(res.data.token)
        useChatStore().disconnect()
        appStore.switchToPlayer()
      }
    } else {
      uni.navigateTo({ url: '/pages-player/apply/index' })
    }
  } catch (e) {
    const msg = e?.data?.msg || e?.message
    if (msg) uni.showToast({ title: msg, icon: 'none' })
    if (e?.data?.code === 1002) uni.navigateTo({ url: '/pages-player/apply/index' })
  }
}
async function goCustomerService() {
  if (!userStore.checkLogin()) return
  try {
    const res = await createCsSession()
    const session = res.data
    uni.navigateTo({ url: '/pages/chat/room?sessionId=' + session.id + '&name=' + encodeURIComponent('在线客服') })
  } catch (e) {
    uni.showToast({ title: e?.msg || '连接客服失败', icon: 'none' })
  }
}
function onLongPress() {
  longPressCount.value++
  if (longPressCount.value >= 3) {
    longPressCount.value = 0
    uni.navigateTo({ url: '/pages-cs/login/index' })
  }
}
</script>
<style lang="scss" scoped>
.mine-page {
  background: #f8fafc;
  height: 100vh;
  position: relative;
  overflow: hidden;
  padding-bottom: 0;
}
.mine-scroll {
  height: calc(100vh - 110rpx - env(safe-area-inset-bottom));
  position: relative;
  z-index: 1;
  overflow: hidden;
}

/* #ifdef H5 */
.mine-scroll :deep(.uni-scroll-view::-webkit-scrollbar),
.mine-scroll :deep(.uni-scroll-view-content::-webkit-scrollbar) {
  width: 0;
  height: 0;
  display: none;
}
/* #endif */

.profile-card {
  margin: 24rpx 24rpx 0;
  padding: 30rpx;
  background: linear-gradient(135deg, #ffffff, #f5f3ff);
  border: 1rpx solid rgba(99, 102, 241, 0.14);
  border-radius: 24rpx;
  box-shadow: 0 12rpx 34rpx rgba(15, 23, 42, 0.07);
  position: relative;
  z-index: 1;
}
.profile-main {
  display: flex;
  align-items: center;
}
.avatar {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  background: #ffffff;
  border: 4rpx solid rgba(99, 102, 241, 0.14);
  margin-right: 24rpx;
  flex-shrink: 0;
}
.profile-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.name {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: #111827;
  line-height: 1.25;
}
.phone {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #7c8aa0;
  line-height: 1.35;
}
.profile-arrow {
  color: #cbd5e1;
  font-size: 32rpx;
  flex-shrink: 0;
  margin-left: 12rpx;
}

.quick-panel {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10rpx;
  margin: 22rpx 24rpx 0;
  padding: 24rpx 10rpx;
  background: #ffffff;
  border-radius: 22rpx;
  box-shadow: 0 10rpx 30rpx rgba(15, 23, 42, 0.06);
  position: relative;
  z-index: 1;
}
.quick-item {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  font-size: 24rpx;
  color: #334155;
  font-weight: 600;
}
.quick-icon-wrap {
  position: relative;
}
.quick-icon {
  width: 54rpx;
  height: 54rpx;
}
.quick-badge {
  position: absolute;
  top: -12rpx;
  right: -20rpx;
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  padding: 0 8rpx;
  font-size: 20rpx;
  color: #fff;
  background: #ee0a24;
  border-radius: 32rpx;
  text-align: center;
}

.settle-card {
  display: flex;
  align-items: center;
  margin: 22rpx 24rpx 0;
  padding: 26rpx 24rpx;
  background: linear-gradient(135deg, #eef2ff, #ffffff);
  border: 1rpx solid rgba(99, 102, 241, 0.16);
  border-radius: 22rpx;
  box-shadow: 0 10rpx 30rpx rgba(99, 102, 241, 0.1);
  position: relative;
  z-index: 1;
}
.settle-icon-box {
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  flex-shrink: 0;
}
.settle-icon {
  width: 44rpx;
  height: 44rpx;
  filter: brightness(0) invert(1);
}
.settle-info {
  flex: 1;
  min-width: 0;
}
.settle-title {
  display: block;
  font-size: 28rpx;
  color: #1f2937;
  font-weight: 800;
}
.settle-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.settle-action {
  margin-left: 18rpx;
  padding: 10rpx 22rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.section-card {
  margin: 22rpx 24rpx 0;
  padding-top: 8rpx;
  background: #ffffff;
  border-radius: 22rpx;
  box-shadow: 0 10rpx 30rpx rgba(15, 23, 42, 0.06);
  overflow: hidden;
  position: relative;
  z-index: 1;
}
.section-title {
  display: block;
  padding: 22rpx 24rpx 10rpx;
  font-size: 24rpx;
  color: #94a3b8;
  font-weight: 700;
}
.menu-item {
  display: flex;
  align-items: center;
  padding: 30rpx 24rpx;
  border-bottom: 1rpx solid #f1f5f9;
  font-size: 28rpx;
  color: #334155;
  .menu-icon { width: 40rpx; height: 40rpx; margin-right: 16rpx; flex-shrink: 0; }
  text { flex: 1; }
  .badge {
    min-width: 32rpx; height: 32rpx; line-height: 32rpx; padding: 0 8rpx;
    font-size: 20rpx; color: #fff; background: #ee0a24; border-radius: 32rpx;
    text-align: center; margin-right: 8rpx; flex-shrink: 0;
  }
  .arrow { color: #cbd5e1; flex: none; }
  &:last-child { border-bottom: none; }
}
.logout {
  margin: 40rpx 24rpx;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: #ffffff;
  color: #ee5555;
  font-size: 30rpx;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(238, 10, 36, 0.08);
  position: relative;
  z-index: 1;
}

</style>
