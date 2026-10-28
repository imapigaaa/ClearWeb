package com.clearweb.app;

import java.util.Locale;

/**
 * 网络层拦截：广告 / 追踪 / 统计域名直接返回空响应，连请求都不发出去。
 * 站点自身的 CDN（phncdn.com）必须放行，否则缩略图和视频全废。
 */
final class AdBlocker {

    private AdBlocker() {
    }

    /** 广告与追踪域名（后缀匹配，含所有子域） */
    private static final String[] AD_HOSTS = {
            // Pornhub 主广告商
            "trafficjunky.net", "trafficjunky.com",
            "adtng.com", "dzhjmp.com",
            // 常见成人广告联盟
            "exoclick.com", "exosrv.com", "exdynsrv.com", "realsrv.com",
            "juicyads.com", "juicyads.net", "juicyads.mobi",
            "popads.net", "popcash.net", "propush.me", "pushnami.com",
            // Google 广告与统计
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagmanager.com", "googletagservices.com",
            "google-analytics.com", "adservice.google.com",
            // 程序化广告交易平台
            "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net",
            "criteo.com", "criteo.net", "casalemedia.com", "smartadserver.com",
            "adsrvr.org", "3lift.com", "teads.tv", "sharethrough.com",
            "spotxchange.com", "yieldmo.com", "moatads.com",
            // 内容推荐 / 原生广告
            "taboola.com", "outbrain.com", "mgid.com",
            // 统计
            "scorecardresearch.com", "quantserve.com", "histats.com",
            "statcounter.com", "connect.facebook.net"
    };

    /** 站点自身域名，必须留在 WebView 内加载 */
    private static final String[] SITE_HOSTS = {
            "pornhub.com", "pornhub.org", "phncdn.com"
    };

    static boolean isAdHost(String host) {
        return matches(host, AD_HOSTS);
    }

    static boolean isSiteHost(String host) {
        return matches(host, SITE_HOSTS);
    }

    private static boolean matches(String host, String[] list) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String domain : list) {
            if (h.equals(domain) || h.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }
}
