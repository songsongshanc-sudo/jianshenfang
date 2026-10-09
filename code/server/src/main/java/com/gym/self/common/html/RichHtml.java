package com.gym.self.common.html;

import com.gym.self.common.api.BizException;

public final class RichHtml {

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
        if (value.length() > 20000) {
            throw BizException.badRequest("内容过长");
        }
        String text = value.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
        if (text.isEmpty() && !value.toLowerCase().contains("<img")) {
            return null;
        }
        return value;
    }

    public static String required(String html, String message) {
        String value = clean(html);
        if (value == null) {
            throw BizException.badRequest(message);
        }
        return value;
    }
}
