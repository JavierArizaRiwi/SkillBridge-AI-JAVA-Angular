package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ChangeUserRoleUseCase;
import com.riwi.skillbridge.application.port.in.ChangeUserStatusUseCase;
import com.riwi.skillbridge.application.port.in.GetUserUseCase;
import com.riwi.skillbridge.application.port.in.ListUsersUseCase;
import com.riwi.skillbridge.application.port.out.UserAdminPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
import com.riwi.skillbridge.domain.policy.UserManagementPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class UserAdminService implements ListUsersUseCase, GetUserUseCase,
    ChangeUserStatusUseCase, ChangeUserRoleUseCase {

    // Logger dedicado a auditoría. Solo se registran ids y valores de estado/rol:
    // nunca emails, nombres, contraseñas ni JWT.
    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    static final Set<String> SORTABLE_FIELDS = Set.of("name", "email", "role", "status", "createdAt");

    private final UserAdminPort users;
    private final UserManagementPolicy policy;

    public UserAdminService(UserAdminPort users, UserManagementPolicy policy) {
        this.users = users;
        this.policy = policy;
    }

    @Override
    public PageResult<UserAccount> listUsers(Actor actor, PageQuery query) {
        policy.assertAdmin(actor);
        if (!SORTABLE_FIELDS.contains(query.sortBy())) {
            throw new IllegalArgumentException("Campo de orden no permitido: " + query.sortBy());
        }
        return users.findAll(query);
    }

    @Override
    public UserAccount getUser(Actor actor, UUID id) {
        policy.assertAdmin(actor);
        return find(id);
    }

    @Override
    public UserAccount changeStatus(Actor actor, UUID targetId, UserStatus newStatus) {
        try {
            policy.assertAdmin(actor);   // antes de buscar: un no-Admin no puede sondear ids
            UserAccount target = find(targetId);
            policy.assertCanChangeStatus(actor, target, newStatus);
            UserAccount saved = users.save(target.withStatus(newStatus));
            AUDIT.info("action=USER_STATUS_CHANGED outcome=SUCCESS actor={} target={} from={} to={}",
                actor.id(), targetId, target.status(), newStatus);
            return saved;
        } catch (RuntimeException e) {
            AUDIT.warn("action=USER_STATUS_CHANGED outcome=REJECTED actor={} target={} to={} reason={}",
                actor.id(), targetId, newStatus, e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public UserAccount changeRole(Actor actor, UUID targetId, Role newRole) {
        try {
            policy.assertAdmin(actor);
            UserAccount target = find(targetId);
            policy.assertCanChangeRole(actor, target, newRole);
            UserAccount saved = users.save(target.withRole(newRole));
            AUDIT.info("action=USER_ROLE_CHANGED outcome=SUCCESS actor={} target={} from={} to={}",
                actor.id(), targetId, target.role(), newRole);
            return saved;
        } catch (RuntimeException e) {
            AUDIT.warn("action=USER_ROLE_CHANGED outcome=REJECTED actor={} target={} to={} reason={}",
                actor.id(), targetId, newRole, e.getClass().getSimpleName());
            throw e;
        }
    }

    private UserAccount find(UUID id) {
        return users.findById(id).orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
    }
}
