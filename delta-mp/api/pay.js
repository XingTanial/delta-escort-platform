import { get, post } from './request'

/** 微信支付（返回wx.requestPayment参数） */
export const createPayment = (orderId) => post(`/pay/wx/${orderId}`)

/** 微信 H5 支付（返回 h5Url，H5 页面跳转到该地址） */
export const createH5Payment = (orderId) => post(`/pay/h5/${orderId}`)

/** 余额充值微信支付（返回wx.requestPayment参数） */
export const createRechargePayment = (packageId) => post(`/pay/recharge/wx/${packageId}`)

/** 余额充值微信 H5 支付（返回h5Url） */
export const createH5RechargePayment = (packageId) => post(`/pay/recharge/h5/${packageId}`)

/** 打手入驻押金支付(100元)，返回paymentNo及wx.requestPayment参数 */
export const createPlayerDeposit = () => post('/pay/player-deposit')

/** 余额支付 */
export const balancePay = (orderId) => post(`/pay/balance/${orderId}`)

/** 交易流水 */
export const getTransactions = (params) => get('/pay/transactions', params)
