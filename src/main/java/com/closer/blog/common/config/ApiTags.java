package com.closer.blog.common.config;

/**
 * Swagger UI에서 API를 묶는 태그 이름. 컨트롤러에 @Tag(name = ApiTags.POST)처럼 붙이고,
 * 설명과 보이는 순서는 OpenApiConfig가 정한다. 묶음은 docs/api-spec.md 5~9절을 따르고 휴지통만 따로 뗐다.
 */
public final class ApiTags {

    public static final String AUTH = "인증";

    public static final String PROFILE = "내 정보·사람";

    public static final String FOLDER = "폴더·경로";

    public static final String POST = "글";

    public static final String TRASH = "휴지통";

    public static final String SEARCH = "검색·태그";

    private ApiTags() {
    }

}
