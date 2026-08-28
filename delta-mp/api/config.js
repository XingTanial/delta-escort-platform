import { get } from './request'

/** 获取站点配置（公开接口，无需登录） */
export function getSiteConfig() {
  return get('/system/config/site', {}, { auth: false, loading: false })
}

/** 获取启用中的余额充值套餐（公开接口） */
export function getRechargePackages() {
  return get('/system/recharge-packages', {}, { auth: false, loading: false })
}
