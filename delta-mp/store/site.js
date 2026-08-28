import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getSiteConfig } from '@/api/config'

export const useSiteStore = defineStore('site', () => {
  const siteName = ref('三角洲')
  const subtitle = ref('专业游戏平台')
  const logo = ref('')
  const adminTitle = ref('三角洲管理后台')
  /** 接单员端抽佣比例（百分比，如 10 表示 10%），由接口 /system/config/site 返回 player_commission_rate */
  const playerCommissionRate = ref(0)
  /** 打手入驻押金开关 */
  const depositRequired = ref(true)
  /** 后台微信支付总开关 */
  const wxPayEnabled = ref(false)

  /** 完整平台名称（用于协议等文案） */
  const fullName = computed(() => siteName.value + '平台')

  async function fetchSiteConfig() {
    try {
      const res = await getSiteConfig()
      const data = res.data || {}
      if (data.site_name) siteName.value = data.site_name
      if (data.site_subtitle) subtitle.value = data.site_subtitle
      if (data.site_logo) logo.value = data.site_logo
      if (data.site_admin_title) adminTitle.value = data.site_admin_title
      if (data.player_commission_rate !== undefined && data.player_commission_rate !== null) {
        playerCommissionRate.value = Number(data.player_commission_rate) || 0
      }
      if (data.player_deposit_required !== undefined) {
        depositRequired.value = data.player_deposit_required === 'true'
      }
      if (data.wx_pay_enabled !== undefined) {
        wxPayEnabled.value = data.wx_pay_enabled === 'true'
      }
    } catch (e) {
      console.warn('[SiteStore] fetch site config failed', e)
    }
  }

  return { siteName, subtitle, logo, adminTitle, playerCommissionRate, depositRequired, wxPayEnabled, fullName, fetchSiteConfig }
})
