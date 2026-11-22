package com.DsaDude.Contest_service.contest;

import org.springframework.data.mongodb.repository.MongoRepository;

interface ContestRepository extends MongoRepository<Contest, String> {}
