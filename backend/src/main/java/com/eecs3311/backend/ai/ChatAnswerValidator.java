package com.eecs3311.backend.ai;

/** Detects obvious unfinished text; does not claim to verify factual correctness. */
public final class ChatAnswerValidator {
    private ChatAnswerValidator() { }

    public static boolean isUnfinished(String answer) {
        String text = answer.strip();
        if (text.endsWith(":") || text.endsWith("：")) return true;
        String lastLine = text.substring(text.lastIndexOf('\n') + 1).strip();
        if (lastLine.matches("(?:[-*•]+|\\d+[.)]|#{1,6})")) return true;
        return text.matches("(?is).*(?:follow (?:these|the) steps|here are (?:the |some )?(?:steps|instructions)|as follows)[.!]?");
    }
}
