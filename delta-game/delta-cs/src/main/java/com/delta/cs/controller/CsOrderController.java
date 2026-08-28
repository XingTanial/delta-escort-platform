package com.delta.cs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.mapper.StatsMapper;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderProgress;
import com.delta.order.service.OrderProgressService;
import com.delta.order.service.OrderService;
import com.delta.common.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/cs/order")
@RequiredArgsConstructor
public class CsOrderController {
    private final OrderService orderService;
    private final OrderProgressService orderProgressService;
    private final CrossModuleMapper crossModuleMapper;
    private final StatsMapper statsMapper;

    @GetMapping("/list")
    public R<Page<Order>> list(PageQuery query, @RequestParam(value = "status", required = false) String status,
                                @RequestParam(value = "orderNo", required = false) String orderNo,
                                @RequestParam(value = "keyword", required = false) String keyword,
                                @RequestParam(value = "userId", required = false) Long userId,
                                @RequestParam(value = "playerId", required = false) Long playerId) {
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<Order>()
                .eq(userId != null, Order::getUserId, userId)
                .eq(playerId != null, Order::getPlayerId, playerId)
                .eq(status != null && !status.isEmpty(), Order::getStatus, status)
                .and((keyword != null && !keyword.isEmpty()) || (orderNo != null && !orderNo.isEmpty()), qw -> {
                    if (orderNo != null && !orderNo.isEmpty()) qw.like(Order::getOrderNo, orderNo);
                    else if (keyword != null && !keyword.isEmpty()) qw.like(Order::getOrderNo, keyword);
                })
                .orderByDesc(Order::getCreatedAt);
        Page<Order> page = orderService.page(new Page<>(query.getPageNum(), query.getPageSize()), w);
        for (Order o : page.getRecords()) {
            if (o.getUserId() != null) {
                String nick = crossModuleMapper.selectUserNickname(o.getUserId());
                o.setUserNickname((nick != null && !nick.isEmpty()) ? nick : "用户" + o.getUserId());
                o.setUserAvatar(crossModuleMapper.selectUserAvatar(o.getUserId()));
            }
            if (o.getPlayerId() != null) {
                String pNick = crossModuleMapper.selectPlayerNickname(o.getPlayerId());
                o.setPlayerName((pNick != null && !pNick.isEmpty()) ? pNick : "接单员" + o.getPlayerId());
                o.setPlayerAvatar(crossModuleMapper.selectPlayerAvatar(o.getPlayerId()));
            }
        }
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<Order> detail(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order != null) {
            if (order.getPlayerId() != null) {
                order.setPlayerName(crossModuleMapper.selectPlayerNickname(order.getPlayerId()));
                order.setPlayerAvatar(crossModuleMapper.selectPlayerAvatar(order.getPlayerId()));
            }
            if (order.getUserId() != null) {
                order.setUserNickname(crossModuleMapper.selectUserNickname(order.getUserId()));
                order.setUserAvatar(crossModuleMapper.selectUserAvatar(order.getUserId()));
            }
        }
        return R.ok(order);
    }

    @GetMapping("/{id}/progress")
    public R<List<OrderProgress>> progress(@PathVariable Long id) {
        return R.ok(orderProgressService.list(new LambdaQueryWrapper<OrderProgress>()
                .eq(OrderProgress::getOrderId, id).orderByAsc(OrderProgress::getCreatedAt)));
    }

    @PostMapping("/{id}/assign/{playerId}")
    public R<Void> assign(@PathVariable Long id, @PathVariable Long playerId) {
        orderService.assignOrder(id, playerId, "CS", SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{id}/refund")
    public R<Void> refund(@PathVariable Long id) {
        orderService.csCancelOrder(id, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        orderService.manualConfirmOrder(id, SecurityUtils.getUserId(), "CS");
        return R.ok();
    }

    /** 客服查看用户消费榜单（复用统计 Mapper） */
    @GetMapping("/spending-rank")
    public R<java.util.List<java.util.Map<String, Object>>> spendingRank(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return R.ok(statsMapper.userSpendingRank(Math.min(limit, 200)));
    }
}
