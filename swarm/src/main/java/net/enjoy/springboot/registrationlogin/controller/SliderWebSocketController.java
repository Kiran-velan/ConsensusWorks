package net.enjoy.springboot.registrationlogin.controller;

import net.enjoy.springboot.registrationlogin.dto.SliderDataDto;
import net.enjoy.springboot.registrationlogin.service.SessionService;
import net.enjoy.springboot.registrationlogin.session.DiscussionFileLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class SliderWebSocketController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SliderWebSocketController.class);

    private final SessionService sessionService;

    @Autowired
    public SliderWebSocketController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @MessageMapping("/slider/submit") // Client sends to: /app/slider/submit
    public void receiveSliderData(@Payload SliderDataDto sliderDataDto) {
        Long discussionId = sliderDataDto.getDiscussionId();
        List<Double> sliders = sliderDataDto.getSliders();
        List<String> optionLabels = sliderDataDto.getOptionLabels();
        Long questionId = sliderDataDto.getQuestionId();
        int roundNum = sliderDataDto.getRoundNum();

        if (discussionId == null || sliders == null || sliders.size() != 6 || optionLabels == null || questionId == null) {
            LOGGER.warn("Invalid slider data received: {}", sliderDataDto);
            DiscussionFileLogger.logWebSocket(discussionId, "Invalid slider data received: " + sliderDataDto);
            return;
        }

        LOGGER.info("Received sliders from discussion {}: {}, with options: {}for questionId: {}, roundNum: {}",
                discussionId, sliders, optionLabels, questionId, roundNum);
        DiscussionFileLogger.logWebSocket(discussionId, "Received sliders from discussion " + discussionId + ": " + sliders + ", with options: " + optionLabels + "for questionId: " + questionId + ", roundNum: " + roundNum);

        sessionService.addSliderData(discussionId, sliders, optionLabels, questionId, roundNum);
    }

    @MessageMapping("/heartbeat/{discussionId}")
    public void handleHeartbeat(@DestinationVariable String discussionId) {
        // Empty method - just receives the heartbeat, doesn't send anything back
    }
}