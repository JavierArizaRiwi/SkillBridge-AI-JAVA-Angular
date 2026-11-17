package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ProviderRequest;

public interface ListPendingProviderRequestsUseCase {
    PageResult<ProviderRequest> listPending(Actor actor, PageQuery query);
}
