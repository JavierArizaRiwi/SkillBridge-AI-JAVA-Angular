package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import java.util.List;

public interface ListMyOfferingsUseCase {
    List<Offering> listMine(Actor actor);

    PageResult<Offering> listMine(Actor actor, PageQuery query);
}
