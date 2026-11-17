package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.TrendingServiceResponse;

import java.util.List;

/**
 * Input port for querying trending services.
 * Returns a ranked list of offerings sorted by trendScore descending.
 */
public interface GetTrendingServicesUseCase {
    List<TrendingServiceResponse> getTrending();
}
