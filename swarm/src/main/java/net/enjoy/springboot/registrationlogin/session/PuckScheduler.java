package net.enjoy.springboot.registrationlogin.session;

import net.enjoy.springboot.registrationlogin.dto.PuckCoordinatesDto;
import net.enjoy.springboot.registrationlogin.dto.SliderEntry;
import net.enjoy.springboot.registrationlogin.service.PythonService;
import net.enjoy.springboot.registrationlogin.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PuckScheduler {

    private final SessionService sessionService;
    private final PythonService pythonService;
    private final SimpMessagingTemplate messagingTemplate;
    private static final Logger log = LoggerFactory.getLogger(PuckScheduler.class);
    private final RoundTimerService roundTimerService;

    // Cache for last converged question/round per discussion
    private final Map<Long, Pair<Long, Integer>> lastConvergedMap = new HashMap<>();

    @Autowired
    public PuckScheduler(SessionService sessionService, PythonService pythonService,
                         SimpMessagingTemplate messagingTemplate, RoundTimerService roundTimerService) {
        this.sessionService = sessionService;
        this.pythonService = pythonService;
        this.messagingTemplate = messagingTemplate;
        this.roundTimerService = roundTimerService;
    }


    // Runs every 5 seconds
    @Scheduled(fixedDelay = 5000)
    public void updatePuckPosition() {
        DiscussionFileLogger.logWebSocket(1L, "Request to PythonService for the discussion");
        Map<Long, List<SliderEntry>> allEntries = sessionService.getProcessableEntries();

        for (Map.Entry<Long, List<SliderEntry>> entry : allEntries.entrySet()) {
            Long discussionId = entry.getKey();
            List<SliderEntry> sliderEntries = entry.getValue();

            // Extract just the slider values for Slider processing input
            List<List<Double>> submissions = sliderEntries.stream()
                    .map(SliderEntry::getSliders)
                    .collect(Collectors.toList());

            // Get option labels and question from the first entry
            List<String> optionLabels = sliderEntries.get(0).getOptionLabels();
            Long questionId = sliderEntries.get(0).getQuestionId();
            int roundNum = sliderEntries.get(0).getRoundNum();
            String mode = sessionService.getMode(discussionId);
            DiscussionFileLogger.log(discussionId, String.format("Processing strengths for questionId=%d roundNum=%d with %d entries, mode %s", questionId, roundNum, sliderEntries.size(), mode));

            try {
                // Call PythonService
                DiscussionFileLogger.logWebSocket(discussionId, "Request to PythonService for the discussion");
                PuckCoordinatesDto response = pythonService.calculatePuckPosition(discussionId, submissions, mode);

                if (response != null) {

                    Pair<Long, Integer> last = lastConvergedMap.get(discussionId);
                    if (last != null && last.getFirst().equals(questionId) && last.getSecond() == roundNum) {
                        // Already handled this convergence earlier and this rounded has ended, so skip
                        DiscussionFileLogger.log( discussionId, String.format("Skipping duplicate convergence for question %d round %d", questionId, roundNum));
                        continue;
                    }
                    // similarly, the issue if the round ends because of timer, then that is handled by frontend

                    sessionService.updateOptionFitnessScores(questionId, roundNum, optionLabels, response.getFitnessScores());
                    DiscussionFileLogger.log(discussionId, String.format("Updated option fitness scores for questionId=%d roundNum=%d", questionId, roundNum));

                    Map<String, Object> data = new HashMap<>();
                    data.put("strengths", response.getFitnessScores());
                    data.put("converged", false);

                    messagingTemplate.convertAndSend("/topic/strengths/" + discussionId, data);
                    DiscussionFileLogger.log(discussionId, String.format("Sent strengths for discussionId=%d with fitness scores: %s, converged = %b", discussionId, response.getFitnessScores(), response.isConverged()));

                    // Use 'converged' directly from Python response
                    if (response.isConverged()) {

                        // New convergence → update cache
                        lastConvergedMap.put(discussionId, Pair.of(questionId, roundNum));

                        int matchedIndex = response.getConvergedIndex();
                        String answer = optionLabels.get(matchedIndex);
                        DiscussionFileLogger.log(discussionId, String.format("Converged to Option %d (%s) for question %d", matchedIndex + 1, answer, questionId));

                        // First Update answer in DB
                        sessionService.updateAnswerByQuestionId(discussionId, questionId, answer);

                        // Send puck to center to start new round
                        messagingTemplate.convertAndSend("/topic/strengths/" + discussionId, Map.of("strengths", List.of(0, 0, 0, 0, 0, 0), "converged", true));

                        // Start new round timer
                        roundTimerService.endRoundAndScheduleNext(discussionId);

                        log.info("✅ Converged to Option {} ({}) for question '{}'", matchedIndex + 1, answer, questionId);

                    }
                }

                log.info("Sent strengths for discussion {}: {} with options: {}for question: {}",
                        discussionId, response.getFitnessScores(), optionLabels, questionId);

            } catch (Exception e) {
                log.error("Failed to calculate or send strengths for discussion " + discussionId, e);
            }
        }
    }

    // simple immutable pair helper
    private record Pair<F, S>(F first, S second) {
        static <F, S> Pair<F, S> of(F f, S s) {
            return new Pair<>(f, s);
        }
        public F getFirst() { return first; }
        public S getSecond() { return second; }
    }

}
