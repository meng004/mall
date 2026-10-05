package com.macro.mall.portal.service.impl;

import java.util.Random;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * 16位优惠码：调用方提供的时间毫秒后8位、4个调用方提供的数字、会员ID后4位。
 * 默认实现仍使用系统时间和每次新建的 Random，不保证全局唯一，也不是安全随机。
 */
public class CouponCodeGenerator {
    private final LongSupplier currentTimeMillis;
    private final IntSupplier randomDigit;

    public CouponCodeGenerator() {
        this(System::currentTimeMillis, () -> new Random().nextInt(10));
    }

    public CouponCodeGenerator(LongSupplier currentTimeMillis, IntSupplier randomDigit) {
        this.currentTimeMillis = currentTimeMillis;
        this.randomDigit = randomDigit;
    }

    public String generate(Long memberId) {
        StringBuilder sb = new StringBuilder();
        String timeMillisStr = Long.toString(currentTimeMillis.getAsLong());
        sb.append(timeMillisStr.substring(timeMillisStr.length() - 8));
        for (int i = 0; i < 4; i++) {
            sb.append(randomDigit.getAsInt());
        }
        String memberIdStr = memberId.toString();
        if (memberIdStr.length() <= 4) {
            sb.append(String.format("%04d", memberId));
        } else {
            sb.append(memberIdStr.substring(memberIdStr.length() - 4));
        }
        return sb.toString();
    }
}
