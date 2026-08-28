<template>
  <view class="review-page">
    <view class="rating-area"><text>评分</text>
      <view class="stars"><text v-for="i in 5" :key="i" class="star" :class="{active:rating>=i}" @click="rating=i">★</text></view>
    </view>
    <textarea v-model="content" placeholder="请输入评价内容" :maxlength="500" />
    <ImageUploader v-model="images" :max="3" />
    <view class="btn" @click="submit">提交评价</view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import ImageUploader from '@/components/ImageUploader.vue'
import { addReview } from '@/api/review'
const orderId = ref(0)
const rating = ref(5)
const content = ref('')
const images = ref([])
onLoad((opts) => { orderId.value = opts.orderId })
async function submit() {
  if (!content.value) return uni.showToast({title:'请输入评价',icon:'none'})
  await addReview({ orderId: orderId.value, rating: rating.value, content: content.value, images: images.value.join(',') })
  uni.showToast({title:'评价成功'})
  setTimeout(() => uni.navigateBack(), 1500)
}
</script>
<style lang="scss" scoped>
.review-page { padding:24rpx; background:#ffffff; min-height:100vh; }
.rating-area { display:flex; align-items:center; gap:20rpx; margin-bottom:24rpx; background:#ffffff; border:1rpx solid #edf1f7; padding:24rpx; border-radius:16rpx; color:#1e293b; }
.stars { display:flex; gap:8rpx; }
.star { font-size:48rpx; color:#cbd5e1; &.active { color:#ff9900; } }
textarea { width:100%; height:240rpx; background:#f8fafc; border:1rpx solid #edf1f7; border-radius:12rpx; padding:24rpx; font-size:28rpx; margin-bottom:24rpx; color:#1e293b; }
.btn { height:88rpx; line-height:88rpx; text-align:center; background:linear-gradient(135deg, #6366f1, #8b5cf6); color:#ffffff; font-weight:bold; font-size:32rpx; border-radius:999rpx; margin-top:40rpx; }
</style>
