package com.schoolbuddy.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "ai.provider",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockLLMClient implements LLMClient {

    @Override
    public String generate(
            String systemPrompt,
            String conversationContext,
            String userMessage,
            String imageBase64,
            String imageMimeType
    ) {

        /*
         * The mock does not understand image contents.
         * It only records that an image was received.
         *
         * IMPORTANT:
         * We still pass the request through the normal learning-mode
         * logic so that image + HINT/SOLVE/etc. can be tested.
         */

        boolean hasImage =
                imageBase64 != null
                        && !imageBase64.isBlank();

        String imageNote = "";

        if (hasImage) {

            String mimeType =
                    imageMimeType != null
                            && !imageMimeType.isBlank()
                            ? imageMimeType
                            : "unknown";

            imageNote = """

                    ---
                    **Mock image status:** Image received successfully
                    (`%s`).

                    The development mock does not analyze the contents
                    of the image. A real multimodal AI provider is required
                    to understand the image.
                    """.formatted(mimeType);
        }

        /*
         * Use the normal mode-selection logic.
         */
        String response = generate(
                systemPrompt,
                conversationContext,
                userMessage
        );

        return response + imageNote;
    }


    @Override
    public String generate(
            String systemPrompt,
            String conversationContext,
            String userMessage
    ) {

        int grade = extractGrade(systemPrompt);

        /*
         * ============================================================
         * HINT
         * ============================================================
         */

        if (systemPrompt.contains("Learning mode: HINT")) {

            return generateHintResponse(
                    grade,
                    conversationContext,
                    userMessage
            );
        }


        /*
         * ============================================================
         * SIMPLIFY
         * ============================================================
         */

        if (systemPrompt.contains("Learning mode: SIMPLIFY")) {

            return generateSimplifyResponse(
                    grade,
                    conversationContext,
                    userMessage
            );
        }


        /*
         * ============================================================
         * PRACTICE
         * ============================================================
         */

        if (systemPrompt.contains("Learning mode: PRACTICE")) {

            return generatePracticeResponse(
                    grade,
                    conversationContext,
                    userMessage
            );
        }


        /*
         * ============================================================
         * QUIZ
         * ============================================================
         */

        if (systemPrompt.contains("Learning mode: QUIZ")) {

            return generateQuizResponse(
                    grade,
                    conversationContext,
                    userMessage
            );
        }


        /*
         * ============================================================
         * SOLVE
         * ============================================================
         */

        if (systemPrompt.contains("Learning mode: SOLVE")) {

            return generateSolveResponse(
                    grade,
                    conversationContext,
                    userMessage
            );
        }


        /*
         * ============================================================
         * DEFAULT = EXPLAIN
         * ============================================================
         */

        return generateExplainResponse(
                grade,
                conversationContext,
                userMessage
        );
    }


    /*
     * ============================================================
     * EXTRACT GRADE
     * ============================================================
     */

    private int extractGrade(String systemPrompt) {

        String marker = "Grade: ";

        int start = systemPrompt.indexOf(marker);

        if (start == -1) {
            return 8;
        }

        start += marker.length();

        int end = systemPrompt.indexOf("\n", start);

        if (end == -1) {
            end = systemPrompt.length();
        }

        try {

            return Integer.parseInt(
                    systemPrompt
                            .substring(start, end)
                            .trim()
            );

        } catch (NumberFormatException exception) {

            return 8;
        }
    }


    /*
     * ============================================================
     * EXPLAIN MODE
     * ============================================================
     */

    private String generateExplainResponse(
            int grade,
            String conversationContext,
            String question
    ) {

        boolean hasPreviousConversation =
                conversationContext != null
                        && !conversationContext.equals(
                        "No previous conversation history."
                );

        if (hasPreviousConversation
                && question.toLowerCase().contains("more simply")) {

            return """
                    Sure! Let's explain the previous idea more simply.

                    The sky looks blue because sunlight contains many
                    different colors. When sunlight enters Earth's atmosphere,
                    the blue part of the light gets scattered more strongly.

                    So, when we look across the sky, more scattered blue light
                    reaches our eyes.

                    In simple words:

                    Sunlight
                    →
                    blue light gets scattered
                    →
                    our eyes see more blue
                    →
                    the sky looks blue.

                    **Key idea:**

                    The atmosphere scatters blue light more strongly than
                    most of the other visible colors.
                    """;
        }


        if (grade <= 3) {

            return """
                    Let's learn this step by step!

                    Your question:

                    "%s"

                    **SchoolBuddy's explanation:**

                    This is a concept that we can understand by breaking it
                    into small and simple parts.

                    **Tip:**

                    Try to connect the idea with something you see in
                    everyday life.
                    """.formatted(question);
        }


        if (grade <= 6) {

            return """
                    Let's understand it step by step.

                    **Question:**

                    "%s"

                    **Explanation:**

                    We can understand this concept by breaking it into
                    smaller ideas and connecting them together.

                    The key idea is to understand not only WHAT happens,
                    but also WHY it happens.

                    **Remember:**

                    Understanding the concept is more important than
                    memorizing the answer.
                    """.formatted(question);
        }


        if (grade <= 8) {

            return """
                    **Grade %d Explanation**

                    **Question:**

                    "%s"

                    Let's break it down.

                    1. First, identify the main concept involved.

                    2. Understand the process or relationship behind it.

                    3. Connect the concept with a real-world example.

                    4. Use the idea to answer the original question.

                    **Key idea:**

                    A good solution should explain both the result and
                    the reasoning behind it.
                    """.formatted(
                    grade,
                    question
            );
        }


        return """
                **Grade %d Explanation**

                **Question:**

                "%s"

                Let's approach this systematically.

                1. Identify the underlying concept.

                2. Determine the relevant principles or rules.

                3. Apply those principles step by step.

                4. Verify that the conclusion follows logically.

                **Key takeaway:**

                At this level, focus on understanding the underlying
                principle rather than memorizing an isolated answer.
                """.formatted(
                grade,
                question
        );
    }


    /*
     * ============================================================
     * HINT MODE
     * ============================================================
     */

    private String generateHintResponse(
            int grade,
            String conversationContext,
            String question
    ) {

        return """
                **Hint — Grade %d**

                **Question:**

                "%s"

                Don't look for the final answer yet.

                Start by identifying the main concept involved.

                Then ask yourself:

                **What rule, formula, definition, or idea do I already
                know that could help me?**

                Try the first step yourself.

                SchoolBuddy can help with the next step after you try.

                **Remember:** A hint should help you think, not give away
                the complete answer.
                """.formatted(
                grade,
                question
        );
    }


    /*
     * ============================================================
     * SIMPLIFY MODE
     * ============================================================
     */

    private String generateSimplifyResponse(
            int grade,
            String conversationContext,
            String question
    ) {

        return """
                **Simplified Explanation**

                **Your question:**

                "%s"

                Let's make the idea easier.

                Think about it as a simple cause-and-effect relationship:

                **Something happens**
                →
                **it produces an effect**
                →
                **we observe the result.**

                Once this basic idea is clear, we can add the more
                advanced details.

                **Remember:** Understanding the simple idea first makes
                the harder explanation easier to understand.
                """.formatted(question);
    }


    /*
     * ============================================================
     * PRACTICE MODE
     * ============================================================
     */

    private String generatePracticeResponse(
            int grade,
            String conversationContext,
            String question
    ) {

        return """
                **Practice Mode — Grade %d**

                **Based on:**

                "%s"

                Try these questions:

                1. What is the main idea behind this concept?

                2. Can you explain it in your own words?

                3. Can you give one real-world example?

                4. What would happen if one important part of the
                   process changed?

                **Challenge:**

                Try answering without looking at your notes.
                """.formatted(
                grade,
                question
        );
    }


    /*
     * ============================================================
     * QUIZ MODE
     * ============================================================
     */

    private String generateQuizResponse(
            int grade,
            String conversationContext,
            String question
    ) {

        String answer = question == null
                ? ""
                : question.trim().toLowerCase();

        boolean hasPreviousConversation =
                conversationContext != null
                        && !conversationContext.isBlank()
                        && !conversationContext.equals(
                        "No previous conversation history."
                );

        /*
         * ============================================================
         * DETERMINE QUIZ STATE
         * ============================================================
         *
         * We use the previous conversation to determine whether the
         * learner is:
         *
         * 1. Answering the FIRST question
         * 2. Answering the SECOND question
         * 3. Already completed the equation
         */

        boolean firstStepAlreadyCompleted =
                hasPreviousConversation
                        && (
                        conversationContext.contains("3x = 15")
                                || conversationContext.contains("Now we have isolated 3x")
                                || conversationContext.contains("What should you do to isolate x?")
                );

        boolean quizAlreadyCompleted =
                hasPreviousConversation
                        && (
                        conversationContext.contains("x = 5")
                                && conversationContext.contains("Verification")
                );


        /*
         * ============================================================
         * FIRST QUIZ TURN
         * ============================================================
         */

        if (!hasPreviousConversation) {

            return """
                Quiz Mode — Grade %d

                Look at the equation:

                3x + 5 = 20

                Question:

                What should you do FIRST to begin isolating 3x?

                Think about the operation attached to 3x.

                Your turn!
                """.formatted(grade);
        }


        /*
         * ============================================================
         * QUIZ ALREADY COMPLETED
         * ============================================================
         */

        if (quizAlreadyCompleted) {

            return """
                Quiz Complete — Grade %d

                You successfully solved:

                3x + 5 = 20

                Final answer:

                x = 5

                Verification:

                3(5) + 5 = 20
                15 + 5 = 20 ✓

                Excellent work!

                Would you like another equation to practice?
                """.formatted(grade);
        }


        /*
         * ============================================================
         * ANSWERING FIRST QUESTION
         * ============================================================
         */

        if (!firstStepAlreadyCompleted) {

            /*
             * --------------------------------------------------------
             * Correct first step
             * --------------------------------------------------------
             */

            boolean subtractFive =
                    answer.contains("subtract 5")
                            || answer.contains("subtract five")
                            || answer.contains("minus 5")
                            || answer.contains("minus five")
                            || answer.contains("subtraction")
                            || answer.contains("take away 5")
                            || answer.contains("take away five")
                            || answer.contains("subtract 5 from both sides")
                            || answer.contains("subtract five from both sides");


            /*
             * --------------------------------------------------------
             * Wrong: divide by 3 too early
             * --------------------------------------------------------
             */

            boolean divideThree =
                    answer.contains("divide by 3")
                            || answer.contains("divide 3")
                            || answer.contains("divide both sides by 3")
                            || answer.contains("dividing by 3")
                            || answer.contains("division by 3");


            /*
             * --------------------------------------------------------
             * Wrong: multiply by 5
             * --------------------------------------------------------
             */

            boolean multiplyFive =
                    answer.contains("multiply 5")
                            || answer.contains("multiply by 5")
                            || answer.contains("multiply five")
                            || answer.contains("times 5")
                            || answer.contains("multiplication");


            /*
             * --------------------------------------------------------
             * WRONG: DIVIDE BY 3 TOO EARLY
             * --------------------------------------------------------
             */

            if (divideThree) {

                return """
                    Quiz Feedback — Grade %d

                    Not quite.

                    You identified an operation involving the 3,
                    but dividing by 3 is not the first step.

                    Look at the equation:

                    3x + 5 = 20

                    The +5 is still attached to 3x.

                    Before dividing by 3, we need to remove the +5.

                    The inverse operation of addition is subtraction.

                    So first:

                    3x + 5 - 5 = 20 - 5

                    Therefore:

                    3x = 15

                    Now we have isolated 3x.

                    Next Question:

                    What should you do to isolate x?

                    Your turn!
                    """.formatted(grade);
            }


            /*
             * --------------------------------------------------------
             * WRONG: MULTIPLY BY 5
             * --------------------------------------------------------
             */

            if (multiplyFive) {

                return """
                    Quiz Feedback — Grade %d

                    Not quite.

                    Look at:

                    3x + 5 = 20

                    The +5 is being added to 3x.

                    Think about inverse operations.

                    What operation would undo addition?

                    Hint:

                    Addition and subtraction are inverse operations.

                    Try answering again in your own words.

                    Your turn!
                    """.formatted(grade);
            }


            /*
             * --------------------------------------------------------
             * CORRECT FIRST STEP
             * --------------------------------------------------------
             */

            if (subtractFive) {

                return """
                    Quiz Feedback — Grade %d

                    Great job! 🎯

                    You identified the correct inverse operation.

                    Starting with:

                    3x + 5 = 20

                    We subtract 5 from both sides:

                    3x + 5 - 5 = 20 - 5

                    So:

                    3x = 15

                    Now we have isolated 3x.

                    Next Question:

                    What should you do to isolate x?

                    Your turn!
                    """.formatted(grade);
            }


            /*
             * --------------------------------------------------------
             * DEFAULT FIRST-STEP WRONG ANSWER
             * --------------------------------------------------------
             */

            return """
                Quiz Feedback — Grade %d

                Not quite yet.

                Look carefully at:

                3x + 5 = 20

                Ask yourself:

                What operation is being performed on 3x?

                Then think about the inverse operation that would
                undo that operation.

                Hint:

                Focus on the +5 before dealing with the 3.

                Try again in your own words.

                Your turn!
                """.formatted(grade);
        }


        /*
         * ============================================================
         * ANSWERING SECOND QUESTION
         * ============================================================
         */

        boolean divideByThree =
                answer.contains("divide by 3")
                        || answer.contains("divide 3")
                        || answer.contains("divide both sides by 3")
                        || answer.contains("dividing by 3")
                        || answer.contains("division by 3")
                        || answer.contains("divide both sides");


        /*
         * ============================================================
         * CORRECT SECOND STEP
         * ============================================================
         */

        if (divideByThree) {

            return """
                Quiz Feedback — Grade %d

                Excellent! 🎉

                We reached:

                3x = 15

                Now divide both sides by 3:

                3x / 3 = 15 / 3

                Therefore:

                x = 5

                Final Answer:

                x = 5

                Verification:

                3(5) + 5 = 20

                15 + 5 = 20 ✓

                Great work! You solved the equation correctly.
                """.formatted(grade);
        }


        /*
         * ============================================================
         * WRONG SECOND STEP
         * ============================================================
         */

        return """
            Quiz Feedback — Grade %d

            You're very close.

            We already simplified the equation to:

            3x = 15

            Now think about what is being multiplied by x.

            The 3 is multiplied by x.

            What inverse operation will undo multiplication by 3?

            Hint:

            Multiplication and division are inverse operations.

            Try again in your own words.

            Your turn!
            """.formatted(grade);
    }



    /*
     * ============================================================
     * SOLVE MODE
     * ============================================================
     */

    private String generateSolveResponse(
            int grade,
            String conversationContext,
            String question
    ) {

        return """
                **Solve Mode — Grade %d**

                **Problem:**

                "%s"

                **Solution approach:**

                **Step 1:** Identify what the question is asking.

                **Step 2:** Identify the information or concepts that
                are relevant.

                **Step 3:** Apply the appropriate rule, formula, or
                reasoning.

                **Step 4:** Check the result.

                The development mock does not calculate the actual answer.
                A real AI model will provide the complete solution.
                """.formatted(
                grade,
                question
        );
    }
}