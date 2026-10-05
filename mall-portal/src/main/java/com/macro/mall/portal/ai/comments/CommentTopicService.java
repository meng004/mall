package com.macro.mall.portal.ai.comments;

import java.util.Map;

/** 按被讨论的主题给评论打多标签，并保留原文片段。 */
public interface CommentTopicService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }

    Result classify(String text);

    enum Topic { QUALITY, LOGISTICS, AFTER_SALES, OTHER }

    record Result(State state, Map<Topic, String> evidence) {}
}
