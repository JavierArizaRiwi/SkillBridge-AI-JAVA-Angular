package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import java.util.List;

public interface ListMyOfferingsUseCase {
    List<Offering> listMine(Actor actor);
}
