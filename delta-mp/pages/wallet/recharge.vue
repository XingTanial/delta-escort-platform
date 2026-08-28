<template>
  <view class="recharge-page">
    <view class="hero">
      <text class="hero-title">余额充值</text>
      <text class="hero-desc">充值后可直接使用余额支付服务</text>
    </view>
    <view class="section-title">选择充值套餐</view>
    <view class="package-grid">
      <view v-for="item in packages" :key="item.id" class="package-card" :class="{ selected: selected?.id === item.id }" @click="selected = item">
        <text class="package-name">{{ item.name || '余额充值套餐' }}</text>
        <view class="pay-amount"><text class="unit">¥</text>{{ money(item.amount) }}</view>
        <text class="bonus">赠送 ¥{{ money(item.bonus) }}</text>
        <text class="arrival">到账 ¥{{ money(Number(item.amount) + Number(item.bonus)) }}</text>
      </view>
    </view>
    <EmptyState v-if="!loading && packages.length === 0" text="暂无可用充值套餐" image="/static/icons/暂无纪录.svg" />
    <view class="bottom-area">
      <text class="selected-info" v-if="selected">实付 ¥{{ money(selected.amount) }}，到账 ¥{{ money(Number(selected.amount) + Number(selected.bonus)) }}</text>
      <view class="pay-button" :class="{ disabled: !selected || paying }" @click="pay">{{ paying ? '支付中...' : '微信支付充值' }}</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getRechargePackages } from '@/api/config'
import { createH5RechargePayment, createRechargePayment } from '@/api/pay'
import { isH5 } from '@/utils/platform'
import EmptyState from '@/components/EmptyState.vue'

const packages = ref([])
const selected = ref(null)
const loading = ref(false)
const paying = ref(false)
function money(value) { return Number(value || 0).toFixed(2) }

onLoad(async () => {
  loading.value = true
  try {
    const res = await getRechargePackages()
    packages.value = res.data || []
    selected.value = packages.value[0] || null
  } finally { loading.value = false }
})

async function pay() {
  if (!selected.value || paying.value) return
  paying.value = true
  try {
    if (isH5()) {
      const res = await createH5RechargePayment(selected.value.id)
      if (!res.data?.h5Url) throw new Error('未获取到微信支付地址')
      window.location.href = res.data.h5Url
      return
    }
    const res = await createRechargePayment(selected.value.id)
    const params = res.data || {}
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: params.timeStamp,
      nonceStr: params.nonceStr,
      package: params.package,
      signType: params.signType || 'RSA',
      paySign: params.paySign,
      success() {
        uni.showToast({ title: '充值成功' })
        setTimeout(() => uni.navigateBack(), 1200)
      },
      fail() { uni.showToast({ title: '支付取消', icon: 'none' }) }
    })
  } catch (e) {
    uni.showToast({ title: e?.data?.msg || e?.message || '充值失败', icon: 'none' })
  } finally { paying.value = false }
}
</script>

<style lang="scss" scoped>
.recharge-page { min-height: 100vh; padding: 24rpx 24rpx 220rpx; background: #f8fafc; box-sizing: border-box; }
.hero { padding: 34rpx 30rpx; border-radius: 24rpx; color: #fff; background: linear-gradient(135deg, #6366f1, #8b5cf6); box-shadow: 0 12rpx 28rpx rgba(79, 70, 229, .18); }
.hero-title { display: block; font-size: 40rpx; font-weight: 800; }
.hero-desc { display: block; margin-top: 12rpx; font-size: 24rpx; opacity: .86; }
.section-title { padding: 30rpx 4rpx 18rpx; color: #334155; font-size: 30rpx; font-weight: 800; }
.package-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 18rpx; }
.package-card { padding: 28rpx 22rpx; min-height: 210rpx; border: 2rpx solid transparent; border-radius: 22rpx; background: #fff; box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, .06); box-sizing: border-box; }
.package-card.selected { border-color: #6366f1; background: #f5f3ff; }
.package-name, .bonus, .arrival { display: block; }
.package-name { color: #475569; font-size: 24rpx; font-weight: 700; }
.pay-amount { margin: 18rpx 0 8rpx; color: #4f46e5; font-size: 48rpx; font-weight: 800; }
.unit { margin-right: 4rpx; font-size: 26rpx; }
.bonus { color: #f97316; font-size: 24rpx; font-weight: 700; }
.arrival { margin-top: 8rpx; color: #94a3b8; font-size: 22rpx; }
.bottom-area { position: fixed; left: 0; right: 0; bottom: 0; padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom)); background: rgba(255,255,255,.96); box-shadow: 0 -8rpx 24rpx rgba(15, 23, 42, .06); }
.selected-info { display: block; margin-bottom: 12rpx; color: #64748b; text-align: center; font-size: 23rpx; }
.pay-button { height: 88rpx; line-height: 88rpx; border-radius: 999rpx; color: #fff; background: linear-gradient(135deg, #6366f1, #8b5cf6); text-align: center; font-size: 30rpx; font-weight: 800; }
.pay-button.disabled { color: #94a3b8; background: #e2e8f0; }
</style>
