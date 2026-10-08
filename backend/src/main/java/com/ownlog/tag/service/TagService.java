package com.ownlog.tag.service;

import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.error.ErrorResponse.FieldError;
import com.ownlog.tag.domain.Tag;
import com.ownlog.tag.repository.TagRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagService {

    public static final int MAX_TAGS_PER_POST = 10;

    private final TagRepository tagRepository;

    /** 정리(앞뒤 공백, 빈 값, 대소문자 무시 중복 제거) → 개수 검증 → 찾거나 만들기 */
    @Transactional
    public Set<Tag> resolveTags(List<String> rawNames) {
        Map<String, String> unique = new LinkedHashMap<>();   // 소문자 → 처음 입력한 표기
        if (rawNames != null) {
            for (String raw : rawNames) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String name = raw.strip();
                unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        }
        if (unique.size() > MAX_TAGS_PER_POST) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    List.of(new FieldError("tags", "태그는 최대 10개까지 붙일 수 있어요.")));
        }

        Set<Tag> tags = new LinkedHashSet<>();
        for (String name : unique.values()) {
            tagRepository.insertIfAbsent(name);
            tags.add(tagRepository.findByNameIgnoreCase(name).orElseThrow());
        }
        return tags;
    }
}
