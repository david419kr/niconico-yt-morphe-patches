package io.github.david419kr.niconicoyt.extension.niconicocomments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
                + "{\"emoji\":{\"shortcuts\":[\":wave:\"],\"image\":{\"thumbnails\":["
                + "{\"url\":\"//emoji-small\",\"width\":16,\"height\":16},"
                + "{\"url\":\"https://emoji-large\",\"width\":48,\"height\":48}]}}}]},"
                + "\"authorBadges\":[{\"liveChatAuthorBadgeRenderer\":{\"customThumbnail\":{}}}]}}}},"
                + "{\"addChatItemAction\":{\"item\":{\"liveChatPaidMessageRenderer\":{"
                + "\"id\":\"paid\",\"message\":{\"simpleText\":\"thank you\"},"
                + "\"bodyBackgroundColor\":4278255360}}}}"
                + "]");

        List<NiconicoComment> comments = YoutubeChatSource.parseComments(actions);

        assertEquals(2, comments.size());
        assertEquals("hello :wave:", comments.get(0).text);
        assertTrue(comments.get(0).membership);
        assertEquals(2, comments.get(0).segments.size());
        assertFalse(comments.get(0).segments.get(0).isEmoji());
        assertTrue(comments.get(0).segments.get(1).isEmoji());
        assertEquals("https://emoji-large", comments.get(0).segments.get(1).imageUrl);
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

    @Test
    public void appliesThreeSecondBasePlusCalculatedReplayCorrection() throws Exception {
        JSONObject zeroCalculatedOffset = new JSONObject("{"
                + "\"videoDetails\":{\"lengthSeconds\":\"30\"},"
                + "\"microformat\":{\"playerMicroformatRenderer\":{"
                + "\"liveBroadcastDetails\":{"
                + "\"startTimestamp\":\"2026-08-04T00:00:00Z\","
                + "\"endTimestamp\":\"2026-08-04T00:00:30Z\"}}}}}");
        JSONObject cappedCalculatedOffset = new JSONObject("{"
                + "\"videoDetails\":{\"lengthSeconds\":\"20\"},"
                + "\"microformat\":{\"playerMicroformatRenderer\":{"
                + "\"liveBroadcastDetails\":{"
                + "\"startTimestamp\":\"2026-08-04T00:00:00Z\","
                + "\"endTimestamp\":\"2026-08-04T00:00:30Z\"}}}}}");

        assertEquals(3_000, YoutubeChatSource.getReplayOffsetMs(zeroCalculatedOffset));
        assertEquals(8_000, YoutubeChatSource.getReplayOffsetMs(cappedCalculatedOffset));
        assertEquals(3_000, YoutubeChatSource.getReplayOffsetMs(null));
    }

    @Test
    public void clampsLivePollingDelayToARealtimeWindow() {
        assertEquals(250, YoutubeChatSource.getLivePollDelayMs(0));
        assertEquals(100, YoutubeChatSource.getLivePollDelayMs(20));
        assertEquals(175, YoutubeChatSource.getLivePollDelayMs(175));
        assertEquals(250, YoutubeChatSource.getLivePollDelayMs(5_000));
    }
}
