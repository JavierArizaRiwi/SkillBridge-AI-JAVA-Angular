package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.ProviderRequest;

import java.util.UUID;

public interface ApproveProviderRequestUseCase {
    ProviderRequest approve(Actor actor, UUID requestId, String reason);
}
