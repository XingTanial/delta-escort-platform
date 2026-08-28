<template>
  <div class="page-container">
    <el-card>
      <template #header><span>订单管理</span></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item><el-input v-model="query.orderNo" placeholder="订单号" clearable @keyup.enter="fetchData" /></el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="订单状态" clearable>
            <el-option v-for="(v, k) in orderStatusMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="fetchData">查询</el-button><el-button @click="query.orderNo='';query.status='';fetchData()">重置</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
        <el-table-column prop="userNickname" label="用户" width="120">
          <template #default="{ row }">{{ row.userNickname || ('ID: ' + row.userId) }}</template>
        </el-table-column>
        <el-table-column prop="playerName" label="打手" width="120">
          <template #default="{ row }">{{ row.playerName || (row.playerId ? 'ID: ' + row.playerId : '未指派') }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="100"><template #default="{ row }">¥{{ row.amount }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }"><el-tag :type="orderTagType(row.status)" size="small">{{ orderStatusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
            <el-button v-if="canAssign(row)" link type="warning" @click="openAssignDialog(row)">{{ row.status === 'ASSIGNED' ? '重新指派' : '指派' }}</el-button>
            <el-button v-if="['ASSIGNED','IN_PROGRESS'].includes(row.status)" link type="danger" @click="handleRefund(row)">退款</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>
    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="700px">
      <el-descriptions :column="2" border v-if="detail" v-loading="detailLoading">
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="orderTagType(detail.status)" size="small">{{ orderStatusLabel(detail.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="商品">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ detail.specInfo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ detail.amount }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ detail.userNickname || ('ID: ' + detail.userId) }}</el-descriptions-item>
        <el-descriptions-item label="打手">{{ detail.playerName || (detail.playerId ? 'ID: ' + detail.playerId : '未指派') }}</el-descriptions-item>
        <el-descriptions-item label="游戏账号">{{ detail.gameAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ detail.contact || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="指派时间">{{ detail.assignTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ detail.completeTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="parsedExtraFields(detail).length" label="动态字段" :span="2">
          <div v-for="f in parsedExtraFields(detail)" :key="f.key" style="margin-bottom:4px">
            <span style="color:#909399">{{ f.key }}：</span>{{ f.value }}
          </div>
        </el-descriptions-item>
      </el-descriptions>
      <div v-if="detail" class="detail-actions">
        <el-button v-if="detail.status === 'COMPLETED'" type="primary" :loading="confirmLoading" @click="handleConfirm(detail)">结单</el-button>
      </div>
      <div v-if="progressList.length" class="progress-section">
        <h4 style="margin:20px 0 12px;color:#303133">订单进度</h4>
        <el-timeline>
          <el-timeline-item v-for="(p, idx) in progressList" :key="idx" :timestamp="p.createdAt" placement="top"
            :type="idx === progressList.length - 1 ? 'primary' : ''">
            <span>{{ p.content || p.description || orderStatusLabel(p.toStatus) || p.toStatus }}</span>
            <span v-if="p.operatorType" style="margin-left:8px;color:#909399;font-size:12px">({{ { USER: '用户', PLAYER: '打手', CS: '客服', SYSTEM: '系统', ADMIN: '管理员' }[p.operatorType] || p.operatorType }})</span>
            <div v-if="p.images" class="progress-images">
              <el-image
                v-for="(img, imgIdx) in p.images.split(',')"
                :key="img + imgIdx"
                :src="img"
                :preview-src-list="p.images.split(',')"
                :initial-index="imgIdx"
                fit="cover"
                class="progress-image"
              />
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>
    <!-- 指派弹窗 -->
    <el-dialog v-model="assignVisible" :title="assignMode === 'reassign' ? '重新指派打手' : '指派打手'" width="600px">
      <el-form :inline="true" style="margin-bottom:12px">
        <el-form-item><el-input v-model="playerSearch" placeholder="搜索打手" clearable @keyup.enter="fetchPlayers" /></el-form-item>
        <el-form-item><el-button type="primary" @click="fetchPlayers">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="playerList" v-loading="playerLoading" stripe size="small" max-height="300" :row-class-name="playerRowClass">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column label="在线" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isOnline === 1 ? 'success' : 'info'" size="small">{{ row.isOnline === 1 ? '在线' : '离线' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="{ ACTIVE: 'success', PENDING: 'warning', REJECTED: 'danger', FROZEN: 'info' }[row.status] || 'info'" size="small">{{ { PENDING: '待审核', ACTIVE: '正常', REJECTED: '已驳回', FROZEN: '已冻结' }[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.isOnline !== 1" @click="handleAssign(row.id)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminOrderList, adminOrderDetail, adminOrderAssign, adminOrderRefund, adminOrderConfirm, adminOrderProgress, adminPlayerList, csOrderList, csOrderDetail, csOrderAssign, csOrderRefund, csOrderConfirm, csOrderProgress, csPlayerList } from '@/api/business'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
const userStore = useUserStore()
const isAdmin = userStore.role === 'admin'
// 兼容后端大写和前端小写枚举
const orderStatusMap = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ASSIGNED: '已指派', ACCEPTED: '已接单',
  WAITING_TEAMMATE: '组队中', IN_PROGRESS: '进行中', COMPLETED: '待确认',
  CONFIRMED: '已完成', REVIEWED: '已评价', CANCELLED: '已取消',
  REFUNDING: '退款中', REFUNDED: '已退款', DISPUTED: '争议中', ARBITRATED: '已仲裁'
}
const orderTagTypeMap = {
  PENDING_PAYMENT: 'info', PAID: '', ASSIGNED: '', ACCEPTED: '',
  WAITING_TEAMMATE: 'warning', IN_PROGRESS: 'warning', COMPLETED: 'success',
  CONFIRMED: 'success', REVIEWED: 'success', CANCELLED: 'info',
  REFUNDING: 'danger', REFUNDED: 'info', DISPUTED: 'danger', ARBITRATED: 'info'
}
function orderStatusLabel(s) { return orderStatusMap[s] || s }
function orderTagType(s) { return orderTagTypeMap[s] || '' }
function parsedExtraFields(order) {
  if (!order?.extraFields) return []
  try {
    const obj = typeof order.extraFields === 'string' ? JSON.parse(order.extraFields) : order.extraFields
    return Object.entries(obj).map(([key, value]) => ({ key, value }))
  } catch { return [] }
}
const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, orderNo: '', status: '' })
async function fetchData() {
  loading.value = true
  try {
    const fn = isAdmin ? adminOrderList : csOrderList
    const res = await fn(query)
    list.value = res.data.records; total.value = Number(res.data.total)
  } finally { loading.value = false }
}
// 详情
const detailVisible = ref(false), detail = ref(null), detailLoading = ref(false), progressList = ref([])
const confirmLoading = ref(false)
async function showDetail(id) {
  detailVisible.value = true; detailLoading.value = true; detail.value = null; progressList.value = []
  try {
    const detailFn = isAdmin ? adminOrderDetail : csOrderDetail
    const progressFn = isAdmin ? adminOrderProgress : csOrderProgress
    const [detailRes, progressRes] = await Promise.all([detailFn(id), progressFn(id)])
    detail.value = detailRes.data
    progressList.value = progressRes.data || []
  } finally { detailLoading.value = false }
}
async function handleConfirm(row) {
  await ElMessageBox.confirm('确定手动结单？订单将变为已确认状态。', '结单确认', { type: 'warning' })
  confirmLoading.value = true
  try {
    const fn = isAdmin ? adminOrderConfirm : csOrderConfirm
    await fn(row.id)
    ElMessage.success('结单成功')
    await showDetail(row.id)
    fetchData()
  } finally {
    confirmLoading.value = false
  }
}
// 指派
const assignVisible = ref(false), assignOrderId = ref(null), assignMode = ref('assign')
const playerSearch = ref(''), playerList = ref([]), playerLoading = ref(false)
function canAssign(row) {
  return (row.status === 'PAID' && !row.playerId) || row.status === 'ASSIGNED'
}
function openAssignDialog(row) {
  assignOrderId.value = row.id
  assignMode.value = row.status === 'ASSIGNED' ? 'reassign' : 'assign'
  playerSearch.value = ''
  playerList.value = []
  assignVisible.value = true
  fetchPlayers()
}
async function fetchPlayers() {
  playerLoading.value = true
  try {
    const fn = isAdmin ? adminPlayerList : csPlayerList
    const res = await fn({ pageNum: 1, pageSize: 20, keyword: playerSearch.value, status: 'ACTIVE' })
    playerList.value = res.data.records || []
  } finally { playerLoading.value = false }
}
function playerRowClass({ row }) {
  return row.isOnline !== 1 ? 'offline-row' : ''
}
async function handleAssign(playerId) {
  const assignFn = isAdmin ? adminOrderAssign : csOrderAssign
  await assignFn(assignOrderId.value, playerId)
  ElMessage.success(assignMode.value === 'reassign' ? '重新指派成功' : '指派成功')
  assignVisible.value = false
  fetchData()
}
async function handleRefund(row) {
  await ElMessageBox.confirm('确定为该订单退款？订单将被取消并退回支付金额。', '退款确认', { type: 'warning' })
  const fn = isAdmin ? adminOrderRefund : csOrderRefund
  await fn(row.id)
  ElMessage.success('退款已提交')
  fetchData()
}
onMounted(fetchData)
</script>
<style scoped>
.search-form { margin-bottom: 16px; }
:deep(.offline-row) { opacity: 0.5; }
.detail-actions { margin-top: 16px; text-align: right; }
.progress-images { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.progress-image { width: 72px; height: 72px; border-radius: 6px; overflow: hidden; }
</style>
