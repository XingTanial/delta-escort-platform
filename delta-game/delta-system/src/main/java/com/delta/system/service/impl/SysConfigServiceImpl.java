package com.delta.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.redis.service.RedisService;
import com.delta.system.dto.RechargePackage;
import com.delta.system.entity.SysConfig;
import com.delta.system.mapper.SysConfigMapper;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {
    private static final String CACHE_PREFIX = "sys_config:";
    private static final List<RechargePackage> DEFAULT_RECHARGE_PACKAGES = List.of(
            packageOf("100", "基础套餐", "100", "10", 1),
            packageOf("300", "进阶套餐", "300", "40", 2),
            packageOf("500", "畅玩套餐", "500", "100", 3)
    );
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @Override
    public String getConfigValue(String key) {
        String cached = redisService.getCacheObject(CACHE_PREFIX + key);
        if (cached != null) return cached;
        SysConfig config = getOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config != null) {
            redisService.setCacheObject(CACHE_PREFIX + key, config.getConfigValue(), 1L, TimeUnit.HOURS);
            return config.getConfigValue();
        }
        return null;
    }

    @Override
    public String getConfigValue(String key, String defaultValue) {
        String val = getConfigValue(key);
        return val != null ? val : defaultValue;
    }

    @Override
    public List<RechargePackage> getRechargePackages(boolean activeOnly) {
        String json = getConfigValue(RECHARGE_PACKAGES_KEY);
        List<RechargePackage> packages;
        try {
            packages = json == null || json.isBlank()
                    ? copyDefaults()
                    : objectMapper.readValue(json, objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, RechargePackage.class));
        } catch (Exception e) {
            packages = copyDefaults();
        }
        packages = packages == null ? new ArrayList<>() : packages;
        packages.removeIf(item -> item == null || item.getAmount() == null
                || item.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || item.getBonus() == null || item.getBonus().compareTo(BigDecimal.ZERO) < 0);
        packages.sort(Comparator.comparing(RechargePackage::getSort,
                Comparator.nullsLast(Integer::compareTo)).thenComparing(RechargePackage::getAmount));
        if (activeOnly) packages.removeIf(item -> !Boolean.TRUE.equals(item.getEnabled()));
        return packages;
    }

    @Override
    public void saveRechargePackages(List<RechargePackage> packages) {
        if (packages == null) throw new IllegalArgumentException("充值套餐不能为空");
        Set<String> ids = new HashSet<>();
        List<RechargePackage> normalized = new ArrayList<>();
        for (RechargePackage item : packages) {
            if (item == null || item.getAmount() == null
                    || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("充值金额必须大于0");
            }
            if (item.getBonus() == null || item.getBonus().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("赠送金额不能小于0");
            }
            String id = item.getId();
            if (id == null || id.isBlank()) id = UUID.randomUUID().toString().replace("-", "");
            if (!ids.add(id)) throw new IllegalArgumentException("充值套餐ID不能重复");
            item.setId(id);
            if (item.getEnabled() == null) item.setEnabled(true);
            if (item.getSort() == null) item.setSort(normalized.size() + 1);
            if (item.getName() == null || item.getName().isBlank()) item.setName("余额充值套餐");
            normalized.add(item);
        }
        try {
            SysConfig config = getOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, RECHARGE_PACKAGES_KEY));
            if (config == null) {
                config = new SysConfig();
                config.setConfigKey(RECHARGE_PACKAGES_KEY);
                config.setConfigName("余额充值套餐");
                config.setValueType("json");
                config.setConfigGroup("余额配置");
                config.setRemark("配置用户充值金额及赠送余额，单位：元");
                config.setCreatedAt(LocalDateTime.now());
            }
            config.setConfigValue(objectMapper.writeValueAsString(normalized));
            config.setUpdatedAt(LocalDateTime.now());
            if (config.getId() == null) save(config); else updateById(config);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("充值套餐配置格式错误", e);
        }
    }

    private List<RechargePackage> copyDefaults() {
        return objectMapper.convertValue(DEFAULT_RECHARGE_PACKAGES,
                objectMapper.getTypeFactory().constructCollectionType(List.class, RechargePackage.class));
    }

    private static RechargePackage packageOf(String id, String name, String amount, String bonus, int sort) {
        RechargePackage item = new RechargePackage();
        item.setId(id);
        item.setName(name);
        item.setAmount(new BigDecimal(amount));
        item.setBonus(new BigDecimal(bonus));
        item.setEnabled(true);
        item.setSort(sort);
        return item;
    }

    @Override
    public boolean updateById(SysConfig entity) {
        // 先查出 configKey 再清缓存（前端可能只传 id + configValue）
        if (entity.getConfigKey() == null && entity.getId() != null) {
            SysConfig old = getById(entity.getId());
            if (old != null) entity.setConfigKey(old.getConfigKey());
        }
        boolean ok = super.updateById(entity);
        if (ok && entity.getConfigKey() != null) {
            redisService.deleteObject(CACHE_PREFIX + entity.getConfigKey());
        }
        return ok;
    }

    @Override
    public boolean updateBatchById(java.util.Collection<SysConfig> entityList) {
        boolean ok = super.updateBatchById(entityList);
        if (ok) {
            for (SysConfig entity : entityList) {
                if (entity.getConfigKey() == null && entity.getId() != null) {
                    SysConfig old = getById(entity.getId());
                    if (old != null) entity.setConfigKey(old.getConfigKey());
                }
                if (entity.getConfigKey() != null) {
                    redisService.deleteObject(CACHE_PREFIX + entity.getConfigKey());
                }
            }
        }
        return ok;
    }
}
