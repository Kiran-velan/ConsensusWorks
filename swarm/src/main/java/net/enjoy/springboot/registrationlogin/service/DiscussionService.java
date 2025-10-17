package net.enjoy.springboot.registrationlogin.service;

import net.enjoy.springboot.registrationlogin.entity.Discussion;
import net.enjoy.springboot.registrationlogin.entity.Question;
import net.enjoy.springboot.registrationlogin.dto.DiscussionDto;
import net.enjoy.springboot.registrationlogin.dto.QuestionDto;
import net.enjoy.springboot.registrationlogin.entity.User;
import java.util.List;

public interface DiscussionService {
    Discussion findDiscussionByNameAndCode(String discussionName, String uniqueCode);
    //List<DiscussionSummaryDto> getPastDiscussions(Long userId);
    List<Discussion> getAllDiscussions();
//    int calculateDuration(int totalOptions);

    //    boolean validateDiscussion(String discussionName, String uniqueCode);
    List<Discussion> getDiscussionsByUserEmail(String email);
    Discussion findDiscussionById(Long discussionId);

    void deleteDiscussion(Long discussionId);

    Question saveQuestion(Question question);
    Discussion saveDiscussion(DiscussionDto discussionDto, User user);

    Discussion updateDiscussionById(Long discussionId, DiscussionDto discussionDto);

    DiscussionDto getDiscussionById(Long discussionId);

    List<QuestionDto> getQuestionsByDiscussionId(Long discussionId);
}
