package org.linlinjava.litemall.wx.service;

import org.linlinjava.litemall.wx.dto.CaptchaItem;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存系统中的验证码
 */
public class CaptchaCodeManager {
    private static ConcurrentHashMap<String, CaptchaItem> captchaCodeCache = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Integer> captchaAttempts = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 3;

    /**
     * 添加到缓存
     *
     * @param phoneNumber 电话号码
     * @param code        验证码
     */
    public static boolean addToCache(String phoneNumber, String code) {
        if (phoneNumber == null || code == null) {
            return false;
        }

        //已经发过验证码且验证码还未过期
        CaptchaItem existing = captchaCodeCache.get(phoneNumber);
        if (existing != null) {
            if (existing.getExpireTime().isAfter(LocalDateTime.now())) {
                return false;
            } else {
                //存在但是已过期，删掉
                captchaCodeCache.remove(phoneNumber);
                captchaAttempts.remove(phoneNumber);
            }
        }

        CaptchaItem captchaItem = new CaptchaItem();
        captchaItem.setPhoneNumber(phoneNumber);
        captchaItem.setCode(code);
        // 有效期为1分钟
        captchaItem.setExpireTime(LocalDateTime.now().plusMinutes(1));

        captchaCodeCache.put(phoneNumber, captchaItem);
        captchaAttempts.remove(phoneNumber);

        return true;
    }

    /**
     * 获取缓存的验证码
     *
     * @param phoneNumber 关联的电话号码
     * @return 验证码
     */
    public static String getCachedCaptcha(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        CaptchaItem item = captchaCodeCache.get(phoneNumber);
        //没有这个电话记录
        if (item == null) {
            return null;
        }

        //有电话记录但是已经过期
        if (item.getExpireTime().isBefore(LocalDateTime.now())) {
            captchaCodeCache.remove(phoneNumber);
            captchaAttempts.remove(phoneNumber);
            return null;
        }

        if (item.getCode() == null) {
            return null;
        }

        int attempts = captchaAttempts.merge(phoneNumber, 1, Integer::sum);
        if (attempts > MAX_ATTEMPTS) {
            item.setCode(null);
            return null;
        }

        return item.getCode();
    }

    /**
     * 清除缓存的验证码
     *
     * @param phoneNumber 关联的电话号码
     */
    public static void removeCaptcha(String phoneNumber) {
        if (phoneNumber != null) {
            captchaCodeCache.remove(phoneNumber);
            captchaAttempts.remove(phoneNumber);
        }
    }
}
