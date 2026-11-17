package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import java.util.List;

public interface ListOfferingsUseCase {
    List<Offering> listActive();

    PageResult<Offering> listActive(PageQuery query);
}
