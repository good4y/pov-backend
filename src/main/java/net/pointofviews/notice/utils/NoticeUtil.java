package net.pointofviews.notice.utils;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
public class NoticeUtil {
    private NoticeUtil() {}

    public static String replaceTemplateVariables(String noticeTemplate, Map<String, String> templateVariables) {
        String result = noticeTemplate;
            for (Map.Entry<String, String> entry : templateVariables.entrySet()) {
                String value = entry.getValue();

                // null인 경우 빈 문자열로 대체
                result = result.replace("{" + entry.getKey() + "}", Objects.requireNonNullElse(value, ""));
            }
            return result;
    }
}
