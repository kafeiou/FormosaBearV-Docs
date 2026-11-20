package com.sismics.docs.core.util.plugin.exam;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser for Moodle GIFT format questions supporting Single Choice, Multiple Choice, and True/False.
 */
public class GiftParser {
    public static final String TYPE_SINGLE_CHOICE = "SINGLE_CHOICE";
    public static final String TYPE_MULTIPLE_CHOICE = "MULTIPLE_CHOICE";
    public static final String TYPE_TRUE_FALSE = "TRUE_FALSE";
    public static final int MAX_GIFT_BYTES = 512 * 1024;
    public static final int MAX_QUESTIONS = 500;

    public static class GiftChoice {
        private final String id;
        private final String text;
        private final double weight;

        public GiftChoice(String id, String text, double weight) {
            this.id = id;
            this.text = text;
            this.weight = weight;
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public double getWeight() {
            return weight;
        }
    }

    public static class GiftQuestion {
        private final String id;
        private final String title;
        private final String type;
        private final String prompt;
        private final List<GiftChoice> choices;
        private final Boolean correctBoolean;

        public GiftQuestion(String id, String title, String type, String prompt,
                            List<GiftChoice> choices, Boolean correctBoolean) {
            this.id = id;
            this.title = title;
            this.type = type;
            this.prompt = prompt;
            this.choices = choices;
            this.correctBoolean = correctBoolean;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getType() {
            return type;
        }

        public String getPrompt() {
            return prompt;
        }

        public List<GiftChoice> getChoices() {
            return choices;
        }

        public Boolean getCorrectBoolean() {
            return correctBoolean;
        }

        public JsonObject toJson(boolean includeAnswers) {
            JsonObjectBuilder builder = Json.createObjectBuilder()
                    .add("id", id)
                    .add("title", title == null ? "" : title)
                    .add("type", type)
                    .add("prompt", prompt);

            if (TYPE_TRUE_FALSE.equals(type)) {
                if (includeAnswers && correctBoolean != null) {
                    builder.add("correct_boolean", correctBoolean);
                }
            } else {
                JsonArrayBuilder choicesArr = Json.createArrayBuilder();
                for (GiftChoice c : choices) {
                    JsonObjectBuilder cb = Json.createObjectBuilder()
                            .add("id", c.getId())
                            .add("text", c.getText());
                    if (includeAnswers) {
                        cb.add("weight", c.getWeight());
                    }
                    choicesArr.add(cb);
                }
                builder.add("choices", choicesArr);
            }
            return builder.build();
        }
    }

    public static List<GiftQuestion> parse(String giftText) {
        if (giftText == null || giftText.trim().isEmpty()) {
            throw new IllegalArgumentException("GIFT content is empty");
        }
        if (giftText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_GIFT_BYTES) {
            throw new IllegalArgumentException("GIFT content exceeds maximum size of 512 KB");
        }

        // Normalize line endings and strip // comment lines
        String normalized = giftText.replace("\r\n", "\n").replace("\r", "\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : normalized.split("\n", -1)) {
            if (line.trim().startsWith("//")) {
                continue;
            }
            cleaned.append(line).append('\n');
        }

        // Split into question blocks by one or more blank lines (linear O(n), ReDoS-safe)
        List<String> blocks = splitBlocksByBlankLines(cleaned.toString());
        List<GiftQuestion> questions = new ArrayList<>();
        int qIndex = 1;
        for (String rawBlock : blocks) {
            String block = rawBlock.trim();
            if (block.isEmpty()) {
                continue;
            }
            // Strip optional [format] prefix such as [markdown] or [html]
            if (block.startsWith("[")) {
                int closeIdx = block.indexOf(']');
                if (closeIdx > 0 && closeIdx < 20) {
                    block = block.substring(closeIdx + 1).trim();
                }
            }

            String title = "";
            if (block.startsWith("::")) {
                int endTitleIdx = block.indexOf("::", 2);
                if (endTitleIdx >= 2) {
                    title = unescapeGift(block.substring(2, endTitleIdx).trim());
                    block = block.substring(endTitleIdx + 2).trim();
                }
            }

            int openBrace = findUnescapedChar(block, '{', 0);
            int closeBrace = findMatchingCloseBrace(block, openBrace);
            if (openBrace < 0 || closeBrace < 0) {
                throw new IllegalArgumentException("Question " + qIndex + " is missing a valid { ... } answer block");
            }

            String beforeBrace = block.substring(0, openBrace).trim();
            String answerBody = block.substring(openBrace + 1, closeBrace).trim();
            String afterBrace = block.substring(closeBrace + 1).trim();
            String prompt = afterBrace.isEmpty()
                    ? unescapeGift(beforeBrace)
                    : unescapeGift(beforeBrace + " _____ " + afterBrace);

            if (prompt.isEmpty()) {
                throw new IllegalArgumentException("Question " + qIndex + " has an empty prompt");
            }
            if (answerBody.isEmpty()) {
                throw new IllegalArgumentException("Question " + qIndex + " has an empty answer block (essay questions are not supported)");
            }
            if (answerBody.startsWith("#")) {
                throw new IllegalArgumentException("Question " + qIndex + " uses numerical format which is not supported");
            }

            String qId = "q" + qIndex;
            questions.add(parseAnswerBlock(qId, title, prompt, answerBody, qIndex));
            if (questions.size() > MAX_QUESTIONS) {
                throw new IllegalArgumentException("GIFT content exceeds maximum of " + MAX_QUESTIONS + " questions");
            }
            qIndex++;
        }

        if (questions.isEmpty()) {
            throw new IllegalArgumentException("No valid GIFT questions found");
        }
        return questions;
    }

    private static List<String> splitBlocksByBlankLines(String text) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                if (current.length() > 0) {
                    blocks.add(current.toString());
                    current.setLength(0);
                }
            } else {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        if (current.length() > 0) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    private static Double parseLeadingWeight(String s) {
        if (!s.startsWith("%")) {
            return null;
        }
        int endIdx = s.indexOf('%', 1);
        if (endIdx <= 1 || endIdx > 20) {
            return null;
        }
        String numStr = s.substring(1, endIdx);
        try {
            return Double.parseDouble(numStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stripLeadingWeight(String s) {
        int endIdx = s.indexOf('%', 1);
        return endIdx > 1 ? s.substring(endIdx + 1).trim() : s;
    }

    public static JsonArray toJsonArray(List<GiftQuestion> questions, boolean includeAnswers) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (GiftQuestion q : questions) {
            arr.add(q.toJson(includeAnswers));
        }
        return arr.build();
    }

    private static GiftQuestion parseAnswerBlock(String qId, String title, String prompt, String answerBody, int qIndex) {
        // Check True/False first (e.g., T, TRUE, F, FALSE optionally followed by #feedback)
        String tfToken = stripFeedback(answerBody).trim().toUpperCase(Locale.ROOT);
        if ("T".equals(tfToken) || "TRUE".equals(tfToken)) {
            return new GiftQuestion(qId, title, TYPE_TRUE_FALSE, prompt, List.of(), Boolean.TRUE);
        }
        if ("F".equals(tfToken) || "FALSE".equals(tfToken)) {
            return new GiftQuestion(qId, title, TYPE_TRUE_FALSE, prompt, List.of(), Boolean.FALSE);
        }

        // Parse choices starting with unescaped '=' or '~'
        List<GiftChoice> choices = new ArrayList<>();
        boolean hasExplicitWeight = false;
        int equalCount = 0;

        int i = 0;
        int len = answerBody.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(answerBody.charAt(i))) {
                i++;
            }
            if (i >= len) {
                break;
            }
            char marker = answerBody.charAt(i);
            if (marker == '#') {
                // General feedback #### ... ignore rest
                break;
            }
            if (marker != '=' && marker != '~') {
                throw new IllegalArgumentException("Question " + qIndex + " has unsupported answer syntax near: " + answerBody.substring(i));
            }
            i++;
            int nextMarker = findNextChoiceMarker(answerBody, i);
            String rawChoice = answerBody.substring(i, nextMarker).trim();
            i = nextMarker;

            String choiceBody = stripFeedback(rawChoice).trim();
            double weight;
            Double parsedWeight = parseLeadingWeight(choiceBody);
            if (marker == '=') {
                equalCount++;
                if (parsedWeight != null) {
                    hasExplicitWeight = true;
                    weight = parsedWeight;
                    choiceBody = stripLeadingWeight(choiceBody);
                } else {
                    weight = 100.0;
                }
            } else {
                if (parsedWeight != null) {
                    hasExplicitWeight = true;
                    weight = parsedWeight;
                    choiceBody = stripLeadingWeight(choiceBody);
                } else {
                    weight = 0.0;
                }
            }

            String text = unescapeGift(choiceBody);
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Question " + qIndex + " contains an empty choice");
            }
            String choiceId = "c" + (choices.size() + 1);
            choices.add(new GiftChoice(choiceId, text, weight));
        }

        if (choices.size() < 2) {
            throw new IllegalArgumentException("Question " + qIndex + " must have at least 2 choices");
        }

        String type = (hasExplicitWeight || equalCount > 1) ? TYPE_MULTIPLE_CHOICE : TYPE_SINGLE_CHOICE;
        if (TYPE_SINGLE_CHOICE.equals(type) && equalCount == 0) {
            throw new IllegalArgumentException("Question " + qIndex + " has no correct answer (=)");
        }
        return new GiftQuestion(qId, title, type, prompt, choices, null);
    }

    private static int findUnescapedChar(String s, char target, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == target) {
                return i;
            }
        }
        return -1;
    }

    private static int findMatchingCloseBrace(String s, int openBrace) {
        if (openBrace < 0) {
            return -1;
        }
        for (int i = openBrace + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '}') {
                return i;
            }
        }
        return -1;
    }

    private static int findNextChoiceMarker(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '=' || c == '~') {
                return i;
            }
            if (i + 3 < s.length() && s.startsWith("####", i)) {
                return i;
            }
        }
        return s.length();
    }

    private static String stripFeedback(String s) {
        int hashIdx = findUnescapedChar(s, '#', 0);
        return hashIdx >= 0 ? s.substring(0, hashIdx) : s;
    }

    private static String unescapeGift(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == 'n') {
                    out.append('\n');
                } else {
                    out.append(next);
                }
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString().trim();
    }
}
