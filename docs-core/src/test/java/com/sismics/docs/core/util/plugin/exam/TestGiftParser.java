package com.sismics.docs.core.util.plugin.exam;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for Moodle GIFT parser.
 */
public class TestGiftParser {
    @Test
    public void testParseSingleMultipleAndTrueFalse() {
        String gift = "// Sample exam in GIFT format\n"
                + "::Q1:: 台灣最高的山是哪一座？ {\n"
                + "  =玉山 #正確\n"
                + "  ~雪山\n"
                + "  ~阿里山\n"
                + "}\n\n"
                + "::Q2:: 下列哪些是 FormosaBearV Docs 的功能？（複選） {\n"
                + "  ~%50%全文檢索\n"
                + "  ~%50%標籤權限控管\n"
                + "  ~%-50%挖礦程式\n"
                + "}\n\n"
                + "外掛停用後，外掛固定標籤仍不可被刪除。 {TRUE}\n";

        List<GiftParser.GiftQuestion> questions = GiftParser.parse(gift);
        Assert.assertEquals(3, questions.size());

        GiftParser.GiftQuestion q1 = questions.get(0);
        Assert.assertEquals(GiftParser.TYPE_SINGLE_CHOICE, q1.getType());
        Assert.assertEquals("Q1", q1.getTitle());
        Assert.assertEquals(3, q1.getChoices().size());
        Assert.assertEquals(100.0, q1.getChoices().get(0).getWeight(), 0.01);
        Assert.assertEquals(0.0, q1.getChoices().get(1).getWeight(), 0.01);

        GiftParser.GiftQuestion q2 = questions.get(1);
        Assert.assertEquals(GiftParser.TYPE_MULTIPLE_CHOICE, q2.getType());
        Assert.assertEquals(50.0, q2.getChoices().get(0).getWeight(), 0.01);
        Assert.assertEquals(-50.0, q2.getChoices().get(2).getWeight(), 0.01);

        GiftParser.GiftQuestion q3 = questions.get(2);
        Assert.assertEquals(GiftParser.TYPE_TRUE_FALSE, q3.getType());
        Assert.assertEquals(Boolean.TRUE, q3.getCorrectBoolean());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectInvalidGift() {
        GiftParser.parse("Missing braces question without answer");
    }
}
