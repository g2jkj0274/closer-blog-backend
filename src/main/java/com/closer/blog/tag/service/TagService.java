package com.closer.blog.tag.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.closer.blog.tag.domain.Tag;
import com.closer.blog.tag.domain.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    private final Clock clock;

    /**
     * 이름 목록을 태그로 바꾼다. 없는 태그는 만든다. 순서를 지키고, 대소문자만 다른 이름은 하나로 합친다.
     */
    @Transactional
    public List<Tag> resolve(List<String> names) {
        List<Tag> tags = new ArrayList<>();
        for (String name : normalize(names)) {
            tagRepository.insertIfAbsent(name, clock.instant());
            tags.add(tagRepository.findByNameIgnoreCase(name).orElseThrow());
        }
        return tags;
    }

    // 앞의 #을 떼고, 대소문자만 다른 이름은 처음 것만 남긴다
    private static List<String> normalize(List<String> names) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String name : names) {
            String stripped = name.startsWith("#") ? name.substring(1) : name;
            unique.putIfAbsent(stripped.toLowerCase(Locale.ROOT), stripped);
        }
        return List.copyOf(unique.values());
    }

}
