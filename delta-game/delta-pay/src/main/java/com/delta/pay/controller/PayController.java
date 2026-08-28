package com.delta.pay.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.pay.config.WxPayConfiguration;
import com.delta.pay.entity.Payment;
import com.delta.pay.entity.Transaction;
import com.delta.pay.service.PaymentService;
import com.delta.pay.service.TransactionService;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.user.entity.User;
import com.delta.user.service.UserService;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.H5Info;
import com.wechat.pay.java.service.payments.h5.model.PrepayResponse;
import com.wechat.pay.java.service.payments.h5.model.SceneInfo;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.Transaction.TradeStateEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {
    private final PaymentService paymentService;
    private final TransactionService transactionService;
    private final OrderService orderService;
    private final WxPayConfiguration wxPayConfiguration;
    private final UserService userService;

    @Value("${file.upload.path:/data/upload}")
    private String uploadPath;

    @PostMapping("/wx/{orderId}")
    public R<Map<String, String>> wxPay(@PathVariable Long orderId) {
        WxPayConfiguration.CachedClients clients = wxPayConfiguration.getClients();
        if (!wxPayConfiguration.isEnabled() || clients == null) {
            return R.fail("微信支付未启用，请使用余额支付或联系管理员配置支付证书");
        }

        Long userId = SecurityUtils.getUserId();
        Payment payment = paymentService.createWxPayment(orderId, userId);

        User user = userService.getById(userId);
        Order order = orderService.getById(orderId);
        String description = (order != null && order.getProductName() != null && !order.getProductName().isEmpty())
                ? order.getProductName() : "订单支付";
        try {
            PrepayWithRequestPaymentResponse response = createPrepayOrder(clients.getJsapiService(), clients.getConfig(),
                    payment, user.getOpenid(), description);

            payment.setWxPrepayId(response.getPackageVal().replace("prepay_id=", ""));
            paymentService.updateById(payment);

            Map<String, String> payParams = new HashMap<>();
            payParams.put("timeStamp", response.getTimeStamp());
            payParams.put("nonceStr", response.getNonceStr());
            payParams.put("package", response.getPackageVal());
            payParams.put("signType", response.getSignType());
            payParams.put("paySign", response.getPaySign());
            return R.ok(payParams);
        } catch (ServiceException e) {
            log.error("微信支付V3下单失败, orderId={}, code={}", orderId, e.getErrorCode(), e);
            if ("ORDERPAID".equals(e.getErrorCode())) {
                return R.fail(4010, "该订单已支付，请勿重复支付");
            }
            return R.fail("发起微信支付失败，请稍后重试");
        } catch (Exception e) {
            log.error("微信支付V3下单失败, orderId={}", orderId, e);
            return R.fail("发起微信支付失败，请稍后重试");
        }
    }

    /** 小程序余额充值，充值套餐由后台配置，支付金额只取实际充值金额。 */
    @PostMapping("/recharge/wx/{packageId}")
    public R<Map<String, String>> rechargeWxPay(@PathVariable String packageId) {
        WxPayConfiguration.CachedClients clients = wxPayConfiguration.getClients();
        if (!wxPayConfiguration.isEnabled() || clients == null) {
            return R.fail("微信支付未启用，暂无法充值");
        }
        Long userId = SecurityUtils.getUserId();
        Payment payment = paymentService.createRechargePayment(packageId, userId);
        User user = userService.getById(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            return R.fail("用户信息不完整，无法发起支付");
        }
        try {
            PrepayWithRequestPaymentResponse response = createPrepayOrder(clients.getJsapiService(), clients.getConfig(),
                    payment, user.getOpenid(), "余额充值");
            payment.setWxPrepayId(response.getPackageVal().replace("prepay_id=", ""));
            paymentService.updateById(payment);
            Map<String, String> payParams = new HashMap<>();
            payParams.put("paymentNo", payment.getPaymentNo());
            payParams.put("timeStamp", response.getTimeStamp());
            payParams.put("nonceStr", response.getNonceStr());
            payParams.put("package", response.getPackageVal());
            payParams.put("signType", response.getSignType());
            payParams.put("paySign", response.getPaySign());
            return R.ok(payParams);
        } catch (ServiceException e) {
            log.error("余额充值微信下单失败, packageId={}, code={}", packageId, e.getErrorCode(), e);
            return R.fail("发起余额充值失败，请稍后重试");
        } catch (Exception e) {
            log.error("余额充值微信下单失败, packageId={}", packageId, e);
            return R.fail("发起余额充值失败，请稍后重试");
        }
    }

    /** H5 余额充值，供浏览器端复用同一充值套餐。 */
    @PostMapping("/recharge/h5/{packageId}")
    public R<Map<String, String>> rechargeH5Pay(@PathVariable String packageId, HttpServletRequest httpRequest) {
        WxPayConfiguration.CachedClients clients = wxPayConfiguration.getClients();
        if (!wxPayConfiguration.isEnabled() || clients == null) {
            return R.fail("微信支付未启用，暂无法充值");
        }
        Payment payment = paymentService.createRechargePayment(packageId, SecurityUtils.getUserId());
        try {
            com.wechat.pay.java.service.payments.h5.model.PrepayRequest request =
                    new com.wechat.pay.java.service.payments.h5.model.PrepayRequest();
            request.setAppid(clients.getConfig().appId());
            request.setMchid(clients.getConfig().mchId());
            request.setDescription("余额充值");
            request.setOutTradeNo(payment.getPaymentNo());
            request.setNotifyUrl(clients.getConfig().notifyUrl());
            com.wechat.pay.java.service.payments.h5.model.Amount amount =
                    new com.wechat.pay.java.service.payments.h5.model.Amount();
            amount.setTotal(payment.getAmount().multiply(new BigDecimal("100")).intValue());
            amount.setCurrency("CNY");
            request.setAmount(amount);
            SceneInfo sceneInfo = new SceneInfo();
            sceneInfo.setPayerClientIp(getClientIp(httpRequest));
            H5Info h5Info = new H5Info();
            h5Info.setType(clients.getConfig().h5Type());
            h5Info.setAppName(clients.getConfig().h5AppName());
            sceneInfo.setH5Info(h5Info);
            request.setSceneInfo(sceneInfo);
            PrepayResponse response = clients.getH5Service().prepay(request);
            Map<String, String> result = new HashMap<>();
            result.put("h5Url", response.getH5Url());
            result.put("paymentNo", payment.getPaymentNo());
            return R.ok(result);
        } catch (Exception e) {
            log.error("余额充值 H5 下单失败, packageId={}", packageId, e);
            return R.fail("发起余额充值失败，请稍后重试");
        }
    }

    /**
     * 微信 H5 支付。返回 h5Url，H5 前端跳转到该地址完成支付。
     */
    @PostMapping("/h5/{orderId}")
    public R<Map<String, String>> h5Pay(@PathVariable Long orderId, HttpServletRequest httpRequest) {
        WxPayConfiguration.CachedClients clients = wxPayConfiguration.getClients();
        if (!wxPayConfiguration.isEnabled() || clients == null) {
            return R.fail("微信支付未启用，请使用余额支付或联系管理员配置支付证书");
        }

        Long userId = SecurityUtils.getUserId();
        Payment payment = paymentService.createWxPayment(orderId, userId);
        Order order = orderService.getById(orderId);
        String description = order != null && order.getProductName() != null && !order.getProductName().isBlank()
                ? order.getProductName() : "订单支付";
        try {
            H5Service h5Service = clients.getH5Service();
            com.wechat.pay.java.service.payments.h5.model.PrepayRequest request =
                    new com.wechat.pay.java.service.payments.h5.model.PrepayRequest();
            request.setAppid(clients.getConfig().appId());
            request.setMchid(clients.getConfig().mchId());
            request.setDescription(description);
            request.setOutTradeNo(payment.getPaymentNo());
            request.setNotifyUrl(clients.getConfig().notifyUrl());

            com.wechat.pay.java.service.payments.h5.model.Amount amount =
                    new com.wechat.pay.java.service.payments.h5.model.Amount();
            amount.setTotal(payment.getAmount().multiply(new BigDecimal("100")).intValue());
            amount.setCurrency("CNY");
            request.setAmount(amount);

            SceneInfo sceneInfo = new SceneInfo();
            sceneInfo.setPayerClientIp(getClientIp(httpRequest));
            H5Info h5Info = new H5Info();
            h5Info.setType(clients.getConfig().h5Type());
            h5Info.setAppName(clients.getConfig().h5AppName());
            sceneInfo.setH5Info(h5Info);
            request.setSceneInfo(sceneInfo);

            PrepayResponse response = h5Service.prepay(request);
            Map<String, String> result = new HashMap<>();
            result.put("h5Url", response.getH5Url());
            result.put("paymentNo", payment.getPaymentNo());
            return R.ok(result);
        } catch (ServiceException e) {
            log.error("微信 H5 支付下单失败, orderId={}, code={}", orderId, e.getErrorCode(), e);
            if ("ORDERPAID".equals(e.getErrorCode())) {
                return R.fail(4010, "该订单已支付，请勿重复支付");
            }
            return R.fail("发起微信 H5 支付失败，请稍后重试");
        } catch (Exception e) {
            log.error("微信 H5 支付下单失败, orderId={}", orderId, e);
            return R.fail("发起微信 H5 支付失败，请稍后重试");
        }
    }

    @PostMapping("/balance/{orderId}")
    public R<Payment> balancePay(@PathVariable Long orderId) {
        return R.ok(paymentService.createBalancePayment(orderId, SecurityUtils.getUserId()));
    }

    /** 打手入驻押金支付（金额来自 sys_config: player.deposit_amount），返回支付参数及paymentNo供提交申请时校验 */
    @PostMapping("/player-deposit")
    public R<Map<String, Object>> playerDeposit() {
        WxPayConfiguration.CachedClients clients = wxPayConfiguration.getClients();
        if (!wxPayConfiguration.isEnabled() || clients == null) {
            return R.fail("微信支付未启用，暂无法支付押金");
        }

        Long userId = SecurityUtils.getUserId();
        Payment payment = paymentService.createPlayerDepositPayment(userId);
        User user = userService.getById(userId);
        if (user == null || user.getOpenid() == null) {
            return R.fail("用户信息不完整，无法发起支付");
        }
        try {
            PrepayWithRequestPaymentResponse response = createPrepayOrder(clients.getJsapiService(), clients.getConfig(),
                    payment, user.getOpenid(), "打手入驻押金");
            payment.setWxPrepayId(response.getPackageVal().replace("prepay_id=", ""));
            paymentService.updateById(payment);
            Map<String, Object> result = new HashMap<>();
            result.put("paymentNo", payment.getPaymentNo());
            result.put("timeStamp", response.getTimeStamp());
            result.put("nonceStr", response.getNonceStr());
            result.put("package", response.getPackageVal());
            result.put("signType", response.getSignType());
            result.put("paySign", response.getPaySign());
            return R.ok(result);
        } catch (ServiceException e) {
            log.error("打手押金微信支付下单失败, userId={}, code={}", userId, e.getErrorCode(), e);
            return R.fail("发起微信支付失败，请稍后重试");
        } catch (Exception e) {
            log.error("打手押金微信支付下单失败, userId={}", userId, e);
            return R.fail("发起微信支付失败，请稍后重试");
        }
    }

    @PostMapping("/wx/notify")
    public Map<String, String> wxNotify(HttpServletRequest httpRequest, @RequestBody String jsonData) {
        Map<String, String> response = new HashMap<>();
        try {
            String serial = httpRequest.getHeader("Wechatpay-Serial");
            String nonce = httpRequest.getHeader("Wechatpay-Nonce");
            String timestamp = httpRequest.getHeader("Wechatpay-Timestamp");
            String signature = httpRequest.getHeader("Wechatpay-Signature");

            log.info("收到微信支付V3回调, serial={}", serial);

            paymentService.handleWxPayV3Notify(jsonData, serial, nonce, timestamp, signature);

            response.put("code", "SUCCESS");
            response.put("message", "成功");
        } catch (Exception e) {
            log.error("处理微信支付V3回调失败", e);
            response.put("code", "FAIL");
            response.put("message", "处理失败");
        }
        return response;
    }

    @GetMapping("/transactions")
    public R<Page<Transaction>> transactions(PageQuery query) {
        Long userId = SecurityUtils.getUserId();
        return R.ok(transactionService.page(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Transaction>().eq(Transaction::getUserId, userId)
                        .orderByDesc(Transaction::getCreatedAt)));
    }

    private PrepayWithRequestPaymentResponse createPrepayOrder(JsapiServiceExtension jsapiServiceExtension,
                                                               WxPayConfiguration.RuntimeConfig wxPayConfiguration,
                                                               Payment payment,
                                                               String openid,
                                                               String description) {
        PrepayRequest request = new PrepayRequest();
        request.setAppid(wxPayConfiguration.appId());
        request.setMchid(wxPayConfiguration.mchId());
        request.setDescription(description);
        request.setOutTradeNo(payment.getPaymentNo());
        request.setNotifyUrl(wxPayConfiguration.notifyUrl());

        Amount amount = new Amount();
        amount.setTotal(payment.getAmount().multiply(new BigDecimal("100")).intValue());
        amount.setCurrency("CNY");
        request.setAmount(amount);

        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);

        return jsapiServiceExtension.prepayWithRequestPayment(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/config/upload")
    public R<String> uploadCertificate(@RequestParam("configKey") String configKey,
                                       @RequestParam("file") MultipartFile file) throws Exception {
        if (!"wx.pay.private-key-path".equals(configKey) && !"wx.pay.public-key-path".equals(configKey)) {
            return R.fail("不允许上传该类型的支付文件");
        }
        if (file == null || file.isEmpty()) {
            return R.fail("请选择证书文件");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            return R.fail("证书文件不能超过2MB");
        }
        String originalName = file.getOriginalFilename();
        String lowerName = originalName == null ? "" : originalName.toLowerCase();
        if (!(lowerName.endsWith(".pem") || lowerName.endsWith(".key") || lowerName.endsWith(".txt"))) {
            return R.fail("仅支持 pem、key 或 txt 格式的证书文件");
        }
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path dir = Paths.get(uploadPath, "wxpay", datePath);
        Files.createDirectories(dir);
        String ext = lowerName.substring(lowerName.lastIndexOf('.'));
        Path target = dir.resolve(UUID.randomUUID().toString().replace("-", "") + ext);
        file.transferTo(target.toFile());
        return R.ok(target.toAbsolutePath().toString());
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp;
    }
}
