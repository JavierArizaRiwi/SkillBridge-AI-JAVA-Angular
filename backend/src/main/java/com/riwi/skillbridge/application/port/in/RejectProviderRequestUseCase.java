package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.ProviderRequest;

import java.util.UUID;

public interface RejectProviderRequestUseCase {
    ProviderRequest reject(Actor actor, UUID requestId, String reason);
}
