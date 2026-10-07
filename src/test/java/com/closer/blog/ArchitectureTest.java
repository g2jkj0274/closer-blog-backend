package com.closer.blog;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.List;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

/**
 * 패키지 사이의 의존 방향을 고정한다 (docs/api-spec.md 2절 "패키지 구조").
 * 스프링을 띄우지 않고 컴파일된 클래스만 읽으므로 빠르다.
 * <p>
 * 한계: static final 문자열 상수(PostVisibility.READABLE_SQL 등)는 컴파일할 때 쓰는 쪽에 값이 복사되므로
 * 그 상수를 통한 의존은 이 테스트에 보이지 않는다.
 */
@AnalyzeClasses(packagesOf = BlogServerApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.closer.blog.";

    private static final List<String> DOMAINS =
            List.of("user", "tag", "folder", "auth", "post", "fs", "search", "profile");

    // 2절의 표: 패키지마다 의존해도 되는 패키지. common은 모두가 쓸 수 있다

    @ArchTest
    static final ArchRule userDependencies = mayDependOnlyOn("user");

    @ArchTest
    static final ArchRule tagDependencies = mayDependOnlyOn("tag");

    @ArchTest
    static final ArchRule folderDependencies = mayDependOnlyOn("folder", "user");

    @ArchTest
    static final ArchRule authDependencies = mayDependOnlyOn("auth", "user", "folder");

    @ArchTest
    static final ArchRule postDependencies = mayDependOnlyOn("post", "user", "folder", "tag");

    @ArchTest
    static final ArchRule fsDependencies = mayDependOnlyOn("fs", "user", "folder", "post");

    @ArchTest
    static final ArchRule searchDependencies = mayDependOnlyOn("search", "user", "folder", "post", "tag");

    @ArchTest
    static final ArchRule profileDependencies = mayDependOnlyOn("profile", "user", "folder", "post");

    @ArchTest
    static final ArchRule commonKnowsNoDomain = noClasses().that().resideInAPackage(ROOT + "common..")
            .should().dependOnClassesThat().resideInAnyPackage(packages(DOMAINS))
            .as("common은 도메인을 모른다");

    // 위 표가 지켜지면 순환은 생기지 않지만, 표를 고칠 때를 위해 따로 둔다
    @ArchTest
    static final ArchRule noCycles = slices().matching(ROOT + "(*)..").should().beFreeOfCycles()
            .as("패키지 사이에 순환 의존이 없다");

    // 다른 패키지는 그 패키지의 service로 부른다. 저장소를 직접 부르지 않는다
    @ArchTest
    static final ArchRule repositoriesStayInTheirPackage = CompositeArchRule
            .of(DOMAINS.stream().map(ArchitectureTest::repositoriesOnlyUsedInside).toList())
            .as("저장소(*Repository)는 자기 패키지 안에서만 쓴다");

    // 각 패키지 안은 controller → service → domain(엔티티, 저장소) 방향이다

    @ArchTest
    static final ArchRule nothingDependsOnControllers = noClasses().that().resideOutsideOfPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .as("controller는 아무도 부르지 않는다");

    @ArchTest
    static final ArchRule controllersGoThroughServices = noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .as("controller는 저장소를 직접 부르지 않고 service를 거친다");

    @ArchTest
    static final ArchRule domainIsInnermost = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..dto..", "..controller..")
            .as("domain은 service, dto, controller를 모른다");

    private static ArchRule mayDependOnlyOn(String pkg, String... allowed) {
        List<String> forbidden = DOMAINS.stream()
                .filter(domain -> !domain.equals(pkg) && !List.of(allowed).contains(domain))
                .toList();
        String description = (allowed.length == 0) ? pkg + "는 다른 도메인에 의존하지 않는다"
                : pkg + "는 " + String.join(", ", allowed) + "에만 의존한다";
        return noClasses().that().resideInAPackage(ROOT + pkg + "..")
                .should().dependOnClassesThat().resideInAnyPackage(packages(forbidden))
                .as(description);
    }

    private static ArchRule repositoriesOnlyUsedInside(String pkg) {
        return noClasses().that().resideOutsideOfPackage(ROOT + pkg + "..")
                .should().dependOnClassesThat(resideInAPackage(ROOT + pkg + "..").and(simpleNameEndingWith("Repository")))
                .as(pkg + "의 저장소는 " + pkg + " 안에서만 쓴다");
    }

    private static String[] packages(List<String> domains) {
        return domains.stream().map(domain -> ROOT + domain + "..").toArray(String[]::new);
    }

}
