package com.mediaproject.prompthubs.domain.review.repository;

import com.mediaproject.prompthubs.domain.review.entity.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewCommentRepository extends JpaRepository<ReviewComment, UUID> {

    @Query("SELECT c FROM ReviewComment c WHERE c.review.id = :reviewId ORDER BY c.lineStart ASC, c.createdAt ASC")
    List<ReviewComment> findByReviewIdOrderByLineStartAsc(@Param("reviewId") UUID reviewId);

    @Query("SELECT c FROM ReviewComment c WHERE c.id = :id AND c.review.id = :reviewId")
    Optional<ReviewComment> findByIdAndReviewId(@Param("id") UUID id, @Param("reviewId") UUID reviewId);

    @Query("SELECT COUNT(c) FROM ReviewComment c WHERE c.review.id = :reviewId AND c.resolved = false")
    long countUnresolvedByReviewId(@Param("reviewId") UUID reviewId);
}