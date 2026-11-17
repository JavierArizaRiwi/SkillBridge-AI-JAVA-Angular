package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.ProviderRequest;

import java.util.List;

public interface GetMyProviderRequestsUseCase {
    List<ProviderRequest> getMyRequests(Actor actor);
}
