package net.enjoy.springboot.registrationlogin.service;

import net.enjoy.springboot.registrationlogin.dto.*;
import net.enjoy.springboot.registrationlogin.entity.Discussion;

import java.util.List;
import java.util.Map;

public interface SessionService {
    // List<QuestionDto> getQuestionsWithOptions(Long discussionId);

    Discussion findDiscussionById(Long discussionId);

    List<QuestionDto> getQuestionsWithAllOptions(Long discussionId);

    void addSliderData(Long discussionId, List<Double> sliders, List<String> optionLabels, Long questionId, int roundNum);
    List<SliderEntry> getLatestEntries(Long discussionId);

    void clearCachedRequiredNum(Long discussionId);

    Map<Long, List<SliderEntry>> getProcessableEntries();

    void clearSliderQueue(Long discussionId);

    List<String> getTopOptionTextsByFitness(Long questionId, int roundNumber);

    String getAnswerByQuestionId(Long questionId);

    void updateOptionFitnessScores(Long questionId, int roundNum, List<String> optionLabels, List<Double> fitnessScores);

    void updateAnswerByQuestionId(Long discussionId, Long questionId, String answer);

    String getMode(Long discussionId);
}