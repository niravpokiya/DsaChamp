package com.DsaDude.Contest_service.registration;

import org.springframework.data.mongodb.repository.MongoRepository;

interface ContestRegistrationRepository extends MongoRepository<ContestRegistration, String> {}
