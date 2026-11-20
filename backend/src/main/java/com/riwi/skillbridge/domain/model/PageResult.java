package com.riwi.skillbridge.domain.model;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
}
