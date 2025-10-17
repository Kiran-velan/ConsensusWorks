package net.enjoy.springboot.registrationlogin.controller;

import jakarta.servlet.http.HttpSession;
import net.enjoy.springboot.registrationlogin.dto.DiscussionDto;
import net.enjoy.springboot.registrationlogin.dto.QuestionDto;
import net.enjoy.springboot.registrationlogin.session.DiscussionParticipationService;
import net.enjoy.springboot.registrationlogin.session.RoundTimerService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.enjoy.springboot.registrationlogin.entity.*;
import net.enjoy.springboot.registrationlogin.service.*;
import net.enjoy.springboot.registrationlogin.repository.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.format.DateTimeParseException;

import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/discussions")
public class DiscussionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscussionController.class);

    private final DiscussionService discussionService;
    private final UserService userService;
    private final DiscussionRepository discussionRepository;
    private final QuestionOptionService questionOptionService;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final RoundTimerService roundTimerService;

    @Autowired
    public DiscussionController(DiscussionService discussionService, UserService userService,
                                DiscussionRepository discussionRepository, QuestionOptionService questionOptionService,
                                QuestionRepository questionRepository, UserRepository userRepository,
                                RoundTimerService roundTimerService) {
        this.discussionService = discussionService;
        this.userService = userService;
        this.discussionRepository = discussionRepository;
        this.questionOptionService = questionOptionService;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.roundTimerService = roundTimerService;
    }

    @Autowired
    private DiscussionParticipationService participationService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    @PostMapping(value = "/save", consumes = "application/json")
    public ResponseEntity<?> saveDiscussion(@RequestBody @Valid DiscussionDto requestData,
                                            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication is required. Please log in."));
        }

        try {
            LOGGER.info("📩 Incoming Request Data: {}", requestData);

            // 🚨 Enhanced Data Validations
            if (requestData.getDiscussionName() == null || requestData.getDiscussionName().trim().isEmpty()) {
                LOGGER.error("❌ Discussion name is missing.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Discussion name is required."));
            }

            if (requestData.getQuestions() == null || requestData.getQuestions().isEmpty()) {
                LOGGER.error("❌ No questions provided in request data.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "At least one question is required."));
            }

            // ✅ Ensure questions have a valid position
            for (QuestionDto question : requestData.getQuestions()) {
                if (Objects.isNull(question.getPosition()) || question.getPosition() <= 0) {
                    LOGGER.error("❌ Invalid position detected for a question.");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Each question must have a valid position (greater than 0)."));
                }
            }

            // ✅ Sort questions by `position` before saving
            requestData.getQuestions().sort(Comparator.comparing(QuestionDto::getPosition));

            // 🕒 Scheduled Time Validation
            String scheduledTimeStr = requestData.getScheduledTime();
            if (scheduledTimeStr == null || scheduledTimeStr.trim().isEmpty()) {
                LOGGER.error("❌ Scheduled time is missing.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Scheduled time is required."));
            }

            // ✅ Log and Validate the Scheduled Time format
            LOGGER.info("🕒 Received Scheduled Time: '{}'", scheduledTimeStr.trim());
            OffsetDateTime scheduledTime;
            try {
                scheduledTime = OffsetDateTime.parse(scheduledTimeStr.trim(), DateTimeFormatter.ISO_DATE_TIME);

                // Validate that the time is in the future
                if (scheduledTime.isBefore(OffsetDateTime.now())) {
                    LOGGER.error("❌ Scheduled time must be in the future: {}", scheduledTimeStr);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Scheduled time must be in the future."));
                }

                // Store the parsed time in the requestData for later use
                requestData.setScheduledTime(scheduledTime.toString());

            } catch (DateTimeParseException e) {
                LOGGER.error("❌ Invalid Scheduled Time format: {}", scheduledTimeStr);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid Scheduled Time format. Use ISO 8601 format with time zone offset (e.g., 2025-03-25T10:00:00+05:30)"));
            }

            // ✅ User Lookup
            String userEmail = authentication.getName();
            User currentUser = userService.findUserByEmail(userEmail);

            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Logged-in user not found."));
            }

            // ✅ Delegating Logic to Service Layer
            Discussion savedDiscussion = discussionService.saveDiscussion(requestData, currentUser);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Discussion saved successfully!",
                    "discussionId", savedDiscussion.getDiscussionId()
            ));
        } catch (Exception e) {
            LOGGER.error("❌ Error during save: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error saving discussion: " + e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<Discussion>> getAllDiscussions() {
        List<Discussion> discussions = discussionService.getAllDiscussions();
        return ResponseEntity.ok(discussions);
    }

    @GetMapping("/past")
    public ResponseEntity<List<Discussion>> getPastDiscussions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            LOGGER.warn("❌ Authentication failed or user not authenticated.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        String userEmail = authentication.getName();

        List<Discussion> discussions = discussionService.getDiscussionsByUserEmail(userEmail);

        LOGGER.info("✅ Discussions Data Sent to Frontend: {}", discussions);

        return ResponseEntity.ok(discussions);
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateDiscussion(@RequestBody Map<String, String> requestData, HttpSession session) {
        String discussionName = requestData.get("discussionName");
        String uniqueCode = requestData.get("uniqueCode");
        String email = requestData.get("email");
        String password = requestData.get("password");

        User user = userRepository.findByEmail(email);
        // ✅ Check if user exists in DB
        if (user == null) {
            // Email not found → tell frontend to redirect to register
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "valid", false,
                            "error", "No account found with this email. Please register first."
                    ));
        }

        // ✅ Verify password with BCrypt
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "valid", false,
                            "error", "Invalid password. Please try again."
                    ));
        }

        Discussion discussion = discussionService.findDiscussionByNameAndCode(discussionName, uniqueCode);

        if (discussion != null) {

            Long discussionId = discussion.getDiscussionId();

            // 🚫 Reject if user already joined
            if (participationService.hasUserJoined(discussionId, email)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("valid", false, "error", "You cannot rejoin this discussion."));
            }

            // Get current time
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime scheduledTime = discussion.getScheduledTime();
            OffsetDateTime endTime = scheduledTime.plusMinutes(discussion.getDuration());

            // Check discussion timing status
            String status;
            boolean canJoin = false;

            if (now.isBefore(scheduledTime)) {
                status = "NOT_STARTED";
            } else if (now.isBefore(scheduledTime.plusMinutes(1))) {
                // ✅ Add user to map (only allowed in first minute)
                participationService.addUser(discussionId, email);
                // start timer
                roundTimerService.registerDiscussion(discussionId, scheduledTime.toInstant());

                // ✅ Mark this session as allowed for this discussion
                session.setAttribute("canAccessDiscussion_" + discussionId, true);

                status = "ACTIVE";
                canJoin = true;
            } else if (now.isBefore(endTime)) {
                // No entry allowed after 1 min of grace time, session already started
                status = "ACTIVE_NO_ENTRY";
            } else {
                status = "ENDED";
            }

            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "discussionId", discussionId,
                    "status", status,
                    "canJoin", canJoin,
                    "scheduledTime", scheduledTime.toString(),
                    "endTime", endTime.toString()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Invalid Discussion Name or Unique Code."));
        }
    }

    @DeleteMapping("/delete/{discussionName}/{uniqueCode}")
    public ResponseEntity<?> deleteDiscussion(@PathVariable String discussionName, @PathVariable String uniqueCode) {
        try {
            Discussion discussion = discussionRepository.findByDiscussionNameAndUniqueCode(discussionName, uniqueCode);

            if (discussion == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Discussion not found.");
            }

            // ✅ Efficient deletion of options first
            questionRepository.findByDiscussion_DiscussionId(discussion.getDiscussionId())
                    .forEach(question -> questionOptionService.deleteByQuestionId(question.getId()));

            discussionService.deleteDiscussion(discussion.getDiscussionId());
            return ResponseEntity.ok("Discussion deleted successfully.");
        } catch (Exception e) {
            LOGGER.error("❌ Error deleting discussion: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting discussion: " + e.getMessage());
        }
    }

    @GetMapping("/update/{discussionId}")
    public ResponseEntity<DiscussionDto> getDiscussionForUpdate(@PathVariable Long discussionId) {
        LOGGER.info("Fetching discussion for update with ID: {}", discussionId);
        DiscussionDto discussion = discussionService.getDiscussionById(discussionId);

        if (discussion == null) {
            LOGGER.warn("Discussion not found with ID: {}", discussionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null); // Return null for not found
        }

        LOGGER.info("✅ Successfully fetched discussion for update: {}", discussion);
        return ResponseEntity.ok(discussion);
    }

    @PutMapping("/update/{discussionId}")
    public ResponseEntity<Map<String, String>> updateDiscussion(@PathVariable Long discussionId,
                                                                @Valid @RequestBody DiscussionDto discussionDto) {
        LOGGER.info("Updating discussion with ID: {}", discussionId);

        if (discussionDto == null) {
            LOGGER.error("Invalid discussion data received for update.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", "Invalid discussion data."));
        }

        try {
            Discussion updatedDiscussion = discussionService.updateDiscussionById(discussionId, discussionDto);

            if (updatedDiscussion == null) {
                LOGGER.warn("Discussion not found with ID: {}", discussionId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Discussion not found."));
            }

            LOGGER.info("✅ Successfully updated discussion with ID: {}", discussionId);
            return ResponseEntity.ok(Collections.singletonMap("message", "Discussion updated successfully."));
        } catch (ResponseStatusException e) {
            LOGGER.error("❌ Error updating discussion (ID: {}): {}", discussionId, e.getMessage(), e);
            return ResponseEntity.status(e.getStatusCode())
                    .body(Collections.singletonMap("error", e.getReason()));
        } catch (Exception e) {
            LOGGER.error("❌ Unexpected error updating discussion (ID: {}): {}", discussionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "An unexpected error occurred."));
        }
    }

    @GetMapping("/{discussionId}/questions")
    public ResponseEntity<List<QuestionDto>> getQuestionsByDiscussionId(@PathVariable Long discussionId) {
        LOGGER.info("Fetching questions for discussion with ID: {}", discussionId);
        List<QuestionDto> questions = discussionService.getQuestionsByDiscussionId(discussionId);

        if (questions == null || questions.isEmpty()) {
            LOGGER.warn("No questions found for discussion with ID: {}", discussionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }

        LOGGER.info("✅ Successfully fetched questions for discussion with ID: {}", discussionId);
        return ResponseEntity.ok(questions);
    }

    @PostMapping("/discussion/exit")
    @ResponseBody
    public void exitDiscussion(@RequestParam Long discussionId, HttpSession session) {
        session.removeAttribute("canAccessDiscussion_" + discussionId);
    }

}
