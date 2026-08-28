<template>
  <view class="home-page tab-page">
    <scroll-view scroll-y class="home-scroll tab-page-scroll" :show-scrollbar="false">
      <!-- 搜索栏 -->
      <view class="search-bar" @click="goSearch">
        <view class="search-inner">
          <text class="search-placeholder">搜索</text>
        </view>
      </view>
      <view class="search-bar-placeholder" />

      <!-- 轮播图 -->
      <swiper class="banner" :autoplay="true" :interval="4000" circular indicator-dots>
        <swiper-item v-for="item in banners" :key="item.id" @click="onBannerClick(item)">
          <image :src="item.imageUrl" mode="aspectFill" class="banner-img" />
        </swiper-item>
      </swiper>

      <!-- 公告栏 - 竖向滚动 -->
      <view v-if="notices.length" class="notice-bar" @click="showNoticeList = true">
        <view class="notice-label">
          <image class="notice-label-icon" src="/static/icons/公告.svg" mode="aspectFit" />
          <text class="notice-label-text">公告</text>
        </view>
        <swiper class="notice-swiper" vertical autoplay :interval="3500" :duration="500" circular :disable-touch="true">
          <swiper-item v-for="item in notices" :key="item.id">
            <view class="notice-swiper-item">
              <text class="notice-title">{{ item.title }}</text>
            </view>
          </swiper-item>
        </swiper>
        <text class="notice-more">{{ notices.length }}条 ></text>
      </view>

      <!-- 公告列表弹窗 -->
      <view class="notice-list-mask" v-if="showNoticeList" @click="showNoticeList = false">
        <view class="notice-list-popup" @click.stop>
          <view class="notice-list-header">
            <text class="notice-list-title">平台公告</text>
            <text class="notice-list-close" @click="showNoticeList = false">✕</text>
          </view>
          <scroll-view scroll-y class="notice-list-body">
            <view
              v-for="item in notices"
              :key="item.id"
              class="notice-list-item"
              @click="openNoticeDetail(item); showNoticeList = false"
            >
              <view class="notice-list-dot"></view>
              <view class="notice-list-info">
                <text class="notice-list-name">{{ item.title }}</text>
                <text class="notice-list-time">{{ formatTime(item.createTime) }}</text>
              </view>
              <text class="notice-list-arrow">></text>
            </view>
          </scroll-view>
        </view>
      </view>

      <!-- 商品分类：图标 + 文字 -->
      <view class="category-grid">
        <view
          v-for="cat in categories"
          :key="cat.id"
          class="category-item"
          @click="goCategory(cat.id)"
        >
          <image class="category-icon" :src="cat.icon || '/static/icons/分类.svg'" mode="aspectFill" lazy-load />
          <text class="category-name">{{ cat.name }}</text>
        </view>
      </view>

      <!-- 带分类的热门推荐：默认全部，可切换护航专区/热门等 -->
      <view class="section">
        <view class="section-header">
          <scroll-view scroll-x class="recommend-tabs" :show-scrollbar="false" scroll-with-animation>
            <view
              v-for="tab in recommendTabs"
              :key="tab.id"
              class="recommend-tab"
              :class="{ active: currentRecommendCategoryId === tab.id }"
              @click="switchRecommendCategory(tab.id)"
            >
              {{ tab.name }}
            </view>
          </scroll-view>
        </view>
        <view class="product-grid">
          <ProductCard v-for="p in recommendProducts" :key="p.id" :product="p" />
        </view>
      </view>

      <view class="tab-page-bottom-spacer" />
    </scroll-view>

    <CustomTabBar :current="0" />

    <!-- 悬浮客服按钮 -->
    <view class="cs-float-btn" @click="goCustomerService">
      <image src="/static/icons/客服.svg" mode="aspectFit" class="cs-icon" />
    </view>

    <!-- 公告弹窗 - 支持多公告叠加 -->
    <view class="notice-popup" v-if="showNoticePopup" @click="closeNoticePopup">
      <view class="popup-content" @click.stop>
        <view class="popup-header">
          <text class="popup-title">公告（{{ popupCurrentIndex + 1 }}/{{ popupNotices.length }}）</text>
          <text class="popup-close" @click="closeNoticePopup">✕</text>
        </view>
        <scroll-view scroll-y class="popup-body">
          <text class="popup-notice-title">{{ popupNotices[popupCurrentIndex]?.title }}</text>
          <rich-text v-if="popupNotices[popupCurrentIndex]?.content" :nodes="popupNotices[popupCurrentIndex].content" />
        </scroll-view>
        <view class="popup-footer">
          <view class="popup-nav" v-if="popupNotices.length > 1">
            <view
              class="popup-nav-btn"
              :class="{ disabled: popupCurrentIndex === 0 }"
              @click="popupCurrentIndex > 0 && popupCurrentIndex--"
            >上一条</view>
            <view
              class="popup-nav-btn"
              :class="{ disabled: popupCurrentIndex === popupNotices.length - 1 }"
              @click="popupCurrentIndex < popupNotices.length - 1 && popupCurrentIndex++"
            >下一条</view>
          </view>
          <view class="popup-btn" @click="closeNoticePopup">我知道了</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh, onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import ProductCard from '@/components/ProductCard.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { getRecommendProducts, getRecommendCategories } from '@/api/product'
import { getActiveBanners } from '@/api/banner'
import { getActiveNotices } from '@/api/notice'
import { getCategoryTree } from '@/api/category'
import { createCsSession } from '@/api/chat'
import { getRemind } from '@/api/message'
import { useUserStore } from '@/store/user'
import { useChatStore } from '@/store/chat'
import { useSiteStore } from '@/store/site'
const userStore = useUserStore()
const siteStore = useSiteStore()

// 分享给好友
onShareAppMessage(() => ({
  title: siteStore.siteName + ' - ' + siteStore.subtitle,
  path: '/pages/index/index'
}))
// 分享到朋友圈
onShareTimeline(() => ({
  title: siteStore.siteName + ' - ' + siteStore.subtitle
}))
const banners = ref([])
const categories = ref([])
const notices = ref([])
const recommendProducts = ref([])
/** 热门推荐 Tab：全部 + 接口返回的分类（护航专区推荐、热门推荐等） */
const recommendTabs = ref([])
const currentRecommendCategoryId = ref('')
const showNoticePopup = ref(false)
const popupNotices = ref([])
const popupCurrentIndex = ref(0)
const showNoticeList = ref(false)

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth() + 1}-${d.getDate()}`
}

async function loadData() {
  const _t = Date.now()
  try {
    const opts = { loading: false }
    const [bannerRes, noticeRes, catRes, categoriesRes] = await Promise.all([
      getActiveBanners(opts),
      getActiveNotices(opts),
      getCategoryTree(opts),
      getRecommendCategories(opts),
    ])

    // 轮播图
    if (bannerRes.data) {
      banners.value = bannerRes.data.records || bannerRes.data || []
    }
    // 公告
    if (noticeRes.data) {
      const list = noticeRes.data.records || noticeRes.data || []
      notices.value = list
      // 筛选出需要弹窗展示的公告
      const popupList = list.filter(n => n.popupDisplay === 1)
      if (popupList.length > 0) {
        const seenIds = uni.getStorageSync('seenNoticeIds') || []
        const unseenList = popupList.filter(n => !seenIds.includes(String(n.id)))
        if (unseenList.length > 0) {
          popupNotices.value = unseenList
          popupCurrentIndex.value = 0
          showNoticePopup.value = true
        }
      }
    }
    // 分类（树接口返回父级）
    if (catRes.data) {
      categories.value = (catRes.data || []).slice(0, 8)
    }
    // 热门推荐分类 Tab（不含"全部热门"，默认选中第一个）
    if (categoriesRes.data && Array.isArray(categoriesRes.data)) {
      recommendTabs.value = categoriesRes.data.map(c => ({ id: String(c.id), name: c.name }))
      if (recommendTabs.value.length > 0 && !currentRecommendCategoryId.value) {
        currentRecommendCategoryId.value = recommendTabs.value[0].id
      }
    }
    // 推荐商品
    const productParams = currentRecommendCategoryId.value ? { categoryId: currentRecommendCategoryId.value } : {}
    const productRes = await getRecommendProducts(productParams, opts)
    if (productRes.data) {
      recommendProducts.value = productRes.data.records || productRes.data || []
    }
  } catch (e) {
    console.error('load home data error', e)
  }
  console.log(`[HOME] loadData done ${Date.now() - _t}ms`)
}

onShow(() => {
  loadData()
  if (useUserStore().token) {
    getRemind({ loading: false }).then(res => {
      const d = res.data || {}
      useChatStore().setUnreadFromServer(d.messageUnread ?? 0)
      useChatStore().setSystemUnreadFromServer(d.systemUnread ?? 0)
    }).catch(() => {})
  }
})

onPullDownRefresh(async () => {
  await loadData()
  uni.stopPullDownRefresh()
})

function switchRecommendCategory(id) {
  if (currentRecommendCategoryId.value === id) return
  currentRecommendCategoryId.value = id
  const params = id ? { categoryId: id } : {}
  getRecommendProducts(params, { loading: false }).then(res => {
    recommendProducts.value = res.data?.records || res.data || []
  }).catch(() => {})
}

function goSearch() {
  uni.navigateTo({ url: '/pages/product/list?focus=1' })
}

function onBannerClick(item) {
  if (item.linkType === 'product' && item.linkValue) {
    uni.navigateTo({ url: `/pages/product/detail?id=${item.linkValue}` })
  } else if (item.linkType === 'url' && item.linkValue) {
    // 外部链接可通过webview打开
  }
}

function goCategory(id) {
  if (id) uni.setStorageSync('selectedCategoryId', id)
  uni.switchTab({ url: '/pages/category/index' })
}

function openNoticeDetail(item) {
  popupNotices.value = [item]
  popupCurrentIndex.value = 0
  showNoticePopup.value = true
}

function closeNoticePopup() {
  showNoticePopup.value = false
  // 记录已看过的弹窗公告ID
  const seenIds = uni.getStorageSync('seenNoticeIds') || []
  popupNotices.value.forEach(n => {
    if (n.id && !seenIds.includes(String(n.id))) {
      seenIds.push(String(n.id))
    }
  })
  uni.setStorageSync('seenNoticeIds', seenIds)
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
</script>

<style lang="scss" scoped>
.home-page {
  background: #f8fafc;
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

/* === 内容层置于背景之上 === */
.search-bar, .banner, .notice-bar, .category-tags, .section, .notice-popup {
  position: relative;
  z-index: 1;
}

.search-bar {
  position: relative;
  z-index: 2;
  padding: 24rpx 24rpx 18rpx;
  background: transparent;

  .search-inner {
    height: 80rpx;
    background: #ffffff;
    border: 1rpx solid #e2e8f0;
    border-radius: 999rpx;
    display: flex;
    align-items: center;
    padding: 0 34rpx;
    box-shadow: 0 8rpx 28rpx rgba(15, 23, 42, 0.12);
  }
  .search-placeholder { font-size: 28rpx; color: #64748b; }
}
.search-bar-placeholder { display: none; }

.banner {
  height: 395rpx;
  margin: 0 24rpx;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.1);

  .banner-img { width: 100%; height: 100%; }
}

.notice-bar {
  display: flex;
  align-items: center;
  margin: 20rpx 24rpx;
  padding: 0 24rpx;
  height: 72rpx;
  background: #ffffff;
  border-radius: 12rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 20rpx rgba(255, 69, 68, 0.08);
}
.notice-label {
  display: flex;
  align-items: center;
  gap: 6rpx;
  flex-shrink: 0;
  margin-right: 16rpx;
  padding-right: 16rpx;
  border-right: 1rpx solid #e2e8f0;
  .notice-label-icon {
    width: 32rpx;
    height: 32rpx;
    filter: brightness(0) saturate(100%) invert(27%) sepia(51%) saturate(2878%) hue-rotate(346deg) brightness(104%) contrast(101%);
  }
  .notice-label-text { font-size: 24rpx; color: #4f46e5; font-weight: 600; }
}
.notice-swiper {
  flex: 1;
  height: 72rpx;
}
.notice-swiper-item {
  display: flex;
  align-items: center;
  height: 72rpx;
}
.notice-title {
  font-size: 24rpx;
  color: #4f46e5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.notice-more {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #94a3b8;
  margin-left: 12rpx;
}
/* 公告列表弹窗 */
.notice-list-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 999;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.notice-list-popup {
  width: 100%;
  max-height: 70vh;
  background: #ffffff;
  border-radius: 32rpx 32rpx 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.notice-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 36rpx 24rpx;
  border-bottom: 1rpx solid #e2e8f0;
  .notice-list-title { font-size: 32rpx; font-weight: bold; color: #4f46e5; }
  .notice-list-close { font-size: 36rpx; color: #94a3b8; padding: 0 8rpx; }
}
.notice-list-body {
  height: 50vh;
  padding: 12rpx 0;
}
.notice-list-item {
  display: flex;
  align-items: center;
  padding: 28rpx 36rpx;
  border-bottom: 1rpx solid #edf1f7;
}
.notice-list-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #4f46e5;
  flex-shrink: 0;
  margin-right: 20rpx;
}
.notice-list-info {
  flex: 1;
  overflow: hidden;
}
.notice-list-name {
  font-size: 28rpx;
  color: #1e293b;
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.notice-list-time {
  font-size: 22rpx;
  color: #94a3b8;
  display: block;
  margin-top: 6rpx;
}
.notice-list-arrow {
  color: #cbd5e1;
  font-size: 26rpx;
  flex-shrink: 0;
  margin-left: 12rpx;
}

/* 商品分类：图标 + 文字网格 */
.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24rpx 16rpx;
  padding: 28rpx 24rpx;
  background: #ffffff;
  margin: 20rpx 24rpx;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(255, 69, 68, 0.08);
}
.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  &:active { opacity: 0.85; }
}
.category-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: #f8fafc;
}
.category-name {
  font-size: 24rpx;
  color: #475569;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.section {
  padding: 24rpx;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-bottom: 20rpx;
  .section-title {
    font-size: 32rpx;
    font-weight: bold;
    color: #4f46e5;
    flex-shrink: 0;
  }
}
.recommend-tabs {
  flex: 1;
  white-space: nowrap;
  height: 56rpx;
}
.recommend-tab {
  display: inline-block;
  padding: 12rpx 24rpx;
  font-size: 24rpx;
  color: #64748b;
  background: #f8fafc;
  border-radius: 999rpx;
  margin-right: 16rpx;

  &.active {
    color: #fff;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
  }
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20rpx;
  overflow: hidden;
}

.notice-popup {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;

  .popup-content {
    width: 600rpx;
    max-height: 70vh;
    background: #ffffff;
    border-radius: 24rpx;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    box-shadow: 0 16rpx 48rpx rgba(0, 0, 0, 0.15);
  }

  .popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 28rpx 32rpx;
    border-bottom: 1rpx solid #e2e8f0;

    .popup-title { font-size: 32rpx; font-weight: bold; color: #4f46e5; }
    .popup-close { font-size: 36rpx; color: #94a3b8; padding: 0 8rpx; }
  }

  .popup-body {
    padding: 32rpx;
    max-height: 50vh;

    .popup-notice-title { font-size: 30rpx; font-weight: bold; color: #0f172a; display: block; margin-bottom: 20rpx; }
  }

  .popup-footer {
    padding: 24rpx 32rpx;
    border-top: 1rpx solid #e2e8f0;

    .popup-nav {
      display: flex;
      justify-content: center;
      gap: 32rpx;
      margin-bottom: 20rpx;

      .popup-nav-btn {
        font-size: 26rpx;
        color: #4f46e5;
        padding: 8rpx 24rpx;
        border: 1rpx solid rgba(99, 102, 241, 0.15);
        border-radius: 999rpx;

        &.disabled {
          color: #cbd5e1;
          border-color: #e2e8f0;
        }
      }
    }

    .popup-btn {
      height: 76rpx;
      line-height: 76rpx;
      text-align: center;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      color: #ffffff;
      font-weight: bold;
      border-radius: 999rpx;
      font-size: 28rpx;
    }
  }
}
.cs-float-btn {
  position: fixed;
  right: 24rpx;
  bottom: 240rpx;
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(255, 107, 43, 0.4);
  z-index: 100;

  .cs-icon {
    width: 52rpx;
    height: 52rpx;
    filter: brightness(0) saturate(100%) invert(100%);
  }
}
</style>
