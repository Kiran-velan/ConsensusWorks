package net.enjoy.springboot.registrationlogin.session;

import net.enjoy.springboot.registrationlogin.chat.ChatController;
import net.enjoy.springboot.registrationlogin.service.SessionService;
import net.enjoy.springboot.registrationlogin.service.PythonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoundTimerService {

    private final SessionService sessionService;
    private final PythonService pythonService;

    @Autowired
    private ChatController chatController;

    // discussionId → round end time
    private final Map<Long, Instant> roundEndTimes = new ConcurrentHashMap<>();

    public RoundTimerService(SessionService sessionService, PythonService pythonService) {
        this.sessionService = sessionService;
        this.pythonService = pythonService;
    }

    int roundDurationSeconds = 180;    // for now it is 30 seconds round, n min = n * 60 seconds

    /**
     * Called when a new discussion starts
     */
    public void registerDiscussion(Long discussionId, Instant scheduledStartTime) {
        // initial end time = scheduled + 1 min grace + round duration
        Instant firstEndTime = scheduledStartTime
                .plusSeconds(60) // waiting room duration
                .plusSeconds(roundDurationSeconds);

        roundEndTimes.putIfAbsent(discussionId, firstEndTime);
        DiscussionFileLogger.log(discussionId, String.format("Registered discussion %d with first end time %s", discussionId, firstEndTime));
    }

    /**
     * End current round and schedule next one.
     * Can also be called manually (convergence or time ends).
     */
    public void endRoundAndScheduleNext(Long discussionId) {
        // Clear slider queue
        sessionService.clearSliderQueue(discussionId);

        // Reset discussion state in Python microservice
        boolean resetSuccessful = pythonService.resetDiscussionState(discussionId);
        if (!resetSuccessful) {
            // optionally log or handle failure
            System.out.println("Warning: Could not reset Python state for discussionId " + discussionId);
        }

        // Compute next round end time
        Instant nextEndTime = Instant.now().plusSeconds(roundDurationSeconds);
        DiscussionFileLogger.logReadingsAndScores(discussionId, "Round ends for discussion.");

        // Update map
        roundEndTimes.put(discussionId, nextEndTime);

        // When the round ends, write it to the chat log
        chatController.writeRoundEnds(discussionId);
        DiscussionFileLogger.log(discussionId, String.format("Ended round for discussion %d with next end time %s", discussionId, nextEndTime));
    }

    /**
     * Scheduler runs every second to check for round expiry
     */
    @Scheduled(fixedRate = 1000)
    public void checkRounds() {
        Instant now = Instant.now();

        for (Map.Entry<Long, Instant> entry : roundEndTimes.entrySet()) {
            Long discussionId = entry.getKey();
            Instant endTime = entry.getValue();

            if (!now.isBefore(endTime)) {
                // Round expired
                endRoundAndScheduleNext(discussionId);
                DiscussionFileLogger.log(discussionId, String.format("Round expired for discussion %d with end time %s", discussionId, endTime));
            }
        }
    }

    public void clearDiscussion(Long discussionId) {
        roundEndTimes.remove(discussionId);
        DiscussionFileLogger.log(discussionId, String.format("Cleaned up expired discussion %d from Round Timer Service", discussionId));
    }

}
