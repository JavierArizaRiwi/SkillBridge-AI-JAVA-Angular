package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.UserAccount;

import java.util.UUID;

public interface GetUserUseCase {
    UserAccount getUser(Actor actor, UUID id);
}
