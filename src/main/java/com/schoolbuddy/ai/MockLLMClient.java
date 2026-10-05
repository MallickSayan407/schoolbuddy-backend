package com.schoolbuddy.ai;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
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
         * A quiz-start request must always begin a fresh quiz.
         * Do not let unrelated earlier conversation determine the state.
         */
        boolean startsQuiz =
                answer.contains("start a quiz")
                        || answer.contains("start quiz")
                        || answer.contains("begin a quiz")
                        || answer.contains("begin quiz")
                        || answer.equals("quiz")
                        || answer.contains("quiz about")
                        || answer.contains("quiz on");

        if (startsQuiz) {
            return generateQuizStartResponse(grade, answer);
        }

        /*
         * After the first question, inspect the current quiz history.
         * The mock quiz intentionally remains small and deterministic.
         */
        if (hasPreviousConversation
                && conversationContext.contains("Plant Quiz")) {

            boolean correct =
                    answer.contains("photosynthesis")
                            || answer.contains("make food")
                            || answer.contains("makes food")
                            || answer.contains("food for itself");

            if (correct) {
                return """
                    **Plant Quiz — Grade %d**

                    Correct! 🎉

                    Plants use sunlight to help make their own food
                    through a process called **photosynthesis**.

                    **Next Question:**

                    What gas do plants take in from the air during
                    photosynthesis?

                    Your turn!
                    """.formatted(grade);
            }

            return """
                **Plant Quiz Feedback — Grade %d**

                Not quite. Think about the process plants use to make
                their own food.

                **Hint:** It starts with the letter **P**.

                Try again!
                """.formatted(grade);
        }

        if (hasPreviousConversation
                && conversationContext.contains("Math Quiz")) {

            boolean correct =
                    answer.contains("35")
                            || answer.contains("thirty-five")
                            || answer.contains("thirty five");

            if (correct) {
                return """
                    **Math Quiz — Grade %d**

                    Correct! 🎉

                    5 × 7 = 35.

                    **Next Question:**

                    What is 8 × 6?

                    Your turn!
                    """.formatted(grade);
            }

            return """
                **Math Quiz Feedback — Grade %d**

                Not quite.

                Try 5 groups of 7 and think about repeated addition.

                Your turn!
                """.formatted(grade);
        }

        if (hasPreviousConversation
                && conversationContext.contains("English Quiz")) {

            boolean correct =
                    answer.contains("noun")
                            || answer.contains("person")
                            || answer.contains("place")
                            || answer.contains("thing");

            if (correct) {
                return """
                    **English Quiz — Grade %d**

                    Correct! 🎉

                    A noun names a person, place, animal, or thing.

                    **Next Question:**

                    What is the verb in this sentence?

                    **The bird flies in the sky.**

                    Your turn!
                    """.formatted(grade);
            }

            return """
                **English Quiz Feedback — Grade %d**

                Not quite.

                Look for the word that tells us what the subject is doing.

                Try again!
                """.formatted(grade);
        }

        /*
         * If the user answers without explicitly starting a quiz in this
         * turn and no known quiz state exists, start a fresh generic quiz.
         */
        return generateQuizStartResponse(grade, answer);
    }


    private String generateQuizStartResponse(int grade, String request) {

        if (request.contains("plant")
                || request.contains("science")
                || request.contains("photosynthesis")) {

            return """
                **Plant Quiz — Grade %d**

                **Question 1:**

                Why do plants need sunlight?

                A) To help make their food
                B) To make their roots grow instantly
                C) To make the soil disappear
                D) To make the leaves blue

                Your turn! Choose **A, B, C, or D**.
                """.formatted(grade);
        }

        if (request.contains("english")
                || request.contains("grammar")
                || request.contains("language")) {

            return """
                **English Quiz — Grade %d**

                **Question 1:**

                Which word is a noun in this sentence?

                **The student reads a book.**

                A) reads
                B) student
                C) the
                D) a

                Your turn! Choose **A, B, C, or D**.
                """.formatted(grade);
        }

        return """
            **Math Quiz — Grade %d**

            **Question 1:**

            What is 5 × 7?

            A) 30
            B) 35
            C) 40
            D) 45

            Your turn! Choose **A, B, C, or D**.
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