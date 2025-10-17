package net.enjoy.springboot.registrationlogin.repository;

import net.enjoy.springboot.registrationlogin.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    // ✅ Fetch questions by discussion ID
    List<Question> findByDiscussion_DiscussionId(Long discussionId);

    // 🔹 NEW: Search for questions by partial text within a discussion
    List<Question> findByDiscussion_DiscussionIdAndQuestionTextContainingIgnoreCase(Long discussionId, String text);

    // ✅ Delete questions by discussion ID (Efficient bulk deletion)
    void deleteAllByDiscussion_DiscussionId(Long discussionId);

    // ✅ Sort questions alphabetically by text for better organization
    List<Question> findByDiscussion_DiscussionIdOrderByQuestionTextAsc(Long discussionId);

    // ✅ Count total questions linked to a specific discussion
    long countByDiscussion_DiscussionId(Long discussionId);

    // 🔹 NEW: Pagination for large question sets
    Page<Question> findByDiscussion_DiscussionId(Long discussionId, Pageable pageable);

    // 🔹 NEW: Count questions by partial text within a discussion (for filtering or search)
    long countByDiscussion_DiscussionIdAndQuestionTextContainingIgnoreCase(Long discussionId, String text);

    // 🔹 NEW: Delete a specific question by its ID (if needed for finer control)
    void deleteById(Long id);

    @Modifying
    @Query("DELETE FROM Question q WHERE q.discussion.discussionId = :discussionId")
    void deleteByDiscussionId(@Param("discussionId") Long discussionId);

    @Modifying
    @Query("DELETE FROM QuestionOption qo WHERE qo.question.id IN " +
            "(SELECT q.id FROM Question q WHERE q.discussion.discussionId = :discussionId)")
    void deleteQuestionOptionsByDiscussionId(@Param("discussionId") Long discussionId);

    List<Question> findByDiscussion_DiscussionIdOrderByPositionAsc(Long discussionId);

    @Query("SELECT q FROM Question q WHERE q.discussion.id = :discussionId ORDER BY q.position ASC")
    List<Question> findByDiscussionIdOrdered(@Param("discussionId") Long discussionId);

    Optional<Question> findByDiscussionDiscussionIdAndId(Long discussionId, Long questionId);

}
