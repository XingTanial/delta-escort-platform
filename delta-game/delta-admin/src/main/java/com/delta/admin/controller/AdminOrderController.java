package com.delta.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderProgress;
import com.delta.order.service.OrderProgressService;
import com.delta.order.service.OrderService;
import com.delta.common.annotation.OpLog;
import com.delta.common.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {
    private final OrderService orderService;
    private final OrderProgressService orderProgressService;
    private final CrossModuleMapper crossModuleMapper;

    @GetMapping("/list")
    public R<Page<Order>> list(PageQuery query,
                                @RequestParam(value = "status", required = false) String status,
                                @RequestParam(value = "orderNo", required = false) String orderNo,
                                @RequestParam(value = "playerId", required = false) Long playerId,
                                @RequestParam(value = "userId", required = false) Long userId) {
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<Order>()
                .eq(status != null && !status.isEmpty(), Order::getStatus, status)
                .eq(playerId != null, Order::getPlayerId, playerId)
                .eq(userId != null, Order::getUserId, userId)
                .like(orderNo != null && !orderNo.isEmpty(), Order::getOrderNo, orderNo)
                .orderByDesc(Order::getCreatedAt);
        Page<Order> page = orderService.page(new Page<>(query.getPageNum(), query.getPageSize()), w);
        for (Order o : page.getRecords()) {
            enrichOrder(o);
        }
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<Order> detail(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order != null) enrichOrder(order);
        return R.ok(order);
    }

    private void enrichOrder(Order o) {
        if (o.getUserId() != null) {
            o.setUserNickname(crossModuleMapper.selectUserNickname(o.getUserId()));
            o.setUserAvatar(crossModuleMapper.selectUserAvatar(o.getUserId()));
        }
        if (o.getPlayerId() != null) {
            o.setPlayerName(crossModuleMapper.selectPlayerNickname(o.getPlayerId()));
            o.setPlayerAvatar(crossModuleMapper.selectPlayerAvatar(o.getPlayerId()));
        }
    }

    @GetMapping("/{id}/progress")
    public R<List<OrderProgress>> progress(@PathVariable Long id) {
        return R.ok(orderProgressService.list(new LambdaQueryWrapper<OrderProgress>()
                .eq(OrderProgress::getOrderId, id).orderByAsc(OrderProgress::getCreatedAt)));
    }

    @OpLog(module = "order", operation = "指派订单")
    @PostMapping("/{id}/assign/{playerId}")
    public R<Void> assign(@PathVariable Long id, @PathVariable Long playerId) {
        orderService.assignOrder(id, playerId, "ADMIN", SecurityUtils.getUserId());
        return R.ok();
    }

    @OpLog(module = "order", operation = "客服退款")
    @PostMapping("/{id}/refund")
    public R<Void> refund(@PathVariable Long id) {
        orderService.csCancelOrder(id, SecurityUtils.getUserId());
        return R.ok();
    }

    @OpLog(module = "order", operation = "手动结单")
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        orderService.manualConfirmOrder(id, SecurityUtils.getUserId(), "ADMIN");
        return R.ok();
    }
}
