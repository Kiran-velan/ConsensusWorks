package net.enjoy.springboot.registrationlogin.controller;

import org.springframework.web.bind.annotation.RestController;
import net.enjoy.springboot.registrationlogin.entity.*;
import net.enjoy.springboot.registrationlogin.service.*;
import net.enjoy.springboot.registrationlogin.dto.*;
import org.springframework.web.bind.annotation.RequestParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/session")
public class SessionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionController.class);
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/discussion-details")
    public ResponseEntity<?> getDiscussionDetails(@RequestParam Long discussionId) {
        // Validate input
        if (discussionId == null || discussionId <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "Invalid discussion ID.",
                            "errorCode", "INVALID_DISCUSSION_ID"
                    ));
        }

        try {
            // Fetch discussion details
            Discussion discussion = sessionService.findDiscussionById(discussionId);

            if (discussion == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "error", "Discussion not found.",
                                "errorCode", "DISCUSSION_NOT_FOUND"
                        ));
            }

            // Fetch ALL questions with their options
            List<QuestionDto> questions = sessionService.getQuestionsWithAllOptions(discussionId);

            // Use a more functional approach with better readability
            List<Map<String, Object>> questionsWithOptions = questions.stream()
                    .map(this::mapQuestionToResponseFormat)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "discussionType", discussion.getDiscussionType(),
                    "scheduledTime", discussion.getScheduledTime(),
                    "description", Optional.ofNullable(discussion.getDescription()).orElse("No description available."),
                    "questions", questionsWithOptions
            ));
        } catch (Exception e) {
            LOGGER.error("Error fetching discussion details for discussionId: {}", discussionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "An unexpected error occurred.",
                            "errorCode", "INTERNAL_SERVER_ERROR"
                    ));
        }
    }

    // Extract mapping logic to a separate method for better readability
    private Map<String, Object> mapQuestionToResponseFormat(QuestionDto question) {
        return Map.of(
                "id", question.getId(),
                "discussionId", question.getDiscussionId(),
                "questionText", question.getQuestionText(),
                "position", question.getPosition(),
                "options", question.getOptions(),
                "persistingOptionsCount", question.getPersistingOptionsCount()
        );
    }

    @GetMapping("/top-options")
    public ResponseEntity<List<String>> getTopOptionsByFitness(@RequestParam("questionId") Long questionId, @RequestParam("roundNumber") int roundNumber) {
        if (questionId == null) {
            return ResponseEntity.badRequest().body(List.of("Missing questionId"));
        }

        List<String> topOptions = sessionService.getTopOptionTextsByFitness(questionId, roundNumber);

        if (topOptions.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of("No options found"));
        }

        return ResponseEntity.ok(topOptions);
    }

    @GetMapping("/answer")
    public ResponseEntity<String> getAnswerForQuestion(@RequestParam("questionId") Long questionId) {
        if (questionId == null) {
            return ResponseEntity.badRequest().body("Missing questionId");
        }

        String answer = sessionService.getAnswerByQuestionId(questionId);

        if (answer == null || answer.equalsIgnoreCase("None")) {
            return ResponseEntity.ok("No answer finalised"); // 200 instead of 404
        }

        return ResponseEntity.ok(answer);
    }


}
