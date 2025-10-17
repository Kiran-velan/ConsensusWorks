package net.enjoy.springboot.registrationlogin.chat;

import net.enjoy.springboot.registrationlogin.session.DiscussionFileLogger;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class ChatController {

    private static final String CHAT_LOG_DIR = "chat_logs/";
    private final Map<Long, Map<String, Integer>> discussionUserMap = new ConcurrentHashMap<>();

    // Message handler
    @MessageMapping("/chat/{discussionId}/sendMessage")
    @SendTo("/topic/{discussionId}")
    public String sendMessage(@DestinationVariable Long discussionId,
                              @Payload String message,
                              @Header("simpSessionId") String sessionId) {

        // Get or create session-user mapping for the discussion
        discussionUserMap.putIfAbsent(discussionId, new ConcurrentHashMap<>());
        Map<String, Integer> userMap = discussionUserMap.get(discussionId);

        // Assign number if not already assigned
        userMap.computeIfAbsent(sessionId, sid -> generateUniqueUserNumber(userMap.values()));

        int userNumber = userMap.get(sessionId);
        String labeledMessage = "Player " + userNumber + "\n" + message;

        String savingMessage = "Player " + userNumber + ": " + message;
        saveMessageToFile(discussionId, savingMessage);
        return labeledMessage;
    }

    private int generateUniqueUserNumber(Collection<Integer> existingNumbers) {
        Random rand = new Random();
        int num;
        do {
            num = rand.nextInt(900) + 100;  // e.g. 100–999
        } while (existingNumbers.contains(num));
        return num;
    }

    private void saveMessageToFile(Long discussionId, String message) {
        try {
            Files.createDirectories(Paths.get(CHAT_LOG_DIR));
            String filename = CHAT_LOG_DIR + "chat_" + discussionId + ".txt";

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true))) {
                writer.write(message);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Public method to allow other classes to append a "Round Ends" line to the discussion chat log
    public void writeRoundEnds(Long discussionId) {
        saveMessageToFile(discussionId, "The Current Round Ends here.");
    }

    public void clearDiscussionUserMap(Long discussionId) {
        discussionUserMap.remove(discussionId);
        DiscussionFileLogger.log(discussionId, String.format("Cleared user map for discussion %d", discussionId));
        System.out.println("Cleared user map for discussion: " + discussionId);
    }
}