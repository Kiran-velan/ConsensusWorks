package net.enjoy.springboot.registrationlogin.service;

import net.enjoy.springboot.registrationlogin.dto.DiscussionDto;
import net.enjoy.springboot.registrationlogin.dto.QuestionDto;
import net.enjoy.springboot.registrationlogin.entity.Discussion;
import net.enjoy.springboot.registrationlogin.entity.Question;
import net.enjoy.springboot.registrationlogin.entity.RoundScore;
import net.enjoy.springboot.registrationlogin.repository.DiscussionRepository;
import net.enjoy.springboot.registrationlogin.repository.QuestionRepository;
import net.enjoy.springboot.registrationlogin.repository.RoundScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final DiscussionRepository discussionRepository;
    private final QuestionRepository questionRepository;
    private final RoundScoreRepository roundScoreRepository;

    @Autowired
    public ReportService(DiscussionRepository discussionRepository,
                         QuestionRepository questionRepository,
                         RoundScoreRepository roundScoreRepository) {
        this.discussionRepository = discussionRepository;
        this.questionRepository = questionRepository;
        this.roundScoreRepository = roundScoreRepository;
    }

    public DiscussionDto getDiscussionReportById(Long discussionId) {
        Discussion discussion = discussionRepository.findById(discussionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion not found"));

        List<Question> questions = questionRepository.findByDiscussion_DiscussionIdOrderByPositionAsc(discussionId);
        List<QuestionDto> questionDtos = new ArrayList<>();

        // ✅ Load chat file for this discussion
        Map<Long, List<String>> questionChats = loadQuestionChats(discussionId, questions);

        for (Question question : questions) {
            QuestionDto dto = new QuestionDto();
            dto.setId(question.getId());
            dto.setQuestionText(question.getQuestionText());
            dto.setPosition(question.getPosition());
            dto.setAnswer(question.getAnswer());
            dto.setPersistingOptionsCount(question.getPersistingOptionsCount());

            // 🔽 Fetch all RoundScores for this question
            List<RoundScore> scores = roundScoreRepository.findByQuestionId(question.getId());

            // 🔽 Group by roundNumber
            Map<Integer, List<RoundScore>> byRound = scores.stream()
                    .collect(Collectors.groupingBy(RoundScore::getRoundNumber, LinkedHashMap::new, Collectors.toList()));

            // Build round-wise sorted scores
            Map<Integer, Map<String, Double>> roundWiseScores = new LinkedHashMap<>();

            for (Map.Entry<Integer, List<RoundScore>> entry : byRound.entrySet()) {
                Integer roundNum = entry.getKey();

                // Map optionText → score
                Map<String, Double> optionScores = entry.getValue().stream()
                        .collect(Collectors.toMap(
                                RoundScore::getOptionText,
                                RoundScore::getScore,
                                (a, b) -> b,
                                LinkedHashMap::new
                        ));

                // Sort by descending score
                Map<String, Double> sortedScores = optionScores.entrySet().stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (a, b) -> b,
                                LinkedHashMap::new
                        ));

                roundWiseScores.put(roundNum, sortedScores);
            }

            // Attach to DTO
            dto.setRoundWiseScores(roundWiseScores);

            // ✅ Attach question-specific chats
            if (questionChats.containsKey(question.getId())) {
                dto.setChatSegments(questionChats.get(question.getId()));
            }

            questionDtos.add(dto);
        }

        return buildDiscussionDto(discussion, questionDtos);
    }

    private DiscussionDto buildDiscussionDto(Discussion discussion, List<QuestionDto> questionDtos) {
        DiscussionDto dto = new DiscussionDto();
        dto.setDiscussionId(discussion.getDiscussionId());
        dto.setUniqueCode(discussion.getUniqueCode());
        dto.setDiscussionName(discussion.getDiscussionName());
        dto.setNumberOfParticipants(discussion.getNumberOfParticipants());
        dto.setNumberOfQuestions(discussion.getNumberOfQuestions());
        dto.setDescription(discussion.getDescription());
        dto.setScheduledTime(discussion.getScheduledTime().toString());
        dto.setDuration(discussion.getDuration());
        dto.setQuestions(questionDtos);
        return dto;
    }

    // -------------------------------------------------------
    // 🧠 Chat segmentation logic
    // -------------------------------------------------------
    private Map<Long, List<String>> loadQuestionChats(Long discussionId, List<Question> questions) {
        Map<Long, List<String>> questionChats = new LinkedHashMap<>();

        try {
            // ✅ 1. Correct path — assuming chat_logs folder is at application root
            Path chatFilePath = Paths.get("/app/logs/chat_logs", "chat_" + discussionId + ".txt");

            if (!Files.exists(chatFilePath)) {
                System.err.println("⚠️ Chat file not found for discussion: " + discussionId);
                return questionChats;
            }

            // ✅ 2. Read full chat content
            String content = Files.readString(chatFilePath);
            String[] splitRounds = content.split(Pattern.quote("The Current Round Ends here."));
            List<String> rounds = Arrays.stream(splitRounds)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            // ✅ 3. Get questionId → totalRounds mapping
            Map<Long, Integer> roundCounts = new LinkedHashMap<>();
            for (Question q : questions) {
                List<RoundScore> qScores = roundScoreRepository.findByQuestionId(q.getId());
                if (!qScores.isEmpty()) {
                    int maxRound = qScores.stream()
                            .mapToInt(RoundScore::getRoundNumber)
                            .max()
                            .orElse(0);
                    roundCounts.put(q.getId(), maxRound);
                }
            }

            // ✅ 4. Distribute chat segments based on round counts
            int index = 0;
            for (Map.Entry<Long, Integer> entry : roundCounts.entrySet()) {
                Long qId = entry.getKey();
                int roundsForQ = entry.getValue();

                int end = Math.min(index + roundsForQ, rounds.size());
                if (index < rounds.size()) {
                    questionChats.put(qId, new ArrayList<>(rounds.subList(index, end)));
                    index = end;
                }
            }

        } catch (IOException e) {
            System.err.println("❌ Error reading chat file for discussion " + discussionId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return questionChats;
    }
}
