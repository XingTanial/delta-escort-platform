<template>
  <div class="page-container">
    <el-card>
      <template #header><div class="card-header"><span>系统配置</span><el-button type="primary" :loading="submitting" @click="handleSave">保存配置</el-button></div></template>
      <el-tabs v-model="activeGroup" v-if="groupNames.length > 1">
        <el-tab-pane v-for="g in groupNames" :key="g" :label="g" :name="g" />
      </el-tabs>
      <el-form v-loading="loading" label-width="200px" class="config-form">
        <el-form-item v-for="item in filteredList" :key="item.configKey" :label="item.configName || item.configKey">
          <el-input v-if="item.valueType === 'file'" v-model="item.configValue" readonly>
            <template #append>
              <el-upload
                :show-file-list="false"
                :auto-upload="true"
                accept=".pem,.key,.txt"
                :http-request="(options) => handleCertificateUpload(item, options)"
              >
                <el-button>上传文件</el-button>
              </el-upload>
            </template>
          </el-input>
          <el-input v-else-if="item.valueType === 'text' || !item.valueType" v-model="item.configValue" />
          <el-input-number v-else-if="item.valueType === 'number'" v-model.number="item.configValue" :controls="false" style="width:200px" />
          <el-switch v-else-if="item.valueType === 'boolean'" v-model="item.configValue" active-value="true" inactive-value="false" />
          <el-input v-else-if="item.valueType === 'textarea'" v-model="item.configValue" type="textarea" :rows="3" />
          <ImageUpload v-else-if="item.valueType === 'image'" v-model="item.configValue" />
          <div class="config-desc" v-if="item.remark">{{ item.remark }}</div>
        </el-form-item>
        <el-empty v-if="!loading && filteredList.length === 0" description="暂无配置项" />
      </el-form>
    </el-card>
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { getSysConfigList, batchUpdateSysConfig, uploadWxPayCertificate } from '@/api/system'
import { ElMessage } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'
const loading = ref(false), submitting = ref(false)
const configList = ref([])
const activeGroup = ref('')
const visibleConfigList = computed(() => configList.value.filter(c => c.configKey !== 'wallet.recharge.packages'))
const groupNames = computed(() => {
  const groups = [...new Set(visibleConfigList.value.map(c => c.configGroup || '默认'))]
  return groups.length ? groups : ['默认']
})
const filteredList = computed(() => {
  // 只显示有 configName 的配置项
  const named = visibleConfigList.value.filter(c => c.configName)
  if (groupNames.value.length <= 1) return named
  const g = activeGroup.value || groupNames.value[0]
  return named.filter(c => (c.configGroup || '默认') === g)
})
async function fetchData() {
  loading.value = true
  try {
    const res = await getSysConfigList()
    configList.value = res.data || []
    if (groupNames.value.length && !activeGroup.value) activeGroup.value = groupNames.value[0]
  } finally { loading.value = false }
}
async function handleSave() {
  submitting.value = true
  try { await batchUpdateSysConfig(configList.value); ElMessage.success('保存成功') } finally { submitting.value = false }
}
async function handleCertificateUpload(item, options) {
  try {
    const res = await uploadWxPayCertificate(item.configKey, options.file)
    item.configValue = res.data || ''
    options.onSuccess?.(res)
    ElMessage.success('证书上传成功，请点击保存配置')
  } catch (e) {
    // request 已统一提示错误
    options.onError?.(e)
  }
}
onMounted(fetchData)
</script>
<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.config-form { max-width: 700px; }
.config-desc { color: #999; font-size: 12px; line-height: 1.4; margin-top: 4px; }
</style>
