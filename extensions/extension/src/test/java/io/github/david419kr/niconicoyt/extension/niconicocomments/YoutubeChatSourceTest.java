package io.github.david419kr.niconicoyt.extension.niconicocomments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class YoutubeChatSourceTest {
    @Test
    public void parsesWatchPageConfigAndBalancedJson() throws Exception {
        String html = "<script>ytcfg.set({\"INNERTUBE_API_KEY\":\"key\"});"
                + "ytcfg.set({\"INNERTUBE_CONTEXT\":{\"client\":{"
                + "\"clientName\":\"WEB\",\"clientVersion\":\"1.2.3\"}}});</script>"
                + "<script>var ytInitialData = {\"text\":\"escaped \\\" } brace\"};</script>";

        JSONObject config = YoutubeChatSource.parseYoutubeConfig(html);
        JSONObject initialData = YoutubeChatSource.parseMarkedObject(
                html,
                "var ytInitialData ="
        );

        assertEquals("key", config.getString("INNERTUBE_API_KEY"));
        assertEquals(
                "1.2.3",
                config.getJSONObject("INNERTUBE_CONTEXT")
                        .getJSONObject("client")
                        .getString("clientVersion")
        );
        assertNotNull(initialData);
        assertEquals("escaped \" } brace", initialData.getString("text"));
    }

    @Test
    public void findsContinuationAndTimeoutRecursively() throws Exception {
        JSONObject value = new JSONObject(
                "{\"outer\":[{\"invalidationContinuationData\":{"
                        + "\"continuation\":\"next\",\"timeoutMs\":2345}}]}"
        );

        YoutubeChatSource.Continuation continuation = YoutubeChatSource.findContinuation(
                value,
                "timedContinuationData",
                "invalidationContinuationData"
        );

        assertNotNull(continuation);
        assertEquals("next", continuation.value);
        assertEquals(2345, continuation.timeoutMs);
    }

    @Test
    public void parsesLiveTextEmojiMembershipAndPaidColor() throws Exception {
        JSONArray actions = new JSONArray("["
                + "{\"addChatItemAction\":{\"item\":{\"liveChatTextMessageRenderer\":{"
                + "\"id\":\"member\",\"message\":{\"runs\":[{\"text\":\"hello \"},"
                + "{\"emoji\":{\"shortcuts\":[\":wave:\"]}}]},"
                + "\"authorBadges\":[{\"liveChatAuthorBadgeRenderer\":{\"customThumbnail\":{}}}]}}}},"
                + "{\"addChatItemAction\":{\"item\":{\"liveChatPaidMessageRenderer\":{"
                + "\"id\":\"paid\",\"message\":{\"simpleText\":\"thank you\"},"
                + "\"bodyBackgroundColor\":4278255360}}}}"
                + "]");

        List<NiconicoComment> comments = YoutubeChatSource.parseComments(actions);

        assertEquals(2, comments.size());
        assertEquals("hello :wave:", comments.get(0).text);
        assertTrue(comments.get(0).membership);
        assertEquals(-1, comments.get(0).videoOffsetMs);
        assertEquals("thank you", comments.get(1).text);
        assertEquals(0x6100ff00, comments.get(1).backgroundColor);
    }

    @Test
    public void parsesReplayOffsetAndNestedChatAction() throws Exception {
        JSONArray actions = new JSONArray("[{\"replayChatItemAction\":{"
                + "\"videoOffsetTimeMsec\":\"15000\",\"actions\":["
                + "{\"addChatItemAction\":{\"item\":{\"liveChatTextMessageRenderer\":{"
                + "\"id\":\"replay\",\"message\":{\"simpleText\":\"at 13 seconds\"}}}}}]}}]");

        YoutubeChatSource.ReplayParseResult result =
                YoutubeChatSource.parseReplayComments(actions, 2_000);

        assertEquals(13_000, result.maxVideoOffsetMs);
        assertEquals(1, result.comments.size());
        assertEquals(13_000, result.comments.get(0).videoOffsetMs);
        assertEquals("at 13 seconds", result.comments.get(0).text);
    }
}
