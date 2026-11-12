package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import java.util.List;

public interface ListAllOfferingsUseCase {
    List<Offering> listAll(Actor actor);
}
