package net.enjoy.springboot.registrationlogin.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import net.enjoy.springboot.registrationlogin.dto.*;
import net.enjoy.springboot.registrationlogin.repository.*;
import net.enjoy.springboot.registrationlogin.entity.*;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.enjoy.springboot.registrationlogin.session.DiscussionFileLogger;

@Service
public class SessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    private final DiscussionRepository discussionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final Map<Long, Deque<SliderEntry>> sliderMap = new ConcurrentHashMap<>();
    private final RoundScoreRepository roundScoreRepository;

    public SessionServiceImpl(DiscussionRepository discussionRepository, QuestionRepository questionRepository,
                              QuestionOptionRepository questionOptionRepository, RoundScoreRepository roundScoreRepository) {
        this.discussionRepository = discussionRepository;
        this.questionRepository = questionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.roundScoreRepository = roundScoreRepository;
    }

    private record DiscussionInfo(Integer requiredNum, String discussionType) {}
    // discussion id : requiredNum and discussionType
    private final Map<Long, DiscussionInfo> participantInfoCache = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "discussionDetails", key = "#discussionId")
    public Discussion findDiscussionById(Long discussionId) {
        return discussionRepository.findById(discussionId).orElse(null);
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "questionsWithOptions", key = "#discussionId")
    public List<QuestionDto> getQuestionsWithAllOptions(Long discussionId) {
        // Validate input
        if (discussionId == null || discussionId <= 0) {
            throw new IllegalArgumentException("Invalid discussion ID");
        }

        // Find questions for the discussion
        List<Question> questions = questionRepository.findByDiscussion_DiscussionIdOrderByPositionAsc(discussionId);

        // Handle case of no questions
        if (questions.isEmpty()) {
            return Collections.emptyList();
        }

        return questions.stream()
                .map(this::mapQuestionToDto)
                .collect(Collectors.toList());
    }

    // Extract mapping logic to improve readability and testability
    private QuestionDto mapQuestionToDto(Question question) {
        QuestionDto questionDto = new QuestionDto();
        questionDto.setId(question.getId());
        questionDto.setDiscussionId(question.getDiscussion().getDiscussionId());
        questionDto.setQuestionText(question.getQuestionText());
        questionDto.setPosition(question.getPosition());
        questionDto.setPersistingOptionsCount(question.getPersistingOptionsCount());

        // Fetch options more efficiently
        List<String> options = questionOptionRepository.findByQuestion_Id(question.getId())
                .stream()
                .map(QuestionOption::getOptionValue)
                .collect(Collectors.toList());

        questionDto.setOptions(options);

        return questionDto;
    }

    @Override
    public void addSliderData(Long discussionId, List<Double> sliders, List<String> optionLabels, Long questionId, int roundNum) {
        sliderMap.putIfAbsent(discussionId, new ArrayDeque<>());

        Deque<SliderEntry> entries = sliderMap.get(discussionId);
        synchronized (entries) {
            entries.addLast(new SliderEntry(sliders, optionLabels, questionId, roundNum));
            DiscussionFileLogger.logWebSocket(discussionId, String.format("Added slider data for questionId=%d roundNum=%d", questionId, roundNum));
        }
    }

    @Override
    public List<SliderEntry> getLatestEntries(Long discussionId) {
        Deque<SliderEntry> entries = sliderMap.getOrDefault(discussionId, new ArrayDeque<>());
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    // Uses cache to avoid repetitive DB calls.
    private int getRequiredNum(Long discussionId) {
        return participantInfoCache.computeIfAbsent(discussionId, id -> {
            Discussion d = discussionRepository.findById(id).orElse(null);
            return (d != null)
                    ? new DiscussionInfo(d.getNumberOfParticipants(), d.getDiscussionType())
                    : new DiscussionInfo(0, null);
        }).requiredNum();
    }

    public String getMode(Long discussionId) {
        DiscussionInfo info = participantInfoCache.get(discussionId);
        return info != null ? info.discussionType() : null;
    }

    public void clearCachedRequiredNum(Long discussionId) {
        participantInfoCache.remove(discussionId);
    }

    @Override
    public Map<Long, List<SliderEntry>> getProcessableEntries() {
        Map<Long, List<SliderEntry>> snapshot = new HashMap<>();

        sliderMap.forEach((discussionId, entries) -> {
            int requiredNum = getRequiredNum(discussionId);
            if (requiredNum == 0) return; // invalid discussion

            DiscussionFileLogger.logWebSocket(discussionId,
                    String.format("Checking discussion %d: %d entries available", discussionId, entries.size()));

            synchronized (entries) {
                if (entries.size() >= requiredNum / 2) {
                    // 1️⃣ Take a snapshot for processing
                    List<SliderEntry> snapshotList = new ArrayList<>(entries);
                    snapshot.put(discussionId, snapshotList);

                    DiscussionFileLogger.logWebSocket(discussionId,
                            String.format("Snapshot created with %d entries for discussion %d", snapshotList.size(), discussionId));

                    // 2️⃣ Determine how many recent entries to retain
                    int retainCount = Math.max(1, requiredNum / 4);
                    int startIndex = Math.max(0, snapshotList.size() - retainCount);

                    // 3️⃣ Build a new deque with the last few retained entries
                    Deque<SliderEntry> newBuffer = new LinkedList<>(snapshotList.subList(startIndex, snapshotList.size()));

                    DiscussionFileLogger.logWebSocket(discussionId,
                            String.format("Retaining %d most recent entries, replacing buffer safely", retainCount));

                    // 4️⃣ Replace the old buffer in the map with the new one
                    sliderMap.put(discussionId, newBuffer);
                }
            }
        });

        return snapshot;
    }

    // To clear slider for a discussion id after every round ends
    @Override
    public void clearSliderQueue(Long discussionId) {
        Deque<SliderEntry> removed = sliderMap.remove(discussionId);
        if (removed != null) {
            log.info("Cleared slider queue for discussionId={} (removed {} entries)", discussionId, removed.size());
            DiscussionFileLogger.log(discussionId, String.format("Cleared slider queue, removed %d entries", removed.size()));
        } else {
            log.info("No slider queue found to clear for discussionId={}", discussionId);
        }
    }
    // the round ends will be known if the timer becomes 0

    @Override
    @Cacheable(value = "topOptions", key = "#questionId + '_' + #roundNumber")
    public List<String> getTopOptionTextsByFitness(Long questionId, int roundNumber) {
        // Determine how many options to persist
        int limit = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid questionId: " + questionId))
                .getPersistingOptionsCount();

        // Fetch scores from RoundScore, sort & limit
        return roundScoreRepository.findByQuestionIdAndRoundNumberOrderByScoreDesc(questionId, roundNumber)
                .stream()
                .limit(limit)
                .map(RoundScore::getOptionText)
                .collect(Collectors.toList());
    }

    @Override
    public String getAnswerByQuestionId(Long questionId) {
        return questionRepository.findById(questionId)
                .map(Question::getAnswer)
                .orElse(null);
    }

    @Override
    public void updateOptionFitnessScores(Long questionId, int roundNum, List<String> optionLabels, List<Double> fitnessScores) {
        if (optionLabels == null || fitnessScores == null) {
            throw new IllegalArgumentException("Labels and scores must be present");
        }
        for (int i = 0; i < optionLabels.size(); i++) {
            String label = optionLabels.get(i);
            double newScore = fitnessScores.get(i);

            // ✅ Skip placeholder or empty options
            // But actually in optionLabels we are not including "-", no need for this check actually
            if (label == null || label.trim().equals("-")) {
                continue;
            }

            // Check if entry exists
            roundScoreRepository.findByQuestionIdAndRoundNumberAndOptionText(questionId, roundNum, label)
                    .ifPresentOrElse(existing -> {
                        // update existing score
                        existing.setScore(newScore);
                        roundScoreRepository.save(existing);
                    }, () -> {
                        // create new entry
                        RoundScore roundScore = new RoundScore(questionId, roundNum, label, newScore);
                        roundScoreRepository.save(roundScore);
                    });
        }
    }

    @Transactional
    public void updateAnswerByQuestionId(Long discussionId, Long questionId, String answer) {
        questionRepository.findByDiscussionDiscussionIdAndId(discussionId, questionId)
                .ifPresent(question -> {
                    question.setAnswer(answer);
                    questionRepository.save(question);
                });
    }

}
