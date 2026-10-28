package com.clearweb.app;

import org.json.JSONObject;

/**
 * DOM 层清理：注入 CSS + JS，删除广告节点、Cookie 横幅、PWA 安装条、
 * 会员升级按钮、以及头部那排"付费入口"（Live Cams / AI Sex Slave 等）。
 * <p>
 * 选择器全部基于对真实移动版页面结构的抓取结果：
 * 广告位统一是 &lt;ins class='adsbytrafficjunky' data-spot-id='...'&gt;，
 * 容器为 .watchpageAd / .middleVideoAdContainer / .ad-tabSplit，以及混淆类名 nuctmx*。
 */
final class PageCleaner {

    private PageCleaner() {
    }

    private static final String CSS =
            "ins.adsbytrafficjunky,ins[data-spot-id],ins[class*='adsby'],ins[id^='ad-']"
                    + "{display:none!important;height:0!important;margin:0!important;padding:0!important}"
                    + ".watchpageAd,.middleVideoAdContainer,.ad-tabSplit,[class*='watchpageAd']"
                    + "{display:none!important}"
                    + ".cookieBanner,.cookieBannerWrapper,.globalCookieBanner,.cookieBannerContent,#cookieBanner"
                    + "{display:none!important}"
                    + ".js-closePWABanner,.pwaBanner,#pwaBanner{display:none!important}"
                    + ".pornhubXPayment__upgradeBtn,.adsRemoveButton,.js-phpAdsRemoveButton"
                    + ",.adsRemoveButtonWrapper,.tjLinksWrapper"
                    + "{display:none!important}"
                    + ".gtm-event-header-paidtabs,.js-liveCam{display:none!important}"
                    + ".premiumBanner,.js-premiumBanner,[class*='premiumPromo']{display:none!important}";

    /**
     * 页面开始加载时立刻注入：先斩断 window.open（拦弹窗），再尽早插入样式表，
     * 避免广告在样式生效前闪一下。
     */
    static String earlyScript() {
        return "(function(){try{"
                + "if(window.__cwEarly)return;window.__cwEarly=1;"
                + "try{window.open=function(){return null;};}catch(e){}"
                + "var css=" + JSONObject.quote(CSS) + ";"
                + "function add(){var d=document.documentElement;"
                + "if(!d)return false;"
                + "if(document.getElementById('__cw_css'))return true;"
                + "var s=document.createElement('style');s.id='__cw_css';s.type='text/css';"
                + "s.appendChild(document.createTextNode(css));d.appendChild(s);return true;}"
                + "if(!add()){var n=0;var t=setInterval(function(){if(add()||++n>300)clearInterval(t);},15);}"
                + "}catch(e){}})();";
    }

    /**
     * 页面加载完成后执行：删除广告节点、清掉残留空位、把 target=_blank 改成同窗口，
     * 并用 MutationObserver 监听无限滚动新插入的内容，持续清理。
     */
    static String cleanScript() {
        return "(function(){try{"
                + "if(window.__cwClean)return;window.__cwClean=1;"
                + "var ADRE=/trafficjunky|exoclick|exosrv|realsrv|juicyads|doubleclick|googletagmanager"
                + "|googlesyndication|googleadservices|dzhjmp|popads|popcash|propush|adtng|adnxs"
                + "|pubmatic|rubiconproject|criteo|mgid|taboola|outbrain|scorecardresearch/i;"
                + "function rm(el){try{if(el&&el.parentNode)el.parentNode.removeChild(el);}catch(e){}}"
                + "function adClass(c){if(!c)return false;"
                + "return /(^|[\\s_-])(ads?|advert|advertisement|sponsor)([\\s_-]|$)/i.test(c)"
                + "||/Ad[A-Z]/.test(c)||/nuctmx/i.test(c);}"
                + "function boxOf(ins){var el=ins,best=ins;"
                + "for(var i=0;i<6&&el&&el.parentElement;i++){el=el.parentElement;"
                + "var cn=(typeof el.className==='string')?el.className:'';var id=el.id||'';"
                + "if(el.tagName==='LI'){best=el;break;}"
                + "if(adClass(cn)||adClass(id)){best=el;break;}}"
                + "return best;}"
                + "function clean(){var i,els;"
                // 1. 广告位 <ins>：连它的 li / 广告容器一起删，避免留下空白
                + "els=document.querySelectorAll('ins.adsbytrafficjunky,ins[data-spot-id],ins[class*=\"adsby\"]');"
                + "for(i=0;i<els.length;i++){rm(boxOf(els[i]));}"
                // 2. 已知广告容器
                + "els=document.querySelectorAll('.watchpageAd,.middleVideoAdContainer,.ad-tabSplit');"
                + "for(i=0;i<els.length;i++){rm(els[i]);}"
                // 3. 广告 iframe
                + "els=document.querySelectorAll('iframe');"
                + "for(i=0;i<els.length;i++){var src=els[i].src||'';if(ADRE.test(src))rm(els[i]);}"
                // 4. Cookie 横幅 / PWA 安装条 / 升级按钮 / 头部付费入口
                + "els=document.querySelectorAll('.cookieBanner,.cookieBannerWrapper,.globalCookieBanner,"
                + ".cookieBannerContent,#cookieBanner,.js-closePWABanner,.pwaBanner,"
                + ".pornhubXPayment__upgradeBtn,.adsRemoveButton,.js-phpAdsRemoveButton,"
                + ".adsRemoveButtonWrapper,.tjLinksWrapper,"
                + ".gtm-event-header-paidtabs,.js-liveCam,.premiumBanner,.js-premiumBanner');"
                + "for(i=0;i<els.length;i++){rm(els[i]);}"
                // 5. 新窗口链接改为当前窗口，避免点击无反应
                + "els=document.querySelectorAll('a[target=\"_blank\"]');"
                + "for(i=0;i<els.length;i++){els[i].target='_self';}"
                + "}"
                + "if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',clean);}"
                + "clean();window.addEventListener('load',clean);"
                + "var t=null;"
                + "try{new MutationObserver(function(){if(t)return;"
                + "t=setTimeout(function(){t=null;clean();},250);})"
                + ".observe(document.documentElement,{childList:true,subtree:true});}catch(e){}"
                + "setTimeout(clean,600);setTimeout(clean,2000);setTimeout(clean,5000);"
                + "}catch(e){}})();";
    }
}
