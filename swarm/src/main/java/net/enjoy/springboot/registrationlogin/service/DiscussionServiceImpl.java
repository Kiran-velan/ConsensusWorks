package net.enjoy.springboot.registrationlogin.service;

import net.enjoy.springboot.registrationlogin.dto.DiscussionDto;
import net.enjoy.springboot.registrationlogin.dto.QuestionDto;
import net.enjoy.springboot.registrationlogin.dto.QuestionOptionDto;
import net.enjoy.springboot.registrationlogin.entity.Discussion;
import net.enjoy.springboot.registrationlogin.entity.Question;
import net.enjoy.springboot.registrationlogin.entity.QuestionOption;
import net.enjoy.springboot.registrationlogin.repository.DiscussionRepository;
import net.enjoy.springboot.registrationlogin.repository.QuestionRepository;
import net.enjoy.springboot.registrationlogin.repository.QuestionOptionRepository;
import net.enjoy.springboot.registrationlogin.repository.UserRepository;

import net.enjoy.springboot.registrationlogin.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Collections;


@Service
public class DiscussionServiceImpl implements DiscussionService {

    private static final Logger LOGGER = Logger.getLogger(DiscussionServiceImpl.class.getName());

    @Autowired
    private DiscussionRepository discussionRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private QuestionOptionService questionOptionService;

    final int ROUND_DURATION_MINUTES = 3;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Discussion saveDiscussion(DiscussionDto discussionDto, User user) {
        Discussion discussion = new Discussion();
        discussion.setDiscussionName(discussionDto.getDiscussionName());
        discussion.setNumberOfParticipants(Optional.ofNullable(discussionDto.getNumberOfParticipants()).orElse(0));
        discussion.setNumberOfQuestions(Optional.ofNullable(discussionDto.getNumberOfQuestions()).orElse(0));
        discussion.setDiscussionType(Optional.ofNullable(discussionDto.getDiscussionType()).orElse("decision-making"));

        // ✅ Handle Description - Default to empty string if null
        discussion.setDescription(Optional.ofNullable(discussionDto.getDescription()).orElse(""));

        // ✅ Handle Scheduled Time
        try {
            OffsetDateTime scheduledTime = OffsetDateTime.parse(
                    discussionDto.getScheduledTime().trim(), DateTimeFormatter.ISO_DATE_TIME
            );
            discussion.setScheduledTime(scheduledTime);
            LOGGER.info("💾 Saving scheduled time: {}" + scheduledTime);
        } catch (DateTimeParseException e) {
            LOGGER.log(Level.SEVERE, "❌ Error parsing scheduled time: " + discussionDto.getScheduledTime(), e);
            throw new RuntimeException("❌ Invalid Scheduled Time format: " + discussionDto.getScheduledTime());
        }

        discussion.setUser(user);

        // ✅ Save Discussion FIRST to generate its ID
        Discussion savedDiscussion = discussionRepository.save(discussion);

        if (discussionDto.getQuestions() == null || discussionDto.getQuestions().isEmpty()) {
            throw new RuntimeException("❌ No questions provided for this discussion.");
        }

        int totalDuration = 0;
        List<Question> questionList = new ArrayList<>();

        for (int i = 0; i < discussionDto.getQuestions().size(); i++) {
            QuestionDto questionDto = discussionDto.getQuestions().get(i);

            Question question = new Question();
            question.setQuestionText(questionDto.getQuestionText());
            question.setDiscussion(savedDiscussion);

            // ✅ Save `position` field
            question.setPosition(i);  // Ensure 1-based ordering

            // ✅ Set persistingOptionsCount from DTO to Entity
            question.setPersistingOptionsCount(questionDto.getPersistingOptionsCount());

            // ✅ Save question to generate its ID
            Question savedQuestion = questionRepository.save(question);
            questionList.add(savedQuestion);

            // ✅ Save options immediately after saving the question
            if (questionDto.getOptions() != null && !questionDto.getOptions().isEmpty()) {
                for (String optionValue : questionDto.getOptions()) {
                    if (optionValue != null && !optionValue.trim().isEmpty()) {
                        QuestionOption option = new QuestionOption(savedQuestion, optionValue.trim());
                        questionOptionService.saveOption(option);
                    }
                }

                // ✅ Duration Calculation Logic (now 5 minutes per round)
                int optionCount = questionDto.getOptions().size();
                int persistCount = questionDto.getPersistingOptionsCount();

                // If there are ≤ 6 options, only one round of 5 minutes is needed
                if (optionCount <= 6) {
                    totalDuration += ROUND_DURATION_MINUTES;
                } else {
                    int optionsToReplace = optionCount - 6; // guaranteed > 0 here
                    int replaceablePerRound = 6 - persistCount;

                    // Failsafe in case somehow replaceablePerRound is 0 (shouldn't happen, but better safe)
                    if (replaceablePerRound <= 0) replaceablePerRound = 1;

                    int roundsAfterFirst = (int) Math.ceil(optionsToReplace / (double) replaceablePerRound);
                    int durationForQuestion = (roundsAfterFirst + 1) * ROUND_DURATION_MINUTES; // +1 for first round

                    totalDuration += durationForQuestion;
                }
            }
        }

        // ✅ Set updated questions in the discussion
        savedDiscussion.setQuestions(questionList);

        // ✅ Set total duration in the discussion
        savedDiscussion.setDuration(totalDuration);

        // ✅ Save Discussion with Final Data
        savedDiscussion = discussionRepository.save(savedDiscussion);

        LOGGER.info("✅ Discussion saved successfully.");

        return savedDiscussion;
    }

    @Override
    public List<Discussion> getAllDiscussions() {
        return discussionRepository.findAll();
    }


    @Override
    public List<Discussion> getDiscussionsByUserEmail(String email) {
        User user = userRepository.findByEmail(email);
        return (user != null) ? discussionRepository.findAllByUserIdOrderByDiscussionIdDesc(user.getId()) : List.of();
    }

    @Override
    public Discussion findDiscussionById(Long discussionId) {
        return discussionRepository.findById(discussionId).orElse(null);
    }

    @Override
    @Transactional
    public void deleteDiscussion(Long discussionId) {
        Discussion discussion = findDiscussionById(discussionId);
        if (discussion != null) {
            entityManager.clear();  // Clears persistence context

            // Delete all linked options for each question first
            List<Question> questions = questionRepository.findByDiscussion_DiscussionId(discussionId);
            for (Question question : questions) {
                questionOptionService.deleteByQuestionId(question.getId());
            }

            // Now delete all questions linked to this discussion
            questionRepository.deleteByDiscussionId(discussionId);

            // Finally, delete the discussion
            discussionRepository.delete(discussion);

            LOGGER.info("✅ Discussion deleted successfully.");

        } else {
            throw new RuntimeException("Discussion not found.");
        }
    }

    @Override
    public Question saveQuestion(Question question) {
        return questionRepository.save(question);  // Save question directly using repository
    }

    @Override
    public Discussion findDiscussionByNameAndCode(String discussionName, String uniqueCode) {
        return discussionRepository.findByDiscussionNameAndUniqueCode(discussionName, uniqueCode);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Discussion updateDiscussionById(Long discussionId, DiscussionDto discussionDto) {
        // Fetch the existing discussion
        Discussion existingDiscussion = discussionRepository.findById(discussionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion not found with ID: " + discussionId));

        // Update discussion details
        existingDiscussion.setDiscussionName(discussionDto.getDiscussionName());
        existingDiscussion.setNumberOfParticipants(discussionDto.getNumberOfParticipants());
        existingDiscussion.setNumberOfQuestions(discussionDto.getQuestions().size());
        existingDiscussion.setScheduledTime(OffsetDateTime.parse(discussionDto.getScheduledTime()));
        existingDiscussion.setDescription(discussionDto.getDescription());
        existingDiscussion.setDiscussionType(discussionDto.getDiscussionType());

        // Process questions
        updateQuestions(existingDiscussion, discussionDto.getQuestions());

        // Calculate and update duration based on options
        updateDuration(existingDiscussion);

        // Save the updated discussion
        return discussionRepository.save(existingDiscussion);
    }

    public void updateQuestions(Discussion discussion, List<QuestionDto> questionDtos) {
        // Fetch existing questions for the discussion
        List<Question> existingQuestions = questionRepository.findByDiscussion_DiscussionId(discussion.getDiscussionId());
        Map<Long, Question> existingQuestionMap = existingQuestions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // Extract received question IDs from DTO
        Set<Long> receivedQuestionIds = questionDtos.stream()
                .map(QuestionDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Case 4: Delete questions that are not in the received list
        List<Question> questionsToDelete = existingQuestions.stream()
                .filter(q -> !receivedQuestionIds.contains(q.getId()))
                .collect(Collectors.toList());

        // Delete options associated with questions to be deleted first (Case 3 for options)
        for (Question question : questionsToDelete) {
            questionOptionRepository.deleteByQuestionId(question.getId());
        }

        // Now delete the questions
        questionRepository.deleteAll(questionsToDelete);

        // Step 2: Process received questions
        for (QuestionDto questionDto : questionDtos) {
            Question question;
            boolean isExistingQuestion = questionDto.getId() != null && existingQuestionMap.containsKey(questionDto.getId());

            if (isExistingQuestion) {
                // Case 1 & 2: Update existing question (text or position)
                question = existingQuestionMap.get(questionDto.getId());
                boolean textChanged = !question.getQuestionText().equals(questionDto.getQuestionText());
                boolean positionChanged = question.getPosition() != questionDto.getPosition();
                boolean persistingCountChanged = question.getPersistingOptionsCount() != questionDto.getPersistingOptionsCount();

                if (textChanged) {
                    question.setQuestionText(questionDto.getQuestionText()); // Update text (Case 1)
                }

                if (positionChanged) {
                    question.setPosition(questionDto.getPosition()); // Update position (Case 2)
                }

                if (persistingCountChanged) {
                    question.setPersistingOptionsCount(questionDto.getPersistingOptionsCount());
                }

                // Save changes if any
                if (textChanged || positionChanged || persistingCountChanged) {
                    questionRepository.save(question);
                }
            } else {
                // Case 5: Add new question
                question = new Question();
                question.setDiscussion(discussion);
                question.setQuestionText(questionDto.getQuestionText());
                question.setPosition(questionDto.getPosition());
                question = questionRepository.save(question); // Save to get ID
                question.setPersistingOptionsCount(questionDto.getPersistingOptionsCount() != null ? questionDto.getPersistingOptionsCount() : 3);
            }

            // Get existing options for this question
            List<QuestionOption> existingOptions = questionOptionRepository.findByQuestionId(question.getId());
            Map<String, QuestionOption> existingOptionValueMap = existingOptions.stream()
                    .collect(Collectors.toMap(QuestionOption::getOptionValue, o -> o, (o1, o2) -> o1));

            // Process options for this question
            List<QuestionOptionDto> optionDtos = new ArrayList<>();
            for (String optionValue : questionDto.getOptions()) {
                QuestionOptionDto optionDto = new QuestionOptionDto();

                // Check if an option with this value already exists
                QuestionOption existingOption = existingOptionValueMap.get(optionValue);
                if (existingOption != null) {
                    optionDto.setId(existingOption.getOptionId());
                }

                optionDto.setOptionValue(optionValue);
                optionDtos.add(optionDto);
            }

            // Step 3: Update options for the question (all cases for options)
            updateOptionsForQuestion(question, optionDtos);
        }
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateOptionsForQuestion(Question question, List<QuestionOptionDto> newOptionDtos) {
        try {
            LOGGER.info("Starting option update for question {}" + question.getId());
            LOGGER.fine("Existing options: {}" + questionOptionRepository.findByQuestionId(question.getId()));
            LOGGER.fine("New options: {}" + newOptionDtos);

            // Validate input
            if (question == null || question.getId() == null) {
                throw new IllegalArgumentException("Question must not be null and must have an ID");
            }

            // Initialize collections
            List<QuestionOption> existingOptions = questionOptionRepository.findByQuestionId(question.getId());
            Map<Long, QuestionOption> existingOptionMap = existingOptions.stream()
                    .collect(Collectors.toMap(QuestionOption::getOptionId, o -> o));

            // Early exit if no changes
            if (isIdentical(existingOptions, newOptionDtos)) {
                LOGGER.log(Level.FINE, "No option changes detected for question {}", question.getId());
                return;
            }

            // Process changes
            List<QuestionOption> optionsToUpdate = new ArrayList<>();
            List<QuestionOption> optionsToCreate = new ArrayList<>();
            Set<Long> optionsToKeep = new HashSet<>();

            for (QuestionOptionDto dto : newOptionDtos) {
                if (dto.getId() != null && existingOptionMap.containsKey(dto.getId())) {
                    QuestionOption existing = existingOptionMap.get(dto.getId());
                    if (!existing.getOptionValue().equals(dto.getOptionValue())) {
                        // Create NEW entity instead of modifying existing
                        QuestionOption updatedOption = new QuestionOption();
                        updatedOption.setOptionId(existing.getOptionId()); // Keep same ID
                        updatedOption.setOptionValue(dto.getOptionValue());
                        updatedOption.setQuestion(question); // Maintain relationship
                        optionsToUpdate.add(updatedOption);
                    }
                    optionsToKeep.add(dto.getId());
                } else {
                    QuestionOption newOption = new QuestionOption();
                    newOption.setQuestion(question);
                    newOption.setOptionValue(dto.getOptionValue());
                    optionsToCreate.add(newOption);
                }
            }

            // Find options to delete (exist in DB but not in new data)
            List<QuestionOption> optionsToDelete = existingOptions.stream()
                    .filter(o -> !optionsToKeep.contains(o.getOptionId()))
                    .collect(Collectors.toList());

            // Log changes before executing
            logChanges(existingOptions.size(), newOptionDtos.size(),
                    optionsToUpdate.size(), optionsToCreate.size(), optionsToDelete.size());

            // Execution order matters:
            // 1. First delete obsolete options
            if (!optionsToDelete.isEmpty()) {
                questionOptionRepository.deleteAll(optionsToDelete);
                entityManager.flush();
            }

            // 2. Create new options
            if (!optionsToCreate.isEmpty()) {
                questionOptionRepository.saveAll(optionsToCreate);
                entityManager.flush();
            }

            // 3. Update changed options (as new entities)
            if (!optionsToUpdate.isEmpty()) {
                questionOptionRepository.saveAll(optionsToUpdate);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "OPTION UPDATE FAILURE for question " + question.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update options: " + e.getMessage());
        }
    }

    // Helper method to compare existing and new options
    private boolean isIdentical(List<QuestionOption> existingOptions, List<QuestionOptionDto> newOptionDtos) {
        if (existingOptions.size() != newOptionDtos.size()) {
            return false;
        }

        Map<String, Long> existingValues = existingOptions.stream()
                .collect(Collectors.toMap(QuestionOption::getOptionValue, QuestionOption::getOptionId));

        Map<String, Long> newValues = newOptionDtos.stream()
                .collect(Collectors.toMap(QuestionOptionDto::getOptionValue,
                        dto -> dto.getId() != null ? dto.getId() : -1L));

        return existingValues.equals(newValues);
    }

    // Detailed logging helper
    private void logChanges(int existingCount, int newCount,
                            int updateCount, int createCount, int deleteCount) {
        LOGGER.info("Option Update Analysis:");
        LOGGER.info("- Existing options: {}" + existingCount);
        LOGGER.info("- Received options: {}" + newCount);

        if (updateCount > 0) {
            LOGGER.info("- Options to update: {}" + updateCount);
        }
        if (createCount > 0) {
            LOGGER.info("- Options to create: {}" + createCount);
        }
        if (deleteCount > 0) {
            LOGGER.info("- Options to delete: {}" + deleteCount);
        }

        if (updateCount == 0 && createCount == 0 && deleteCount == 0) {
            LOGGER.info("- No database operations needed");
        }
    }

    @Override
    public List<QuestionDto> getQuestionsByDiscussionId(Long discussionId) {
        // Fetch questions ordered by position
        List<Question> questions = questionRepository.findByDiscussion_DiscussionIdOrderByPositionAsc(discussionId);

        // Convert Question entities to QuestionDto objects
        return questions.stream()
                .map(this::convertToQuestionDto)
                .collect(Collectors.toList());
    }

    private QuestionDto convertToQuestionDto(Question question) {
        QuestionDto questionDto = new QuestionDto();
        questionDto.setId(question.getId());
        questionDto.setQuestionText(question.getQuestionText());
        questionDto.setPosition(question.getPosition());
        questionDto.setAnswer(question.getAnswer());
        questionDto.setPersistingOptionsCount(question.getPersistingOptionsCount());

        // Convert QuestionOption entities to option values (strings)
        List<String> options = question.getOptions().stream()
                .map(QuestionOption::getOptionValue)
                .collect(Collectors.toList());
        questionDto.setOptions(options);

        return questionDto;
    }

    private void updateDuration(Discussion discussion) {
        List<Question> questions = questionRepository.findByDiscussion_DiscussionIdOrderByPositionAsc(discussion.getDiscussionId());
        int totalDuration = 0;

        for (Question question : questions) {
            List<QuestionOption> options = questionOptionRepository.findByQuestionId(question.getId());
            int optionCount = options.size();
            int persistCount = question.getPersistingOptionsCount();

            // If there are ≤ 6 options, only one round of 5 minutes is needed
            if (optionCount <= 6) {
                totalDuration += ROUND_DURATION_MINUTES;
            } else {
                int optionsToReplace = optionCount - 6; // guaranteed > 0 here
                int replaceablePerRound = 6 - persistCount;

                // Failsafe in case somehow replaceablePerRound is 0 (shouldn't happen, but better safe)
                if (replaceablePerRound <= 0) replaceablePerRound = 1;

                int roundsAfterFirst = (int) Math.ceil(optionsToReplace / (double) replaceablePerRound);
                int durationForQuestion = (roundsAfterFirst + 1) * ROUND_DURATION_MINUTES; // +1 for first round

                totalDuration += durationForQuestion;
            }
        }

        LOGGER.info("Updating discussion duration to: " + totalDuration);
        discussion.setDuration(totalDuration);
    }

    @Override
    public DiscussionDto getDiscussionById(Long discussionId) {
        // Fetch the discussion
        Discussion discussion = discussionRepository.findById(discussionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion not found with ID: " + discussionId));

        // Fetch all questions for the discussion (ordered by position)
        List<Question> questions = questionRepository.findByDiscussion_DiscussionIdOrderByPositionAsc(discussionId);

        // Fetch all options for the discussion
        List<QuestionOption> options = questionOptionRepository.findByQuestion_Discussion_DiscussionId(discussionId);

        // Group options by question ID
        Map<Long, List<String>> optionsByQuestionId = options.stream()
                .collect(Collectors.groupingBy(
                        option -> option.getQuestion().getId(),
                        Collectors.mapping(QuestionOption::getOptionValue, Collectors.toList())
                ));

        // Convert Discussion entity to DTO
        DiscussionDto discussionDto = new DiscussionDto();
        discussionDto.setDiscussionId(discussion.getDiscussionId());
        discussionDto.setUniqueCode(discussion.getUniqueCode());
        discussionDto.setDiscussionName(discussion.getDiscussionName());
        discussionDto.setNumberOfParticipants(discussion.getNumberOfParticipants());
        discussionDto.setNumberOfQuestions(discussion.getNumberOfQuestions());
        discussionDto.setDescription(discussion.getDescription());
        discussionDto.setScheduledTime(discussion.getScheduledTime().toString());
        discussionDto.setDuration(discussion.getDuration());
        discussionDto.setDiscussionType(discussion.getDiscussionType());

        // Convert Questions to QuestionDto
        List<QuestionDto> questionDtos = questions.stream()
                .map(question -> {
                    QuestionDto questionDto = convertToQuestionDto(question);
                    questionDto.setOptions(optionsByQuestionId.getOrDefault(question.getId(), Collections.emptyList()));
                    return questionDto;
                })
                .collect(Collectors.toList());

        discussionDto.setQuestions(questionDtos);
        return discussionDto;
    }
}
