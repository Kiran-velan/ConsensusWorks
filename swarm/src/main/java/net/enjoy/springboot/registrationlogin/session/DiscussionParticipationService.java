package net.enjoy.springboot.registrationlogin.session;

import net.enjoy.springboot.registrationlogin.chat.ChatController;
import net.enjoy.springboot.registrationlogin.repository.DiscussionRepository;
import net.enjoy.springboot.registrationlogin.service.SessionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Service
public class DiscussionParticipationService {

    private final ChatController chatController;
    private final DiscussionRepository discussionRepository;
    private final RoundTimerService roundTimerService;
    private final SessionService sessionService;

    private static final Logger LOGGER = Logger.getLogger(DiscussionParticipationService.class.getName());

    // ✅ Thread-safe map: discussionId -> set of user emails
    private final Map<Long, Set<String>> discussionParticipants = new ConcurrentHashMap<>();

    public DiscussionParticipationService(ChatController chatController, DiscussionRepository discussionRepository,
                                          RoundTimerService roundTimerService, SessionService sessionService) {
        this.chatController = chatController;
        this.discussionRepository = discussionRepository;
        this.roundTimerService = roundTimerService;
        this.sessionService = sessionService;
    }

    // Check if user already joined discussion
    public boolean hasUserJoined(Long discussionId, String email) {
        return discussionParticipants.getOrDefault(discussionId, Collections.emptySet())
                .contains(email);
    }

    // Add user to discussion (create map entry if not exists)
    public void addUser(Long discussionId, String email) {
        discussionParticipants.computeIfAbsent(discussionId, k -> ConcurrentHashMap.newKeySet())
                .add(email);
        DiscussionFileLogger.log(discussionId, String.format("User %s joined discussion %d", email, discussionId));
    }

    // Delete entire discussion participant map (cleanup after session ends)
    public void removeDiscussion(Long discussionId) {
        discussionParticipants.remove(discussionId);
    }

    // (Optional) Get all users of a discussion
    public Set<String> getUsers(Long discussionId) {
        return discussionParticipants.getOrDefault(discussionId, Collections.emptySet());
    }
    // this might later be useful to get the list of users in a discussion

    // Helper method to get all discussion IDs
    private Set<Long> getAllDiscussionIds() {
        return new HashSet<>(discussionParticipants.keySet());
    }

    // Scheduled task every 5 min (300000) to check scheduledTime + duration > now to clean all in-memory maps of these completed discussions
    @Scheduled(fixedRate = 300000)
    public void validateDiscussionTimers() {
        OffsetDateTime now = OffsetDateTime.now();

        // Only check active discussions from map
        Set<Long> activeDiscussionIds = getAllDiscussionIds();

        if (activeDiscussionIds.isEmpty()) {
            return; // No active discussions to check
        }

        // Fetch only the timing information for active discussions
        List<Object[]> discussionTimings = discussionRepository.findDiscussionTimingsByIds(activeDiscussionIds);

        for (Object[] timing : discussionTimings) {
            Long discussionId = (Long) timing[0];
            OffsetDateTime startTime = (OffsetDateTime) timing[1];
            Integer durationMinutes = (Integer) timing[2];

            if (startTime != null && durationMinutes != null && durationMinutes > 0) {
                OffsetDateTime endTime = startTime.plusMinutes(durationMinutes);

                if (now.isAfter(endTime)) {
                    // 🧹 Clear chat user-number map
                    chatController.clearDiscussionUserMap(discussionId);

                    // 🧹 Clear in-memory of the map from discussion id to user email.
                    removeDiscussion(discussionId);
                    DiscussionFileLogger.log(discussionId, String.format("Cleaned up expired discussion %d", discussionId));

                    // 🧹 Clear the timer
                    roundTimerService.clearDiscussion(discussionId);

                    // Clear the RequiredNum cache
                    sessionService.clearCachedRequiredNum(discussionId);

                    LOGGER.info("Cleaned up expired discussion: {}" + discussionId);
                }
            }
        }
    }
}

