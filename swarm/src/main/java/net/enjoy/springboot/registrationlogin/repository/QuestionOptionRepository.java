package net.enjoy.springboot.registrationlogin.repository;

import net.enjoy.springboot.registrationlogin.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    // ✅ Retrieve all options for a specific question
    @Query("SELECT qo FROM QuestionOption qo WHERE qo.question.id = :questionId")
    List<QuestionOption> findByQuestionId(@Param("questionId") Long questionId);

    // ✅ Recommended: Improved deletion with @Modifying & @Transactional
    @Modifying
    @Transactional
    @Query("DELETE FROM QuestionOption qo WHERE qo.question.id = :questionId")
    void deleteByQuestionId(Long questionId);

    // ✅ Optional: Alternative efficient bulk deletion (Spring Data JPA derived method)
    void deleteAllByQuestionId(Long questionId);

    List<QuestionOption> findByQuestion_Discussion_DiscussionId(Long discussionId);

    List<QuestionOption> findByQuestion_Id(Long questionId);

    Optional<QuestionOption> findByQuestionIdAndOptionValue(Long questionId, String optionValue);

}
