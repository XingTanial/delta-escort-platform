import { get, post, put, del } from './request'

/** 获取用户资料（始终使用 user_token） */
export const getUserProfile = (opts = {}) => get('/user/profile', {}, { role: 'user', ...opts })

/** 修改用户资料（始终使用 user_token） */
export const updateUserProfile = (data) => put('/user/profile', data, { role: 'user' })

/** 修改手机号（始终使用 user_token） */
export const updatePhone = (data) => put('/user/phone', data, { role: 'user' })

/** 获取钱包信息（始终使用 user_token） */
export const getWallet = () => get('/user/wallet', {}, { role: 'user' })

/** 已保存的游戏信息列表（下单页可选择复用） */
export const getGameInfoList = () => get('/user/game-info/list', {}, { role: 'user' })

/** 保存当前填写的游戏信息，下次下单可选择 */
export const saveGameInfo = (data) => post('/user/game-info', data, { role: 'user' })

/** 按分类获取已保存的动态字段信息 */
export const getSavedInfoByCategory = (categoryId) => get(`/user/game-info/list/${categoryId}`, {}, { role: 'user' })

/** 保存动态字段信息 */
export const saveDynamicInfo = (data) => post('/user/game-info/dynamic', data, { role: 'user' })

/** 删除已保存信息 */
export const deleteSavedInfo = (id) => del(`/user/game-info/${id}`, {}, { role: 'user' })
