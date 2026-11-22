package com.DsaDude.Execution_Submission_Service.Repository;

import com.DsaDude.Execution_Submission_Service.Model.Submission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends MongoRepository<Submission, String> {
    public List<Submission> findByUserId(int userId);
    List<Submission> findByProblemSlugAndUserId(String problemSlug, int userId);
    Optional<Submission> findByIdAndUserId(String id, int userId);
    int countByUserId(int userId);
}
