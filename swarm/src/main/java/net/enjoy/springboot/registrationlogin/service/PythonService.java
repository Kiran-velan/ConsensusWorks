package net.enjoy.springboot.registrationlogin.service;

import net.enjoy.springboot.registrationlogin.dto.PuckCoordinatesDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import net.enjoy.springboot.registrationlogin.session.DiscussionFileLogger;

@Service
public class PythonService {

    private static final Logger log = LoggerFactory.getLogger(PythonService.class);
    private final WebClient webClient;

    // Constructor with environment variable
    public PythonService(@Value("${microservice.url}") String microserviceUrl) {
        this.webClient = WebClient.create(microserviceUrl);
        log.info("PythonService initialized with URL: {}", microserviceUrl);
    }

    public PuckCoordinatesDto calculatePuckPosition(Long discussionId, List<List<Double>> submissions, String mode) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("discussionId", discussionId);
        requestBody.put("submissions", submissions);
        requestBody.put("mode", mode);

        try {
            DiscussionFileLogger.logWebSocket(discussionId, String.format("Sending Request to MicroService for processing, %d data", submissions.size()));
            PuckCoordinatesDto response = webClient.post()
                    .uri("/process_slider")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(PuckCoordinatesDto.class)
                    .block();

            if (response == null) {
                log.error("⚠️ Python service returned null for discussion {}", discussionId);
                DiscussionFileLogger.logWebSocket(discussionId, "⚠️ Python service returned null for the discussion.");
                return null;
            }

            if (response.getFitnessScores() == null || response.getFitnessScores().isEmpty()) {
                log.warn("⚠️ Received potentially empty fitness list for discussion {}", discussionId);
                DiscussionFileLogger.logWebSocket(discussionId, "⚠️ Received potentially empty fitness list for the discussion.");
            }

            log.info("✅ Microservice result for discussion {}: fitness scores = {}, converged = {}", discussionId, response.getFitnessScores() ,response.isConverged());
            DiscussionFileLogger.logWebSocket(discussionId, "✅ Microservice result for discussion %d:" + response);

            return response;

        } catch (WebClientResponseException e) {
            log.error("❌ HTTP error from Python service (status {}): {}", e.getRawStatusCode(), e.getResponseBodyAsString());
        } catch (WebClientRequestException e) {
            log.error("❌ Failed to reach Python service: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error during PSO call for discussion " + discussionId, e);
        }

        return null;
    }

    public boolean resetDiscussionState(Long discussionId) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("discussionId", discussionId);

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/reset_discussion_state")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null && "reset".equals(response.get("status"))) {
                log.info("Successfully reset discussion state for discussionId {}", discussionId);
                DiscussionFileLogger.log(discussionId, String.format("Successfully reset discussion state for discussionId %d", discussionId));
                return true;
            } else {
                log.warn("Reset endpoint returned unexpected response: {}", response);
                DiscussionFileLogger.log(discussionId, String.format("Reset endpoint returned unexpected response: %s", response));
                return false;
            }
        } catch (Exception e) {
            log.error("Error calling /reset_discussion_state for discussionId {}: {}", discussionId, e.getMessage());
            return false;
        }
    }

}
