<template>
  <view class="product-detail" v-if="product">
    <!-- 轮播图 -->
    <view class="banner-wrap">
      <swiper class="banner" autoplay :interval="3000" circular :current="swiperIdx" @change="e => swiperIdx = e.detail.current">
        <swiper-item v-for="(img,i) in images" :key="i">
          <image :src="img" mode="aspectFill" class="banner-img" lazy-load @click="previewImg(i)" />
        </swiper-item>
      </swiper>
      <!-- 自定义指示器 -->
      <view class="banner-dots" v-if="images.length > 1">
        <view v-for="(img,i) in images" :key="i" class="dot" :class="{ active: swiperIdx === i }" />
      </view>
      <!-- 图片计数 -->
      <view class="img-counter">{{ swiperIdx + 1 }}/{{ images.length }}</view>
    </view>

    <!-- 价格区域 -->
    <view class="price-card">
      <view class="price-main">
        <text class="yen">¥</text>
        <text class="price-int">{{ priceInt }}</text>
        <text class="price-dec">.{{ priceDec }}</text>
      </view>
      <view class="price-extra">
        <!-- <text class="sales-badge">已售 {{ product.salesCount || 0 }}</text> -->
        <text
          v-if="product.perUserLimitEnabled === 1 && product.perUserLimitCount"
          class="limit-tag"
        >
          限购 {{ product.perUserLimitCount }} 次
        </text>
      </view>
    </view>

    <!-- 商品信息 -->
    <view class="info-card">
      <text class="product-name">{{ product.name }}</text>
      <text v-if="product.subtitle" class="product-subtitle">{{ product.subtitle }}</text>
      <view class="meta-row" v-if="product.categoryName">
        <view class="category-tag">
          <text class="tag-dot">●</text>
          <text class="tag-text">{{ product.categoryName }}</text>
        </view>
      </view>
      <view class="desc-wrap" v-if="product.description">
        <view class="desc-divider"></view>
        <text class="desc">{{ product.description }}</text>
      </view>
    </view>

    <!-- 商品详情 -->
    <view class="detail-section" v-if="product.detail">
      <view class="section-header">
        <view class="section-line"></view>
        <text class="section-title">商品详情</text>
        <view class="section-line"></view>
      </view>
      <view class="detail-body">
        <rich-text :nodes="product.detail" />
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="btn-chat" @click="goChat">
        <image class="btn-icon" src="/static/icons/客服.svg" mode="aspectFit" />
        <text>客服</text>
      </view>
      <view class="btn-buy" @click="goBuy">立即购买</view>
    </view>
  </view>
</template>
<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import PriceText from '@/components/PriceText.vue'
import { getProductRichDetail, getProductDetail } from '@/api/product'
import { createCsSession, sendChatMessage } from '@/api/chat'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const product = ref(null)
const swiperIdx = ref(0)

// 分享给好友
onShareAppMessage(() => {
  const p = product.value
  if (!p) return { title: '服务', path: '/pages/index/index' }
  return {
    title: p.name + ' ¥' + Number(p.price || 0).toFixed(2),
    path: '/pages/product/detail?id=' + p.id,
    imageUrl: p.coverImage || p.image || ''
  }
})
// 分享到朋友圈
onShareTimeline(() => {
  const p = product.value
  if (!p) return { title: '服务' }
  return {
    title: p.name + ' ¥' + Number(p.price || 0).toFixed(2),
    query: 'id=' + p.id,
    imageUrl: p.coverImage || p.image || ''
  }
})

const priceInt = computed(() => {
  const p = Number(product.value?.price || 0).toFixed(2)
  return p.split('.')[0]
})
const priceDec = computed(() => {
  const p = Number(product.value?.price || 0).toFixed(2)
  return p.split('.')[1]
})

const images = computed(() => {
  if (!product.value) return []
  const raw = product.value.images
  if (!raw) return [product.value.coverImage || product.value.image]
  try {
    const arr = JSON.parse(raw)
    if (Array.isArray(arr)) return arr
  } catch {}
  return raw.split(',').filter(Boolean)
})
function previewImg(idx) { uni.previewImage({ urls: images.value, current: idx }) }

onLoad(async (opts) => {
  try {
    const res = await getProductRichDetail(opts.id)
    const d = res.data
    product.value = d.product || d
    if (d.categoryName) product.value.categoryName = d.categoryName
  } catch (e) {
    const basic = await getProductDetail(opts.id)
    product.value = basic.data
  }
})

async function goChat() {
  if (!userStore.checkLogin()) return
  try {
    const res = await createCsSession()
    const session = res.data
    // 自动发送当前商品卡片给客服
    const p = product.value
    const content = JSON.stringify({ id: p.id, name: p.name, price: p.price, coverImage: p.coverImage || p.image })
    await sendChatMessage({ sessionId: session.id, type: 'PRODUCT', content })
    uni.navigateTo({ url: '/pages/chat/room?sessionId=' + session.id + '&name=' + encodeURIComponent('在线客服') })
  } catch (e) {
    uni.showToast({ title: e?.msg || '连接客服失败', icon: 'none' })
  }
}
function goBuy() {
  const price = product.value?.price || 0
  const name = encodeURIComponent(product.value.name)
  uni.navigateTo({ url: `/pages/order/create?productId=${product.value.id}&amount=${price}&productName=${name}` })
}
</script>
<style lang="scss" scoped>
.product-detail {
  background: #f8fafc;
  min-height: 100vh;
  padding-bottom: 140rpx;
}

/* ---- 轮播 ---- */
.banner-wrap {
  position: relative;
}
.banner {
  height: 750rpx;
}
.banner-img {
  width: 100%;
  height: 750rpx;
}
.banner-wrap::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60rpx;
  background: linear-gradient(to top, rgba(241,245,249,0.9), transparent);
  pointer-events: none;
  z-index: 1;
}
.banner-dots {
  position: absolute;
  bottom: 24rpx;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 12rpx;
  .dot {
    width: 12rpx;
    height: 12rpx;
    border-radius: 50%;
    background: #cbd5e1;
    transition: all 0.3s;
    &.active {
      width: 32rpx;
      border-radius: 6rpx;
      background: #4f46e5;
    }
  }
}
.img-counter {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.7);
  background: rgba(0, 0, 0, 0.4);
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
}

/* ---- 价格卡片 ---- */
.price-card {
  margin: -20rpx 24rpx 0;
  position: relative;
  z-index: 2;
  background: linear-gradient(135deg, rgba(255, 69, 68, 0.18), rgba(255, 69, 68, 0.08));
  border: 1rpx solid rgba(255, 69, 68, 0.22);
  border-radius: 20rpx;
  padding: 28rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.price-main {
  display: flex;
  align-items: baseline;
  .yen {
    font-size: 30rpx;
    color: #4f46e5;
    font-weight: bold;
    margin-right: 4rpx;
  }
  .price-int {
    font-size: 56rpx;
    color: #4f46e5;
    font-weight: 800;
    line-height: 1;
  }
  .price-dec {
    font-size: 30rpx;
    color: #4f46e5;
    font-weight: bold;
  }
}
.price-extra {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.sales-badge {
  font-size: 22rpx;
  color: #94a3b8;
  background: #f8fafc;
  padding: 6rpx 18rpx;
  border-radius: 20rpx;
}
.limit-tag {
  font-size: 22rpx;
  color: #b91c1c;
  background: rgba(248, 113, 113, 0.16);
  padding: 6rpx 18rpx;
  border-radius: 20rpx;
}

/* ---- 商品信息 ---- */
.info-card {
  margin: 20rpx 24rpx 0;
  padding: 28rpx 32rpx;
  background: #f8fafc;
  border: 1rpx solid #edf1f7;
  border-radius: 20rpx;
}
.product-name {
  font-size: 34rpx;
  font-weight: bold;
  display: block;
  color: #1e293b;
  line-height: 1.5;
}
.product-subtitle {
  font-size: 24rpx;
  color: #94a3b8;
  display: block;
  margin-top: 8rpx;
  line-height: 1.4;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 16rpx;
}
.category-tag {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 22rpx;
  color: #4f46e5;
  background: rgba(212, 175, 55, 0.1);
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
  border: 1rpx solid rgba(255, 69, 68, 0.15);
  .tag-dot {
    font-size: 14rpx;
    color: #4f46e5;
  }
}
.desc-wrap {
  margin-top: 20rpx;
}
.desc-divider {
  height: 1rpx;
  background: #f8fafc;
  margin-bottom: 20rpx;
}
.desc {
  font-size: 26rpx;
  color: #64748b;
  display: block;
  line-height: 1.7;
}

/* ---- 详情区域 ---- */
.detail-section {
  margin: 20rpx 24rpx 0;
  background: #f8fafc;
  border: 1rpx solid #edf1f7;
  border-radius: 20rpx;
  overflow: hidden;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20rpx;
  padding: 28rpx 32rpx 0;
}
.section-line {
  flex: 1;
  height: 1rpx;
  background: linear-gradient(to right, transparent, rgba(255, 69, 68, 0.3), transparent);
}
.section-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #4f46e5;
  white-space: nowrap;
}
.detail-body {
  padding: 24rpx 32rpx 32rpx;
}

/* ---- 底部栏 ---- */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1rpx solid #e2e8f0;
  gap: 20rpx;
  backdrop-filter: blur(20px);
}
.btn-chat {
  flex: 1;
  height: 84rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border: 1rpx solid rgba(255, 69, 68, 0.4);
  color: #4f46e5;
  border-radius: 999rpx;
  font-size: 26rpx;
  .btn-icon {
    width: 36rpx;
    height: 36rpx;
  }
}
.btn-buy {
  flex: 2;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
  font-weight: bold;
  border-radius: 999rpx;
  font-size: 30rpx;
  letter-spacing: 2rpx;
}
</style>
