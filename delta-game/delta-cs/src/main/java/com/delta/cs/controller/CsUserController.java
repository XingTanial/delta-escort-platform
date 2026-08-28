package com.delta.cs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.user.entity.User;
import com.delta.user.entity.Wallet;
import com.delta.user.service.UserService;
import com.delta.user.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/cs/user")
@RequiredArgsConstructor
public class CsUserController {
    private final UserService userService;
    private final WalletService walletService;
    private final OrderService orderService;

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> data = new HashMap<>();
        data.put("user", userService.getById(id));
        data.put("wallet", walletService.getByUserId(id));
        long orderCount = orderService.count(new LambdaQueryWrapper<Order>().eq(Order::getUserId, id));
        data.put("orderCount", orderCount);
        return R.ok(data);
    }

    @GetMapping("/list")
    public R<Page<User>> list(PageQuery query, @RequestParam(value = "keyword", required = false) String keyword) {
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<User>()
                .like(keyword != null, User::getNickname, keyword)
                .orderByDesc(User::getCreatedAt);
        return R.ok(userService.page(new Page<>(query.getPageNum(), query.getPageSize()), w));
    }

    /**
     * 更新用户状态
     * H5端发送 query param: ?status=0/1
     * MP端发送 JSON body: {status: 'ACTIVE'/'DISABLED'} 或 {status: 0/1}
     */
    @OpLog(module = "user", operation = "更新用户状态")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id,
                                 @RequestBody(required = false) Map<String, Object> body,
                                 @RequestParam(value = "status", required = false) Integer statusParam) {
        Integer status = statusParam;
        if (status == null && body != null && body.containsKey("status")) {
            Object val = body.get("status");
            if (val instanceof Number) {
                status = ((Number) val).intValue();
            } else if (val instanceof String s) {
                status = "ACTIVE".equalsIgnoreCase(s) || "1".equals(s) ? 1 : 0;
            }
        }
        if (status == null) return R.fail("status参数不能为空");
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        userService.updateById(user);
        return R.ok();
    }
}
