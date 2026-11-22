package com.DsaDude.Execution_Submission_Service.Repository;

import com.DsaDude.Execution_Submission_Service.Model.Submission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Aggregation;
import com.DsaDude.Execution_Submission_Service.DTO.ContestScore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends MongoRepository<Submission, String> {
    public List<Submission> findByUserId(int userId);
    List<Submission> findByProblemSlugAndUserId(String problemSlug, int userId);
    Optional<Submission> findByIdAndUserId(String id, int userId);
    int countByUserId(int userId);

    boolean existsByContestIdAndStatus(String contestId, String status);

    @Aggregation(pipeline = {
            "{ $match: { contestId: ?0, status: 'COMPLETED', verdict: 'ACCEPTED' } }",
            "{ $group: { _id: { userId: '$userId', questionId: '$questionId' }, "
                    + "points: { $max: '$contestPoints' }, firstAcceptedAt: { $min: '$submissionTime' } } }",
            "{ $group: { _id: '$_id.userId', points: { $sum: '$points' }, solved: { $sum: 1 }, "
                    + "scoreReachedAt: { $max: '$firstAcceptedAt' } } }",
            "{ $project: { _id: 0, userId: '$_id', points: 1, solved: 1, scoreReachedAt: 1 } }",
            "{ $sort: { points: -1, scoreReachedAt: 1, userId: 1 } }",
            "{ $skip: ?1 }",
            "{ $limit: ?2 }"
    })
    List<ContestScore> findContestScores(String contestId, long offset, int limit);
}
