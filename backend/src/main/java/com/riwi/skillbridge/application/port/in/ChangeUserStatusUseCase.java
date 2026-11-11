package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;

import java.util.UUID;

public interface ChangeUserStatusUseCase {
    UserAccount changeStatus(Actor actor, UUID targetId, UserStatus newStatus);
}
