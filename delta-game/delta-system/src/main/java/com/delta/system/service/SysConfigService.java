package com.delta.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.system.entity.SysConfig;
import com.delta.system.dto.RechargePackage;

import java.util.List;

public interface SysConfigService extends IService<SysConfig> {
    String RECHARGE_PACKAGES_KEY = "wallet.recharge.packages";

    /** 根据 key 获取配置值（带 Redis 缓存） */
    String getConfigValue(String key);

    /** 获取配置值，不存在返回默认值 */
    String getConfigValue(String key, String defaultValue);

    /** 获取余额充值套餐，activeOnly=true 时只返回启用套餐。 */
    List<RechargePackage> getRechargePackages(boolean activeOnly);

    /** 保存并校验余额充值套餐。 */
    void saveRechargePackages(List<RechargePackage> packages);
}
