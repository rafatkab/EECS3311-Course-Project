package com.eecs3311.backend.ai;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ChatAnswerValidatorTests {
    @Test void rejectsDanglingIntroductionsAndListMarkers() {
        for (String text : new String[] { "To complete Stage 2, follow these steps:", "Here are the steps.",
                "The requirements are as follows", "First implement the GUI.\n2.", "Use these steps:\n-", "The required work is：" })
            assertThat(ChatAnswerValidator.isUnfinished(text)).as(text).isTrue();
    }
    @Test void acceptsCompleteShortAnswersListsAndInlineExplanations() {
        for (String text : new String[] { "Active transport requires energy.", "Follow these steps: implement the GUI, test it, then record a demo.",
                "Steps:\n1. Implement the feature.\n2. Write the report.", "The following steps are required: build, test, and document.",
                "A URL includes a scheme such as https://example.com." })
            assertThat(ChatAnswerValidator.isUnfinished(text)).as(text).isFalse();
    }
}
