package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;

import java.util.UUID;

public interface ChangeUserRoleUseCase {
    UserAccount changeRole(Actor actor, UUID targetId, Role newRole);
}
