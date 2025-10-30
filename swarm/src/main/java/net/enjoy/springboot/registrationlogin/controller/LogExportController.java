package net.enjoy.springboot.registrationlogin.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/export")
public class LogExportController {

    @GetMapping("/logs/all")
    public ResponseEntity<Resource> exportAllLogs() {
        try {
            Path logsDir = Paths.get("/app/logs");
            if (!Files.exists(logsDir) || !Files.isDirectory(logsDir)) {
                return ResponseEntity.notFound().build();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // Add all log directories
                addDirectoryToZip(zos, logsDir, "chat_logs");
                addDirectoryToZip(zos, logsDir, "discussion_logs");
                addDirectoryToZip(zos, logsDir, "discussion_websocket_logs");
                addDirectoryToZip(zos, logsDir, "discussion_readings_logs");
            }

            ByteArrayResource resource = new ByteArrayResource(baos.toByteArray());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=springboot-all-logs.zip")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(baos.size())
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/logs/chat")
    public ResponseEntity<Resource> exportChatLogs() {
        return exportSingleLogDirectory("chat_logs", "chat-logs.zip");
    }

    @GetMapping("/logs/discussion")
    public ResponseEntity<Resource> exportDiscussionLogs() {
        return exportSingleLogDirectory("discussion_logs", "discussion-logs.zip");
    }

    @GetMapping("/logs/websocket")
    public ResponseEntity<Resource> exportWebsocketLogs() {
        return exportSingleLogDirectory("discussion_websocket_logs", "websocket-logs.zip");
    }

    @GetMapping("/logs/readings")
    public ResponseEntity<Resource> exportReadingsLogs() {
        return exportSingleLogDirectory("discussion_readings_logs", "readings-logs.zip");
    }

    private ResponseEntity<Resource> exportSingleLogDirectory(String dirName, String filename) {
        try {
            Path dirPath = Paths.get("/app/logs", dirName);
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                return ResponseEntity.notFound().build();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                addDirectoryToZip(zos, Paths.get("/app/logs"), dirName);
            }

            ByteArrayResource resource = new ByteArrayResource(baos.toByteArray());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(baos.size())
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private void addDirectoryToZip(ZipOutputStream zos, Path basePath, String dirName) throws IOException {
        Path dirPath = basePath.resolve(dirName);
        if (!Files.exists(dirPath)) return;

        Files.walk(dirPath)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    try {
                        String zipPath = basePath.relativize(path).toString();
                        zos.putNextEntry(new ZipEntry(zipPath));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        // Log error but continue with other files
                        System.err.println("Failed to add file to zip: " + path + " - " + e.getMessage());
                    }
                });
    }

    @GetMapping("/logs/list")
    public ResponseEntity<?> listAvailableLogs() {
        try {
            Path logsDir = Paths.get("/app/logs");
            if (!Files.exists(logsDir)) {
                return ResponseEntity.ok().body("No logs directory found");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Available log directories:\n");

            String[] directories = {"chat_logs", "discussion_logs", "discussion_websocket_logs", "discussion_readings_logs"};

            for (String dir : directories) {
                Path dirPath = logsDir.resolve(dir);
                if (Files.exists(dirPath)) {
                    long fileCount = Files.list(dirPath).filter(p -> !Files.isDirectory(p)).count();
                    sb.append("- ").append(dir).append(": ").append(fileCount).append(" files\n");
                } else {
                    sb.append("- ").append(dir).append(": NOT FOUND\n");
                }
            }

            return ResponseEntity.ok(sb.toString());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error listing logs: " + e.getMessage());
        }
    }
}