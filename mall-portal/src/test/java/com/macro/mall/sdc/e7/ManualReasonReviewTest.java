package com.macro.mall.sdc.e7;

import com.macro.mall.portal.ai.reasons.ManualReasonReview;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ManualReasonReviewTest {
    @org.junit.jupiter.api.io.TempDir Path dir;
    private final List<Item> input = List.of(new Item("a", "鞋", "物流问题", "鞋底开胶"), new Item("b", "鞋", "", ""));
    private final List<Classified> valid = List.of(new Classified("a", Label.QUALITY, "鞋底开胶", false), new Classified("b", Label.UNCERTAIN, null, false));

    @Test void retainsDenominatorAndFailureWithoutGuessing() {
        var result = ManualReasonReview.review(input, valid);
        assertEquals(State.OK, result.state()); assertEquals(2, result.denominator());
        var partial = ManualReasonReview.review(input, List.of(valid.get(0), new Classified("b", null, null, true)));
        assertEquals(State.PARTIAL, partial.state()); assertEquals(2, partial.denominator()); assertEquals(1, partial.failedCount());
    }

    @Test void rejectsMissingDuplicateForeignIdAndUnsupportedEvidence() {
        assertThrows(IllegalArgumentException.class, () -> ManualReasonReview.review(input, List.of(valid.get(0))));
        assertThrows(IllegalArgumentException.class, () -> ManualReasonReview.review(input, List.of(valid.get(0), valid.get(0))));
        assertThrows(IllegalArgumentException.class, () -> ManualReasonReview.review(input, List.of(valid.get(0), new Classified("x", null, null, true))));
        assertThrows(IllegalArgumentException.class, () -> ManualReasonReview.review(input, List.of(new Classified("a", Label.QUALITY, "物流问题", false), valid.get(1))));
        assertThrows(IllegalArgumentException.class, () -> ManualReasonReview.review(input, List.of(new Classified("a", null, null, false), valid.get(1))));
    }

    @Test void otherAloneCannotBeRecordedAsOther() {
        var batch = List.of(new Item("a", "鞋", "", "其他"));
        assertThrows(IllegalArgumentException.class, () -> ManualReasonReview.review(batch, List.of(new Classified("a", Label.OTHER, "其他", false))));
        assertEquals(Label.UNCERTAIN, ManualReasonReview.review(batch, List.of(new Classified("a", Label.UNCERTAIN, null, false))).items().get(0).label());
    }

    @Test void readsTeacherFilesAndRejectsMalformedInput() throws Exception {
        var batch = dir.resolve("batch.json"); var review = dir.resolve("review.json");
        Files.writeString(batch, "[{\"anonymousId\":\"a\",\"category\":\"鞋\",\"reason\":\"\",\"description\":\"鞋底开胶\"}]");
        Files.writeString(review, "[{\"anonymousId\":\"a\",\"label\":\"QUALITY\",\"evidence\":\"鞋底开胶\",\"failed\":false}]");
        assertDoesNotThrow(() -> ManualReasonReview.main(new String[]{batch.toString(), review.toString()}));
        assertThrows(Exception.class, () -> ManualReasonReview.main(new String[]{batch.toString(), dir.resolve("missing.json").toString()}));
        Files.writeString(review, "[{\"anonymousId\":\"a\",\"anonymousId\":\"b\",\"label\":\"QUALITY\",\"evidence\":\"鞋底开胶\",\"failed\":false}]");
        assertThrows(Exception.class, () -> ManualReasonReview.main(new String[]{batch.toString(), review.toString()}));
        Files.writeString(review, "[{\"anonymousId\":\"a\",\"label\":\"UNCERTAIN\",\"evidence\":null}]");
        assertThrows(Exception.class, () -> ManualReasonReview.main(new String[]{batch.toString(), review.toString()}));
        Files.writeString(review, "[{\"anonymousId\":\"a\",\"label\":\"UNCERTAIN\",\"evidence\":null,\"failed\":null}]");
        assertThrows(Exception.class, () -> ManualReasonReview.main(new String[]{batch.toString(), review.toString()}));
    }
}
