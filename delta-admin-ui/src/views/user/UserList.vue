<template>
  <div class="page-container">
    <el-card>
      <template #header><span>用户管理</span></template>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item><el-input v-model="query.keyword" placeholder="用户名/手机号" clearable @keyup.enter="fetchData" /></el-form-item>
        <el-form-item><el-select v-model="query.status" placeholder="状态" clearable><el-option label="正常" :value="1" /><el-option label="封禁" :value="0" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="fetchData">查询</el-button><el-button @click="query.keyword='';query.status='';fetchData()">重置</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="avatar" label="头像" width="70"><template #default="{ row }"><el-avatar :src="row.avatar" :size="36" /></template></el-table-column>
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="balance" label="余额" width="100"><template #default="{ row }"><span class="balance-text">¥{{ row.balance || 0 }}</span></template></el-table-column>
        <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '正常' : '封禁' }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="170" />
        <el-table-column label="操作" :width="isAdmin ? 320 : 240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
            <el-button link type="info" @click="showUserOrders(row)">订单</el-button>
            <el-button v-if="isAdmin" link type="warning" @click="openBalanceDialog(row)">余额</el-button>
            <el-popconfirm :title="row.status === 1 ? '确认封禁该用户？' : '确认解封该用户？'" @confirm="handleToggleStatus(row)">
              <template #reference><el-button link :type="row.status === 1 ? 'danger' : 'success'">{{ row.status === 1 ? '封禁' : '解封' }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="fetchData" />
    </el-card>

    <!-- 用户详情弹窗 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="550px">
      <el-descriptions :column="2" border v-if="detailData" v-loading="detailLoading">
        <el-descriptions-item label="ID">{{ detailData.user?.id }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detailData.user?.nickname }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailData.user?.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="detailData.user?.status === 1 ? 'success' : 'danger'" size="small">{{ detailData.user?.status === 1 ? '正常' : '封禁' }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="余额">¥{{ detailData.wallet?.balance || 0 }}</el-descriptions-item>
        <el-descriptions-item label="订单数">{{ detailData.orderCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="注册时间" :span="2">{{ detailData.user?.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 用户订单弹窗 -->
    <el-dialog v-model="ordersVisible" :title="ordersUserName ? ordersUserName + ' 的订单' : '用户订单'" width="900px" destroy-on-close>
      <el-table :data="userOrders" v-loading="ordersLoading" stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
        <el-table-column prop="playerName" label="打手" width="120">
          <template #default="{ row }">{{ row.playerName || (row.playerId ? 'ID: ' + row.playerId : '未指派') }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="orderTagType(row.status)" size="small">{{ orderStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="170" />
      </el-table>
      <Pagination :total="ordersTotal" v-model:page="ordersQuery.pageNum" v-model:limit="ordersQuery.pageSize" @pagination="fetchUserOrders" />
    </el-dialog>

    <!-- 余额调整弹窗 -->
    <el-dialog v-model="balanceVisible" title="余额调整" width="450px" destroy-on-close>
      <div class="balance-info">
        <span>用户：<strong>{{ balanceUser?.nickname }}</strong></span>
        <span>当前余额：<strong class="balance-text">¥{{ balanceUser?.balance || 0 }}</strong></span>
      </div>
      <el-form ref="balanceFormRef" :model="balanceForm" :rules="balanceRules" label-width="80px" style="margin-top: 20px">
        <el-form-item label="操作类型">
          <el-radio-group v-model="balanceForm.type">
            <el-radio-button value="add">充值（增加）</el-radio-button>
            <el-radio-button value="sub">扣款（减少）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="balanceForm.amount" :min="0.01" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="balanceForm.remark" placeholder="操作原因（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="balanceVisible = false">取消</el-button>
        <el-button type="primary" :loading="balanceSubmitting" @click="handleBalanceSubmit">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminUserList, adminUserDetail, adminUserUpdateStatus, adminUserAdjustBalance, adminOrderList, csUserList, csUserDetail, csUserUpdateStatus, csOrderList } from '@/api/business'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/Pagination.vue'

const userStore = useUserStore()
const isAdmin = userStore.role === 'admin'
const loading = ref(false), list = ref([]), total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })
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
function orderStatusLabel(status) { return orderStatusMap[status] || status }
function orderTagType(status) { return orderTagTypeMap[status] || '' }

async function fetchData() {
  loading.value = true
  try {
    const fn = isAdmin ? adminUserList : csUserList
    const res = await fn(query)
    list.value = res.data.records; total.value = Number(res.data.total)
  } finally { loading.value = false }
}

async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  const fn = isAdmin ? adminUserUpdateStatus : csUserUpdateStatus
  await fn(row.id, newStatus)
  ElMessage.success('操作成功'); fetchData()
}

const detailVisible = ref(false), detailData = ref(null), detailLoading = ref(false)
async function showDetail(id) {
  detailVisible.value = true; detailLoading.value = true; detailData.value = null
  try {
    const res = isAdmin ? await adminUserDetail(id) : await csUserDetail(id)
    detailData.value = res.data
  } finally { detailLoading.value = false }
}

const ordersVisible = ref(false), ordersLoading = ref(false)
const userOrders = ref([]), ordersTotal = ref(0), ordersUserName = ref('')
const ordersQuery = reactive({ pageNum: 1, pageSize: 10, userId: null })
function showUserOrders(row) {
  ordersUserName.value = row.nickname || ('ID: ' + row.id)
  ordersQuery.userId = row.id
  ordersQuery.pageNum = 1
  userOrders.value = []
  ordersTotal.value = 0
  ordersVisible.value = true
  fetchUserOrders()
}
async function fetchUserOrders() {
  if (!ordersQuery.userId) return
  ordersLoading.value = true
  try {
    const fn = isAdmin ? adminOrderList : csOrderList
    const res = await fn({
      pageNum: ordersQuery.pageNum,
      pageSize: ordersQuery.pageSize,
      userId: ordersQuery.userId
    })
    userOrders.value = res.data?.records || []
    ordersTotal.value = Number(res.data?.total || 0)
  } finally { ordersLoading.value = false }
}

const balanceVisible = ref(false)
const balanceUser = ref(null)
const balanceSubmitting = ref(false)
const balanceFormRef = ref(null)
const balanceForm = reactive({ type: 'add', amount: null, remark: '' })
const balanceRules = {
  amount: [{ required: true, message: '请输入金额', trigger: 'change' }]
}

function openBalanceDialog(row) {
  balanceUser.value = row
  balanceForm.type = 'add'
  balanceForm.amount = null
  balanceForm.remark = ''
  balanceVisible.value = true
}

async function handleBalanceSubmit() {
  const valid = await balanceFormRef.value.validate().catch(() => false)
  if (!valid) return

  const amount = balanceForm.type === 'add' ? balanceForm.amount : -balanceForm.amount
  const action = balanceForm.type === 'add' ? '充值' : '扣款'

  try {
    await ElMessageBox.confirm(
      `确认对用户 "${balanceUser.value.nickname}" ${action} ¥${balanceForm.amount}？`,
      '确认操作', { type: 'warning' }
    )
  } catch { return }

  balanceSubmitting.value = true
  try {
    await adminUserAdjustBalance(balanceUser.value.id, { amount, remark: balanceForm.remark })
    ElMessage.success(`${action}成功`)
    balanceVisible.value = false
    fetchData()
  } catch (e) {
    const msg = e?.data?.msg || e?.msg || '操作失败'
    ElMessage.error(msg)
  } finally {
    balanceSubmitting.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.search-form { margin-bottom: 16px; }
.balance-text { color: #ff6b2b; font-weight: 600; }
.balance-info {
  display: flex;
  justify-content: space-between;
  padding: 16px 20px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 14px;
  color: #606266;
}
</style>
