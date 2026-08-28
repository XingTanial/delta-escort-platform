package com.delta.user.dto;

import lombok.Data;

@Data
public class WxLoginRequest {
    private String code;
    /** 微信获取手机号的code */
    private String phoneCode;
}
