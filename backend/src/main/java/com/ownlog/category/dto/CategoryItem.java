package com.ownlog.category.dto;

import com.ownlog.category.domain.Category;
import com.ownlog.global.common.Visibility;

/** API 명세서 2.3 CategoryItem */
public record CategoryItem(Long id, String name, int displayOrder, boolean isDefault,
                           Visibility visibility, long postCount) {

    public static CategoryItem of(Category category, long postCount) {
        return new CategoryItem(category.getId(), category.getName(), category.getDisplayOrder(),
                category.isDefault(), category.getVisibility(), postCount);
    }
}
