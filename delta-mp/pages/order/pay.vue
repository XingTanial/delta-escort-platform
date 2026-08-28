<template>
  <view class="pay-page">
    <view class="amount-area">
      <text class="label">支付金额</text>
      <PriceText :value="amount" :size="60" />
      <text v-if="countdown > 0" class="countdown">支付剩余时间 {{ formatCountdown }}</text>
      <text v-else-if="expired" class="countdown expired">订单已超时</text>
    </view>
    <view class="pay-methods">
      <view v-if="canUseWechatPay" class="method" :class="{ active: payType === 'WECHAT' }" @click="payType='WECHAT'">
        <image class="method-icon" src="/static/icons/钞票.svg" mode="aspectFit" />
        <text class="method-name">微信支付</text>
        <view class="radio" :class="{ checked: payType === 'WECHAT' }" />
      </view>
      <view class="method" :class="{ active: payType === 'BALANCE' }" @click="payType='BALANCE'">
        <image class="method-icon" src="/static/icons/理财.svg" mode="aspectFit" />
        <view class="method-info">
          <text class="method-name">余额支付</text>
          <text class="balance-text">余额：¥{{ walletBalance }}</text>
        </view>
        <view class="radio" :class="{ checked: payType === 'BALANCE' }" />
      </view>
    </view>
    <view class="btn-pay" :class="{ disabled: expired }" @click="handlePay">确认支付</view>
  </view>
</template>
<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PriceText from '@/components/PriceText.vue'
import { createPayment, createH5Payment, balancePay } from '@/api/pay'
import { getSiteConfig } from '@/api/config'
import { getOrderDetail } from '@/api/order'
import { getWallet } from '@/api/user'
import { requestOrderSubscribe } from '@/utils/subscribe'
import { isMpWeixin, isH5 } from '@/utils/platform'

const orderId = ref(0)
const amount = ref(0)
const canUseWechatPay = ref(false)
const payType = ref('BALANCE')
const walletBalance = ref('0.00')
const countdown = ref(0)
const expired = ref(false)
let timer = null

const formatCountdown = computed(() => {
  const m = Math.floor(countdown.value / 60)
  const s = countdown.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

onLoad(async (opts) => {
  orderId.value = opts.orderId
  amount.value = opts.amount

  try {
    const siteRes = await getSiteConfig()
    const enabled = siteRes.data?.wx_pay_enabled === 'true'
    canUseWechatPay.value = enabled && (isMpWeixin() || isH5())
    if (canUseWechatPay.value) payType.value = 'WECHAT'
  } catch (e) { /* 微信支付未配置时默认使用余额支付 */ }

  // 从后端获取订单信息，计算真实倒计时
  try {
    const orderRes = await getOrderDetail(orderId.value)
    const order = orderRes.data
    if (order) {
      amount.value = order.amount || amount.value
      // 如果订单已不是待支付状态，直接跳转
      if (order.status !== 'PENDING_PAYMENT') {
        handleOrderNotPending(order.status)
        return
      }
      // 用payDeadline计算剩余秒数
      if (order.payDeadline) {
        const deadlineMs = new Date(order.payDeadline.replace(' ', 'T')).getTime()
        const remainSec = Math.floor((deadlineMs - Date.now()) / 1000)
        countdown.value = remainSec > 0 ? remainSec : 0
      } else {
        countdown.value = 1800
      }
    }
  } catch (e) {
    // fallback: 使用URL参数
    countdown.value = 1800
  }

  if (countdown.value <= 0) {
    expired.value = true
  } else {
    startCountdown()
  }

  // 加载钱包余额
  try {
    const w = await getWallet()
    walletBalance.value = Number(w.data?.balance || 0).toFixed(2)
  } catch (e) { /* ignore */ }
})

function startCountdown() {
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      expired.value = true
    }
  }, 1000)
}

function handleOrderNotPending(status) {
  const msg = status === 'PAID' ? '该订单已支付' : status === 'CANCELLED' ? '订单已取消' : '订单状态已变更'
  uni.showModal({
    title: '提示',
    content: msg,
    showCancel: false,
    success: () => uni.redirectTo({ url: `/pages/order/detail?id=${orderId.value}` })
  })
}

function handleAlreadyPaid() {
  uni.showModal({
    title: '提示',
    content: '该订单已支付，请勿重复支付',
    showCancel: false,
    success: () => uni.redirectTo({ url: `/pages/order/detail?id=${orderId.value}` })
  })
}

onUnmounted(() => { if (timer) clearInterval(timer) })

async function handlePay() {
  if (expired.value) return uni.showToast({ title: '订单已超时', icon: 'none' })
  // 支付前请求订阅授权（用户拒绝不影响支付流程）
  await requestOrderSubscribe()
  try {
    if (payType.value === 'BALANCE') {
      await balancePay(orderId.value)
      uni.showToast({ title: '支付成功' })
      setTimeout(() => uni.redirectTo({ url: `/pages/order/detail?id=${orderId.value}` }), 1500)
    } else {
      if (!canUseWechatPay.value) {
        return uni.showToast({ title: '微信支付未开启，请使用余额支付', icon: 'none' })
      }
      if (isH5()) {
        const res = await createH5Payment(orderId.value)
        const h5Url = res.data?.h5Url
        if (!h5Url) return uni.showToast({ title: '未获取到微信支付地址', icon: 'none' })
        window.location.href = h5Url
        return
      }
      const res = await createPayment(orderId.value)
      const payParams = res.data
      uni.requestPayment({
        provider: 'wxpay',
        timeStamp: payParams.timeStamp,
        nonceStr: payParams.nonceStr,
        package: payParams.package,
        signType: payParams.signType || 'RSA',
        paySign: payParams.paySign,
        success() {
          uni.showToast({ title: '支付成功' })
          setTimeout(() => uni.redirectTo({ url: `/pages/order/detail?id=${orderId.value}` }), 1500)
        },
        fail() {
          uni.showToast({ title: '支付取消', icon: 'none' })
        }
      })
    }
  } catch (e) {
    if (e && e.code === 4010) {
      handleAlreadyPaid()
    }
  }
}
</script>
<style lang="scss" scoped>
.pay-page { padding: 40rpx 24rpx; background: #f8fafc; min-height: 100vh; }
.amount-area { text-align: center; padding: 60rpx 0;
  .label { font-size: 28rpx; color: #64748b; display: block; margin-bottom: 20rpx; }
  .countdown { font-size: 26rpx; color: #ff9900; display: block; margin-top: 16rpx; &.expired { color: #ee0a24; } }
}
.pay-methods { background: #ffffff; border: 1rpx solid #e2e8f0; border-radius: 12rpx; overflow: hidden; margin-bottom: 60rpx; }
.method { display: flex; align-items: center; padding: 32rpx 24rpx; border-bottom: 1rpx solid #edf1f7; gap: 16rpx;
  .method-icon { width: 40rpx; height: 40rpx; flex-shrink: 0; }
  .method-name { font-size: 28rpx; color: #1e293b; flex: 1; }
  .method-info { flex: 1; .balance-text { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 4rpx; } }
  .radio { width: 36rpx; height: 36rpx; border: 2rpx solid #cbd5e1; border-radius: 50%; &.checked { border-color: #4f46e5; background: #4f46e5; } }
}
.btn-pay { height: 88rpx; line-height: 88rpx; text-align: center; background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #ffffff; font-weight: bold; font-size: 32rpx; border-radius: 999rpx; &.disabled { background: #e2e8f0; color: #94a3b8; } }
</style>
