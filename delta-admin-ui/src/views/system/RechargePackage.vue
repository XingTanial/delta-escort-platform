<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span>余额充值套餐</span>
            <span class="header-tip">用户支付充值金额后，到账金额 = 充值金额 + 赠送金额</span>
          </div>
          <div class="header-actions">
            <el-button @click="addPackage">新增套餐</el-button>
            <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="packages" border>
        <el-table-column label="套餐名称" min-width="170">
          <template #default="{ row }"><el-input v-model="row.name" placeholder="如：畅玩套餐" /></template>
        </el-table-column>
        <el-table-column label="充值金额（元）" width="180">
          <template #default="{ row }"><el-input-number v-model="row.amount" :min="0.01" :precision="2" :controls="false" /></template>
        </el-table-column>
        <el-table-column label="赠送金额（元）" width="180">
          <template #default="{ row }"><el-input-number v-model="row.bonus" :min="0" :precision="2" :controls="false" /></template>
        </el-table-column>
        <el-table-column label="到账金额（元）" width="150">
          <template #default="{ row }"><span class="total">¥{{ total(row) }}</span></template>
        </el-table-column>
        <el-table-column label="排序" width="130">
          <template #default="{ row }"><el-input-number v-model="row.sort" :min="1" :precision="0" :controls="false" /></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ $index }"><el-button link type="danger" @click="removePackage($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && packages.length === 0" description="暂无充值套餐，请新增" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRechargePackages, updateRechargePackages } from '@/api/system'

const loading = ref(false)
const saving = ref(false)
const packages = ref([])

function newPackage() {
  return { id: `new-${Date.now()}-${Math.random().toString(16).slice(2)}`, name: '余额充值套餐', amount: 100, bonus: 0, enabled: true, sort: packages.value.length + 1 }
}
function addPackage() { packages.value.push(newPackage()) }
function total(row) { return (Number(row.amount || 0) + Number(row.bonus || 0)).toFixed(2) }
async function removePackage(index) {
  try {
    await ElMessageBox.confirm('删除后用户将无法购买该套餐，确定继续吗？', '提示', { type: 'warning' })
    packages.value.splice(index, 1)
  } catch (e) { /* 用户取消 */ }
}
async function fetchData() {
  loading.value = true
  try {
    const res = await getRechargePackages()
    packages.value = (res.data || []).map(item => ({ ...item, enabled: item.enabled !== false }))
  } finally { loading.value = false }
}
async function save() {
  if (packages.value.some(item => !Number(item.amount) || Number(item.amount) <= 0 || Number(item.bonus) < 0)) {
    return ElMessage.warning('请检查充值金额和赠送金额')
  }
  saving.value = true
  try {
    await updateRechargePackages(packages.value)
    ElMessage.success('充值套餐已保存')
    await fetchData()
  } finally { saving.value = false }
}
onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.header-tip { margin-left: 14px; color: #94a3b8; font-size: 12px; }
.header-actions { display: flex; gap: 10px; }
.total { color: #f59e0b; font-weight: 700; }
</style>
