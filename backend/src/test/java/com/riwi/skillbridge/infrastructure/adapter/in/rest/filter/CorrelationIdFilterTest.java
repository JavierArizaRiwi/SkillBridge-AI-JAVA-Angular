package com.riwi.skillbridge.infrastructure.adapter.in.rest.filter;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void shouldPropagateClientCorrelationIdAndClearItAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-Id", "abc-123");

        FilterChain chain = (ignoredRequest, ignoredResponse) ->
            assertThat(CorrelationIdHolder.get()).isEqualTo("abc-123");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("abc-123");
        assertThat(CorrelationIdHolder.get()).isNull();
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (request, ignoredResponse) ->
            assertThat(CorrelationIdHolder.get()).isNotBlank());

        assertThat(response.getHeader("X-Correlation-Id")).isNotBlank();
        assertThat(CorrelationIdHolder.get()).isNull();
    }
}
