package com.gym.self.common.html;

import com.gym.self.common.api.BizException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RichHtml {

    private static final Pattern IMG = Pattern.compile("(?i)<img\\b([^>]*)>");
    private static final String IMG_FIT = "max-width:100%;width:100%;height:auto;display:block;";

    private RichHtml() {
    }

    public static String clean(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String value = html
                .replaceAll("(?i)<script[\\s\\S]*?</script>", "")
                .replaceAll("(?i)<iframe[\\s\\S]*?</iframe>", "")
                .replaceAll("(?i)\\s+on\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "");
        value = fitImages(value);
        if (value.length() > 20000) {
            throw BizException.badRequest("内容过长");
        }
        String text = value.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
        if (text.isEmpty() && !value.toLowerCase().contains("<img")) {
            return null;
        }
        return value;
    }

    /** 展示侧再用一次，兼容历史未约束的大图 */
    public static String forDisplay(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        return fitImages(html);
    }

    private static String fitImages(String html) {
        Matcher matcher = IMG.matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String attrs = matcher.group(1) == null ? "" : matcher.group(1);
            attrs = attrs.replaceAll("(?i)\\s*(width|height)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "");
            attrs = attrs.replaceAll("(?i)\\s*style\\s*=\\s*(\"[^\"]*\"|'[^']*')", "");
            matcher.appendReplacement(out, Matcher.quoteReplacement("<img style=\"" + IMG_FIT + "\"" + attrs + ">"));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String required(String html, String message) {
        String value = clean(html);
        if (value == null) {
            throw BizException.badRequest(message);
        }
        return value;
    }
}
