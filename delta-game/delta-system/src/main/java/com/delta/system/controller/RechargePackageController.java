package com.delta.system.controller;

import com.delta.common.domain.R;
import com.delta.system.dto.RechargePackage;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户端公开读取启用中的余额充值套餐。 */
@RestController
@RequestMapping("/system/recharge-packages")
@RequiredArgsConstructor
public class RechargePackageController {
    private final SysConfigService sysConfigService;

    @GetMapping
    public R<List<RechargePackage>> list() {
        return R.ok(sysConfigService.getRechargePackages(true));
    }
}
