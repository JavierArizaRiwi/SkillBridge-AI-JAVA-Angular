package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.ProviderRequestStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderRequestPort {
    /** Lanza BusinessRuleException si el usuario ya tiene una solicitud pendiente. */
    ProviderRequest save(ProviderRequest request);

    Optional<ProviderRequest> findById(UUID id);

    /** Solicitudes de un usuario, de la más reciente a la más antigua. */
    List<ProviderRequest> findByUserId(UUID userId);

    PageResult<ProviderRequest> findByStatus(ProviderRequestStatus status, PageQuery query);
}
