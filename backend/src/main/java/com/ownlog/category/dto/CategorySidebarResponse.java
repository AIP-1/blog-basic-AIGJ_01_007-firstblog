package com.ownlog.category.dto;

import java.util.List;

/** API 명세서 5.3 응답 */
public record CategorySidebarResponse(long totalPostCount, List<CategoryItem> categories) {
}
