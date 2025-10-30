package net.enjoy.springboot.registrationlogin.session;

import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DiscussionFileLogger {

    private static final String LOG_BASE = "/app/logs/";
    private static final String LOG_DIR = LOG_BASE + "discussion_logs";
    private static final String WS_LOG_DIR = LOG_BASE + "discussion_websocket_logs";
    private static final String READINGS_LOG_DIR = LOG_BASE + "discussion_readings_logs";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
//        createDirectory(LOG_DIR);
//        createDirectory(WS_LOG_DIR);
        createDirectory(READINGS_LOG_DIR);
    }

    /** Utility to ensure directory exists */
    private static void createDirectory(String dirName) {
        Path path = Paths.get(dirName);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** ✅ Existing discussion log */
    public static void log(Long discussionId, String message) {
//        writeToFile(LOG_DIR + "/discussion-" + discussionId + ".log", message);
    }

    /** ✅ New WebSocket-specific log */
    public static void logWebSocket(Long discussionId, String message) {
//        writeToFile(WS_LOG_DIR + "/ws-" + discussionId + ".log", message);
    }

    // Readings and scores
    public static void logReadingsAndScores(Long discussionId, String message) {
        writeToFile(READINGS_LOG_DIR + "/discussion-" + discussionId + ".log", message);
    }

    /** Common writer utility */
    private static void writeToFile(String filePath, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        try (PrintWriter out = new PrintWriter(new FileWriter(filePath, true))) {
            out.printf("%s - %s%n", timestamp, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
