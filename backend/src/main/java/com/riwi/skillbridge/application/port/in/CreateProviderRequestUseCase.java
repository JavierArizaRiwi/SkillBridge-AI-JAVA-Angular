package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.ProviderRequest;

public interface CreateProviderRequestUseCase {
    ProviderRequest create(Actor actor, String message);
}
