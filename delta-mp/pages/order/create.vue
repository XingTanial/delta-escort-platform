<template>
  <view class="create-order">
    <view class="section">
      <view class="section-title">商品信息</view>
      <text class="product-name">{{ productName }}</text>
      <text v-if="specInfo" class="spec">{{ specInfo }}</text>
      <PriceText :value="amount" :size="36" />
    </view>
    <!-- 使用已保存信息 -->
    <view class="section saved-section" v-if="savedList.length || formFields.length">
      <view class="section-title">使用已保存信息</view>
      <view class="saved-tags">
        <view
          v-for="item in savedList" :key="item.id"
          class="saved-tag" :class="{ active: activeSavedId === item.id }"
          @click="applySaved(item)"
        >
          <text class="saved-label">{{ item.label || '未命名' }}</text>
        </view>
        <view class="saved-tag" :class="{ active: activeSavedId === 'manual' }" @click="applyManual">
          <text class="saved-label">手动填写</text>
        </view>
      </view>
    </view>
    <view class="section" v-if="formFields.length">
      <view class="section-title">订单信息</view>
      <view v-for="field in formFields" :key="field.id" class="form-item">
        <text class="label">{{ field.fieldLabel }}<text v-if="field.required" class="required">*</text></text>
        <!-- TEXT -->
        <input v-if="field.fieldType === 'TEXT'" v-model="extraFields[field.fieldLabel]" :placeholder="field.placeholder || ('请输入' + field.fieldLabel)" />
        <!-- TEXTAREA -->
        <textarea v-else-if="field.fieldType === 'TEXTAREA'" v-model="extraFields[field.fieldLabel]" :placeholder="field.placeholder || ('请输入' + field.fieldLabel)" :maxlength="500" />
        <!-- SELECT -->
        <picker v-else-if="field.fieldType === 'SELECT'" :range="getFieldOptions(field)" @change="extraFields[field.fieldLabel] = getFieldOptions(field)[$event.detail.value]">
          <view class="picker-value">
            <text :class="{ placeholder: !extraFields[field.fieldLabel] }">{{ extraFields[field.fieldLabel] || field.placeholder || ('请选择' + field.fieldLabel) }}</text>
            <text class="arrow">></text>
          </view>
        </picker>
      </view>
    </view>
    <view class="section">
      <view class="section-title">指定接单员（选填）</view>
      <view class="designate-toggle">
        <text>是否指定接单员</text>
        <switch :checked="wantDesignate" @change="wantDesignate = $event.detail.value" color="#6366f1" />
      </view>
      <view v-if="wantDesignate" class="player-select" @click="showPlayerPicker = true">
        <text v-if="selectedPlayer" class="selected-player">{{ selectedPlayer.nickname }}</text>
        <text v-else class="placeholder">点击选择接单员</text>
        <text class="arrow">></text>
      </view>
    </view>
    <view class="bottom-bar">
      <view class="total">合计：<PriceText :value="amount" :size="36" /></view>
      <view class="btn-submit" @click="submitOrder">提交订单</view>
    </view>
    <!-- 接单员选择弹窗（底部滑出） -->
    <view v-if="showPlayerPicker" class="modal-mask" @click="showPlayerPicker = false">
      <view class="player-picker" :class="{ 'slide-up': showPlayerPicker }" @click.stop>
        <view class="picker-handle"><view class="handle-bar" /></view>
        <view class="picker-header">
          <text class="picker-title">选择接单员</text>
          <text class="picker-close" @click="showPlayerPicker = false">✕</text>
        </view>
        <view class="limit-tip">
          接单员最多同时接 <text class="gold">{{ maxConcurrent }}</text> 个订单
        </view>
        <view class="picker-search">
          <input v-model="playerKeyword" placeholder="搜索接单员昵称/手机号" @confirm="searchPlayers" />
          <view class="search-btn" @click="searchPlayers">搜索</view>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view
            v-for="p in playerList" :key="p.id"
            class="picker-item"
            :class="{ selected: selectedPlayer && selectedPlayer.id === p.id, full: isFull(p), offline: !isOnline(p) }"
            @click="pickPlayer(p)"
          >
            <image :src="p.avatar || '/static/images/default-avatar.png'" class="picker-avatar" mode="aspectFill" />
            <view class="picker-info">
              <view class="picker-name-row">
                <text class="picker-name">{{ p.nickname || '-' }}</text>
                <text v-if="isOnline(p)" class="online-badge">在线</text>
                <text v-else class="offline-badge">离线</text>
                <text v-if="isFull(p)" class="full-badge">已满载</text>
              </view>
              <view class="picker-tags">
                <text v-if="p.avgRating" class="picker-tag">⭐{{ Number(p.avgRating).toFixed(1) }}</text>
                <text class="picker-tag done">完成 {{ p.completedOrders || 0 }}</text>
                <text class="picker-tag" :class="(p.activeOrders||0) > 0 ? 'active-tag' : ''">进行中 {{ p.activeOrders || 0 }}/{{ maxConcurrent }}</text>
              </view>
            </view>
            <text v-if="isOnline(p) && selectedPlayer && selectedPlayer.id === p.id" class="picker-check">✓</text>
          </view>
          <view v-if="playerList.length === 0" class="picker-empty">暂无可用接单员</view>
        </scroll-view>
        <view class="picker-footer">
          <view class="picker-btn cancel" @click="showPlayerPicker = false">取消</view>
          <view class="picker-btn confirm" @click="confirmPickPlayer">确定</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PriceText from '@/components/PriceText.vue'
import { createOrder, getAvailablePlayers } from '@/api/order'
import { getProductDetail, getCategoryFormFields } from '@/api/product'
import { getSavedInfoByCategory, saveDynamicInfo } from '@/api/user'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const productId = ref(0)
const specInfo = ref('')
const amount = ref(0)
const productName = ref('')

// 动态表单
const formFields = ref([])
const extraFields = reactive({})
const currentCategoryId = ref(null)

// 已保存的下单信息
const savedList = ref([])
const activeSavedId = ref(null)

function getFieldOptions(field) {
  if (!field.options) return []
  return field.options.split(',').map(s => s.trim()).filter(Boolean)
}

async function loadFormFields(pid) {
  try {
    const prodRes = await getProductDetail(pid)
    const categoryId = prodRes.data?.categoryId
    if (!categoryId) return
    currentCategoryId.value = categoryId
    const res = await getCategoryFormFields(categoryId)
    formFields.value = res.data || []
    // 加载已保存的下单信息
    loadSavedList(categoryId)
  } catch (e) {
    formFields.value = []
  }
}

async function loadSavedList(categoryId) {
  try {
    const res = await getSavedInfoByCategory(categoryId)
    savedList.value = res.data || []
  } catch (e) {
    savedList.value = []
  }
}

function applySaved(item) {
  activeSavedId.value = item.id
  let fields = {}
  if (item.savedFields) {
    try {
      fields = typeof item.savedFields === 'string' ? JSON.parse(item.savedFields) : item.savedFields
    } catch (e) { /* ignore */ }
  }
  Object.keys(extraFields).forEach(k => { extraFields[k] = '' })
  Object.entries(fields).forEach(([k, v]) => { extraFields[k] = v })
}

function applyManual() {
  activeSavedId.value = 'manual'
  Object.keys(extraFields).forEach(k => { extraFields[k] = '' })
}

const wantDesignate = ref(false)
const showPlayerPicker = ref(false)
const selectedPlayer = ref(null)
const playerKeyword = ref('')
const playerList = ref([])
const maxConcurrent = ref(5)

watch(wantDesignate, (val) => {
  if (val) searchPlayers()
})

function isFull(p) {
  return (p.activeOrders || 0) >= maxConcurrent.value
}
function isOnline(p) {
  return p.isOnline === 1
}

async function searchPlayers() {
  try {
    const res = await getAvailablePlayers({ pageNum: 1, pageSize: 50, keyword: playerKeyword.value })
    const data = res.data || {}
    const page = data.players || {}
    playerList.value = page.records || []
    if (data.maxConcurrent != null) maxConcurrent.value = data.maxConcurrent
  } catch (e) {
    playerList.value = []
  }
}

function pickPlayer(p) {
  if (!isOnline(p)) {
    uni.showModal({ title: '无法选择', content: `${p.nickname} 当前处于离线状态，无法指定`, showCancel: false })
    return
  }
  if (isFull(p)) {
    uni.showModal({
      title: '无法选择',
      content: `该接单员当前已有 ${p.activeOrders || 0} 个进行中订单，已达最大接单数 ${maxConcurrent.value}`,
      showCancel: false
    })
    return
  }
  selectedPlayer.value = p
}

function confirmPickPlayer() {
  showPlayerPicker.value = false
}

onLoad((opts) => {
  productId.value = opts.productId
  specInfo.value = decodeURIComponent(opts.specCombination || opts.specInfo || '')
  amount.value = opts.amount || 0
  productName.value = decodeURIComponent(opts.productName || '服务')
  loadFormFields(opts.productId)
})

async function submitOrder() {
  if (!userStore.checkLogin()) return
  // 校验必填动态字段
  for (const field of formFields.value) {
    if (field.required && !extraFields[field.fieldLabel]?.trim()) {
      return uni.showToast({ title: `请填写${field.fieldLabel}`, icon: 'none' })
    }
  }
  try {
    const fieldsToSubmit = { ...extraFields }
    const res = await createOrder({
      productId: productId.value,
      specInfo: specInfo.value,
      amount: amount.value,
      extraFields: fieldsToSubmit,
      designatedPlayerId: selectedPlayer.value?.id
    })
    // 自动保存本次填写的信息（最多3条，超出覆盖最旧）
    if (currentCategoryId.value && formFields.value.length) {
      const vals = Object.values(fieldsToSubmit).filter(v => v && v.trim())
      if (vals.length) {
        const label = vals.slice(0, 2).join(' / ').substring(0, 30)
        try {
          await saveDynamicInfo({
            categoryId: currentCategoryId.value,
            savedFields: fieldsToSubmit,
            label
          })
        } catch (e) {
          console.error('保存下单信息失败', e)
        }
      }
    }
    uni.redirectTo({ url: `/pages/order/pay?orderId=${res.data.id}&amount=${res.data.amount}` })
  } catch (e) {
    console.error('create order failed', e)
    const msg = e?.msg || e?.message || '下单失败，请稍后重试'
    uni.showToast({ title: msg, icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.create-order { background: #f8fafc; min-height: 100vh; padding-bottom: 120rpx; }
.section { background: #ffffff; padding: 24rpx; margin: 20rpx 24rpx; border-radius: 16rpx; box-shadow: 0 4rpx 20rpx rgba(99, 102, 241, 0.08); }
.section-title { font-size: 30rpx; font-weight: bold; margin-bottom: 20rpx; color: #4f46e5; }
.product-name { font-size: 28rpx; font-weight: bold; color: #1e293b; display: block; margin-bottom: 8rpx; }
.spec { font-size: 24rpx; color: #64748b; display: block; margin-bottom: 12rpx; }
.required { color: #4f46e5; margin-left: 4rpx; font-size: 24rpx; }
.form-item { margin-bottom: 24rpx; .label { font-size: 26rpx; color: #64748b; margin-bottom: 12rpx; display: block; }
  input { height: 72rpx; background: #f8fafc; border: 1rpx solid #e2e8f0; border-radius: 12rpx; padding: 0 24rpx; font-size: 28rpx; color: #1e293b; }
  textarea { width: 100%; height: 160rpx; background: #f8fafc; border: 1rpx solid #e2e8f0; border-radius: 12rpx; padding: 20rpx 24rpx; font-size: 28rpx; color: #1e293b; box-sizing: border-box; }
}
.picker-value { display: flex; align-items: center; justify-content: space-between; height: 72rpx; background: #f8fafc; border: 1rpx solid #e2e8f0; border-radius: 12rpx; padding: 0 24rpx; font-size: 28rpx; color: #1e293b;
  .placeholder { color: #94a3b8; }
  .arrow { color: #94a3b8; font-size: 24rpx; }
}
.designate-toggle { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; font-size: 28rpx; color: #1e293b; }
.player-select { display: flex; align-items: center; justify-content: space-between; padding: 24rpx; background: #f8fafc; border: 1rpx solid #e2e8f0; border-radius: 12rpx; font-size: 28rpx; color: #1e293b; }
.player-select .placeholder { color: #94a3b8; }
.player-select .arrow { color: #94a3b8; font-size: 24rpx; }
.selected-player { font-weight: 500; color: #4f46e5; }

.modal-mask {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.6); z-index: 999;
  display: flex; align-items: flex-end; justify-content: center;
}

.player-picker {
  width: 100%; max-height: 80vh; background: #ffffff;
  border-radius: 24rpx 24rpx 0 0; padding: 0 24rpx 24rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.picker-handle {
  display: flex; justify-content: center; padding: 16rpx 0;
  .handle-bar { width: 64rpx; height: 8rpx; background: #e2e8f0; border-radius: 4rpx; }
}

.picker-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16rpx;
}
.picker-title { font-size: 32rpx; font-weight: bold; color: #4f46e5; }
.picker-close { font-size: 36rpx; color: #94a3b8; padding: 0 8rpx; }

.limit-tip {
  padding: 12rpx 24rpx; font-size: 24rpx; color: rgba(0,0,0,0.45);
  background: rgba(0,0,0,0.04); text-align: center;
  border-radius: 8rpx; margin-bottom: 16rpx;
  .gold { color: #4f46e5; font-weight: bold; }
}

.picker-search {
  display: flex; gap: 16rpx; margin-bottom: 16rpx;
  input {
    flex: 1; height: 72rpx; background: #f8fafc;
    padding: 0 24rpx; border-radius: 999rpx;
    font-size: 26rpx; color: #1e293b; box-sizing: border-box;
  }
  .search-btn {
    padding: 0 32rpx; height: 72rpx; line-height: 72rpx;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    color: #ffffff; font-weight: bold; border-radius: 999rpx;
    font-size: 26rpx; flex-shrink: 0;
  }
}

.picker-list { max-height: 480rpx; }

.picker-item {
  display: flex; align-items: center; padding: 20rpx;
  background: rgba(0,0,0,0.02); border-radius: 12rpx; margin-bottom: 12rpx;
  &.selected { background: rgba(99, 102, 241, 0.08); }
  &.full { opacity: 0.5; }
  &.offline { opacity: 0.5; }
}
.picker-avatar { width: 80rpx; height: 80rpx; border-radius: 50%; flex-shrink: 0; margin-right: 20rpx; }
.picker-info { flex: 1; overflow: hidden; }
.picker-name-row { display: flex; align-items: center; gap: 8rpx; }
.picker-name { font-size: 28rpx; color: #1e293b; font-weight: 500; }
.online-badge {
  font-size: 20rpx; color: #22c55e; background: rgba(34,197,94,0.15);
  padding: 2rpx 10rpx; border-radius: 4rpx;
}
.offline-badge {
  font-size: 20rpx; color: #94a3b8; background: rgba(148,163,184,0.15);
  padding: 2rpx 10rpx; border-radius: 4rpx;
}
.full-badge {
  font-size: 20rpx; color: #ee6723; background: rgba(238,103,35,0.15);
  padding: 2rpx 10rpx; border-radius: 4rpx;
}
.picker-tags { display: flex; gap: 8rpx; margin-top: 6rpx; flex-wrap: wrap; }
.picker-tag {
  font-size: 22rpx; color: rgba(0,0,0,0.55);
  background: rgba(0,0,0,0.05); padding: 4rpx 12rpx; border-radius: 4rpx;
  &.done { color: #07c160; }
  &.active-tag { color: #ee6723; }
}
.picker-check { font-size: 28rpx; color: #4f46e5; font-weight: bold; margin-left: 12rpx; }
.picker-empty { text-align: center; padding: 40rpx; font-size: 26rpx; color: #94a3b8; }

.picker-footer {
  display: flex; gap: 20rpx; margin-top: 20rpx;
  padding-top: 16rpx; border-top: 1rpx solid #e2e8f0;
}
.picker-btn {
  flex: 1; height: 80rpx; line-height: 80rpx;
  text-align: center; font-size: 28rpx; border-radius: 999rpx;
  &.cancel { border: 1rpx solid #e2e8f0; color: #64748b; }
  &.confirm { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #ffffff; font-weight: bold; }
}

/* 已保存的下单信息标签 */
.saved-section { padding: 24rpx 24rpx 20rpx !important; }
.saved-tags { display: flex; gap: 16rpx; flex-wrap: wrap; }
.saved-tag {
  display: flex; align-items: center; justify-content: center;
  padding: 16rpx 28rpx; background: #ffffff;
  border: 2rpx solid #e2e8f0; border-radius: 8rpx;
  font-size: 26rpx; color: #1e293b;
  &.active { border-color: #4f46e5; color: #4f46e5; }
}
.saved-label { max-width: 280rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.bottom-bar { position: fixed; bottom: 0; left: 0; right: 0; display: flex; height: 100rpx; background: rgba(255,255,255,0.95); border-top: 1rpx solid #e2e8f0; align-items: center; padding: 0 24rpx; padding-bottom: env(safe-area-inset-bottom);
  .total { flex: 1; font-size: 28rpx; color: #1e293b; }
  .btn-submit { width: 240rpx; height: 76rpx; line-height: 76rpx; text-align: center; background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #ffffff; font-weight: bold; font-size: 30rpx; border-radius: 999rpx; }
}
</style>
