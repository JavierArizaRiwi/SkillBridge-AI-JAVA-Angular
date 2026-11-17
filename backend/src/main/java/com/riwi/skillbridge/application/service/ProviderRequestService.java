package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ApproveProviderRequestUseCase;
import com.riwi.skillbridge.application.port.in.CreateProviderRequestUseCase;
import com.riwi.skillbridge.application.port.in.GetMyProviderRequestsUseCase;
import com.riwi.skillbridge.application.port.in.ListPendingProviderRequestsUseCase;
import com.riwi.skillbridge.application.port.in.RejectProviderRequestUseCase;
import com.riwi.skillbridge.application.port.out.ProviderRequestPort;
import com.riwi.skillbridge.application.port.out.UserAdminPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.ProviderRequestStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.policy.ProviderRequestPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ProviderRequestService implements
    CreateProviderRequestUseCase,
    GetMyProviderRequestsUseCase,
    ListPendingProviderRequestsUseCase,
    ApproveProviderRequestUseCase,
    RejectProviderRequestUseCase {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private final ProviderRequestPort providerRequests;
    private final UserAdminPort users;
    private final ProviderRequestPolicy policy;
    private final Clock clock;

    public ProviderRequestService(ProviderRequestPort providerRequests,
                                  UserAdminPort users,
                                  ProviderRequestPolicy policy,
                                  @Autowired(required = false) Clock clock) {
        this.providerRequests = providerRequests;
        this.users = users;
        this.policy = policy;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    @Transactional
    public ProviderRequest create(Actor actor, String message) {
        try {
            UserAccount user = users.findById(actor.id())
                .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
            policy.assertCanRequest(actor, user);

            ProviderRequest request = ProviderRequest.create(actor.id(), message, clock.instant());
            ProviderRequest saved = providerRequests.save(request);
            AUDIT.info("action=PROVIDER_REQUEST_CREATED outcome=SUCCESS actor={} requestId={}",
                actor.id(), saved.id());
            return saved;
        } catch (RuntimeException e) {
            AUDIT.warn("action=PROVIDER_REQUEST_CREATED outcome=REJECTED actor={} reason={}",
                actor.id(), e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public List<ProviderRequest> getMyRequests(Actor actor) {
        return providerRequests.findByUserId(actor.id());
    }

    @Override
    public PageResult<ProviderRequest> listPending(Actor actor, PageQuery query) {
        policy.assertAdmin(actor);
        return providerRequests.findByStatus(ProviderRequestStatus.PENDING, query);
    }

    @Override
    @Transactional
    public ProviderRequest approve(Actor actor, UUID requestId, String reason) {
        try {
            policy.assertAdmin(actor);
            ProviderRequest request = providerRequests.findById(requestId)
                .orElseThrow(() -> new DomainNotFoundException("Solicitud no encontrada"));
            policy.assertCanDecide(actor, request);

            UserAccount applicant = users.findById(request.userId())
                .orElseThrow(() -> new DomainNotFoundException("Usuario solicitante no encontrado"));
            policy.assertCanApprove(applicant);

            ProviderRequest approved = request.approve(actor.id(), reason, clock.instant());
            users.save(applicant.withRole(Role.PROVIDER));
            ProviderRequest saved = providerRequests.save(approved);

            AUDIT.info("action=PROVIDER_REQUEST_APPROVED outcome=SUCCESS actor={} requestId={} targetUser={}",
                actor.id(), requestId, applicant.id());
            return saved;
        } catch (RuntimeException e) {
            AUDIT.warn("action=PROVIDER_REQUEST_APPROVED outcome=REJECTED actor={} requestId={} reason={}",
                actor.id(), requestId, e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    @Transactional
    public ProviderRequest reject(Actor actor, UUID requestId, String reason) {
        try {
            policy.assertAdmin(actor);
            ProviderRequest request = providerRequests.findById(requestId)
                .orElseThrow(() -> new DomainNotFoundException("Solicitud no encontrada"));
            policy.assertCanDecide(actor, request);

            ProviderRequest rejected = request.reject(actor.id(), reason, clock.instant());
            ProviderRequest saved = providerRequests.save(rejected);

            AUDIT.info("action=PROVIDER_REQUEST_REJECTED outcome=SUCCESS actor={} requestId={} targetUser={}",
                actor.id(), requestId, request.userId());
            return saved;
        } catch (RuntimeException e) {
            AUDIT.warn("action=PROVIDER_REQUEST_REJECTED outcome=REJECTED actor={} requestId={} reason={}",
                actor.id(), requestId, e.getClass().getSimpleName());
            throw e;
        }
    }
}
