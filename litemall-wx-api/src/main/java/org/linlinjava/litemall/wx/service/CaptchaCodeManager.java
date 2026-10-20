package org.linlinjava.litemall.wx.service;

import org.linlinjava.litemall.wx.dto.CaptchaItem;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存系统中的验证码
 */
public class CaptchaCodeManager {
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static ConcurrentHashMap<String, CaptchaItem> captchaCodeCache = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();

    /**
     * 添加到缓存
     *
     * @param phoneNumber 电话号码
     * @param code        验证码
     */
    public static boolean addToCache(String phoneNumber, String code) {


        //已经发过验证码且验证码还未过期
        if (captchaCodeCache.get(phoneNumber) != null) {
            if (captchaCodeCache.get(phoneNumber).getExpireTime().isAfter(LocalDateTime.now())) {
                return false;
            } else {
                //存在但是已过期，删掉
                captchaCodeCache.remove(phoneNumber);
            }
        }

        CaptchaItem captchaItem = new CaptchaItem();
        captchaItem.setPhoneNumber(phoneNumber);
        captchaItem.setCode(code);
        // 有效期为1分钟
        captchaItem.setExpireTime(LocalDateTime.now().plusMinutes(1));

        captchaCodeCache.put(phoneNumber, captchaItem);
        failedAttempts.remove(phoneNumber);

        return true;
    }

    /**
     * 获取缓存的验证码
     *
     * @param phoneNumber 关联的电话号码
     * @return 验证码
     */
    public static String getCachedCaptcha(String phoneNumber) {
        //没有这个电话记录
        CaptchaItem captchaItem = captchaCodeCache.get(phoneNumber);
        if (captchaItem == null)
            return null;

        //有电话记录但是已经过期
        if (captchaItem.getExpireTime().isBefore(LocalDateTime.now())) {
            captchaCodeCache.remove(phoneNumber);
            failedAttempts.remove(phoneNumber);
            return null;
        }

        return captchaItem.getCode();
    }

    /**
     * 校验验证码，并在验证失败时递增错误计数（超过限制时失效验证码），验证成功时立即消费验证码
     *
     * @param phoneNumber 电话号码
     * @param code        验证码
     * @return 校验是否通过
     */
    public static boolean checkCaptcha(String phoneNumber, String code) {
        if (phoneNumber == null || code == null || code.isEmpty()) {
            return false;
        }

        CaptchaItem captchaItem = captchaCodeCache.get(phoneNumber);
        if (captchaItem == null) {
            return false;
        }

        if (captchaItem.getExpireTime().isBefore(LocalDateTime.now())) {
            captchaCodeCache.remove(phoneNumber);
            failedAttempts.remove(phoneNumber);
            return false;
        }

        AtomicInteger failures = failedAttempts.get(phoneNumber);
        if (failures != null && failures.get() >= MAX_FAILED_ATTEMPTS) {
            captchaCodeCache.remove(phoneNumber);
            failedAttempts.remove(phoneNumber);
            return false;
        }

        if (!captchaItem.getCode().equals(code)) {
            int currentFailures = failedAttempts.computeIfAbsent(phoneNumber, k -> new AtomicInteger(0)).incrementAndGet();
            if (currentFailures >= MAX_FAILED_ATTEMPTS) {
                captchaCodeCache.remove(phoneNumber);
                failedAttempts.remove(phoneNumber);
            }
            return false;
        }

        // 验证成功，立即清除，防止重放
        captchaCodeCache.remove(phoneNumber);
        failedAttempts.remove(phoneNumber);
        return true;
    }

    /**
     * 清除缓存中的验证码
     *
     * @param phoneNumber 电话号码
     */
    public static void removeCaptcha(String phoneNumber) {
        if (phoneNumber != null) {
            captchaCodeCache.remove(phoneNumber);
            failedAttempts.remove(phoneNumber);
        }
    }
}
