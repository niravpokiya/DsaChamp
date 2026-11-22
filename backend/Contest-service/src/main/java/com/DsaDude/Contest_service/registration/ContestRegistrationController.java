package com.DsaDude.Contest_service.registration;

import com.DsaDude.Contest_service.ContestAccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contests/{id}/registration")
class ContestRegistrationController {
    private final ContestAccess access;
    private final ContestRegistrationService registration;

    ContestRegistrationController(ContestAccess access, ContestRegistrationService registration) {
        this.access = access;
        this.registration = registration;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void join(@PathVariable String id,
              @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key,
              @RequestHeader(value = "X-USER-ID", required = false) Integer userId) {
        registration.join(id, access.requireUser(key, userId));
    }

    @GetMapping
    RegistrationStatus status(@PathVariable String id,
                              @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key,
                              @RequestHeader(value = "X-USER-ID", required = false) Integer userId) {
        return new RegistrationStatus(registration.hasJoined(id, access.requireUser(key, userId)));
    }

    record RegistrationStatus(boolean joined) {}
}
