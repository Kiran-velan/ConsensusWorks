package net.enjoy.springboot.registrationlogin.repository;

import net.enjoy.springboot.registrationlogin.entity.Discussion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.Set;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {

    // ✅ Efficient Existence Check for Validation
    boolean existsByDiscussionNameAndUniqueCode(String discussionName, String uniqueCode);

    boolean existsByUniqueCode(String uniqueCode);

    Optional<Discussion> findByUniqueCode(String uniqueCode);

    // ✅ Enhanced Search for Discussion Name
    List<Discussion> findByDiscussionNameContainingIgnoreCase(String discussionName);

    // ✅ Delete Discussion by Unique Code
    int deleteByUniqueCode(String uniqueCode);

    // ✅ Optimized Pagination for Large Data
    Page<Discussion> findAll(Pageable pageable);

    List<Discussion> findAllByUserIdOrderByDiscussionIdDesc(Long userId);

    List<Discussion> findAllById(Iterable<Long> ids);

    // 🔹 FIXED: Corrected scheduledTime to LocalDateTime
    List<Discussion> findByScheduledTimeOrderByScheduledTimeAsc(OffsetDateTime scheduledTime);

    // 🔹 Renamed for Clarity
    List<Discussion> findAllByOrderByDurationAsc();

    // 🔹 New: Filter Discussions Within a Date Range
    List<Discussion> findByScheduledTimeBetween(OffsetDateTime start, OffsetDateTime end);

    // 🔹 Flexible Pagination
    Page<Discussion> findByUserId(Long userId, Pageable pageable);

    Page<Discussion> findByScheduledTime(OffsetDateTime scheduledTime, Pageable pageable);

    // 🔹 New: Filter by User and Date Range with Pagination
    Page<Discussion> findByUserIdAndScheduledTimeBetween(Long userId, OffsetDateTime start, OffsetDateTime end, Pageable pageable);

    // 🔹 New: Filter Discussions by Duration Greater Than
    Page<Discussion> findByDurationGreaterThan(int duration, Pageable pageable);

    // 🔹 New: Filter Discussions by Duration Less Than
    Page<Discussion> findByDurationLessThan(int duration, Pageable pageable);

    // 🔹 New: Existence check for combination of discussionName and scheduledTime
    boolean existsByDiscussionNameAndScheduledTime(String discussionName, OffsetDateTime scheduledTime);

    // 🔹 Optional Custom Query Example for Filtering and Sorting
    @Query("SELECT d FROM Discussion d WHERE d.scheduledTime BETWEEN :start AND :end ORDER BY d.scheduledTime ASC")
    List<Discussion> findDiscussionsWithinTimeRange(LocalDateTime start, LocalDateTime end);

    @Query("SELECT d FROM Discussion d WHERE d.discussionName = :discussionName AND d.uniqueCode = :uniqueCode")
    Discussion findByDiscussionNameAndUniqueCode(@Param("discussionName") String discussionName,
                                                 @Param("uniqueCode") String uniqueCode);

    @Query("SELECT d.discussionId, d.scheduledTime, d.duration FROM Discussion d WHERE d.discussionId IN :discussionIds")
    List<Object[]> findDiscussionTimingsByIds(@Param("discussionIds") Set<Long> discussionIds);

    //    findById
    //    No, you don’t need to explicitly write findById in DiscussionRepository because it's already provided by Spring Data JPA.

}