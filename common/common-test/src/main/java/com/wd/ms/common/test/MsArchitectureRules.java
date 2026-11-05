package com.wd.ms.common.test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;

/**
 * 每个服务内部的分层规则，写成可执行的测试。服务里只要写一个测试类引用这些规则，违反就编译通过但测试失败。
 *
 * <pre>
 * interfaces（入站适配器：HTTP 接口、消息监听、定时任务触发）
 *     ↓
 * application（用例编排、事务边界）
 *     ↓
 * domain（领域模型、领域规则、仓储和外部依赖的接口）  ← infrastructure（出站适配器：数据库、HTTP 客户端、消息投递）实现这些接口
 * </pre>
 *
 * <p>依赖倒置：application 只依赖 domain 里定义的接口（如 InventoryPort），具体是 HTTP 还是 Kafka 由 infrastructure 决定。
 */
public final class MsArchitectureRules {

    private MsArchitectureRules() {}

    /** 规则 1：分层依赖方向。启动类、配置类放在根包，不属于任何一层 */
    public static ArchRule layers(String basePackage) {
        return layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Interfaces")
                .definedBy(basePackage + ".interfaces..")
                .layer("Application")
                .definedBy(basePackage + ".application..")
                .layer("Domain")
                .definedBy(basePackage + ".domain..")
                .layer("Infrastructure")
                .definedBy(basePackage + ".infrastructure..")
                .whereLayer("Interfaces")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Application")
                .mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers("Application", "Interfaces", "Infrastructure")
                .whereLayer("Infrastructure")
                .mayNotBeAccessedByAnyLayer()
                .because("依赖只能从外往里：接口层 → 应用层 → 领域层，基础设施层实现领域层定义的接口");
    }

    /** 规则 2：领域层是纯 Java，不依赖任何框架。换框架、写单元测试都不受影响 */
    public static ArchRule domainIsFrameworkFree(String basePackage) {
        return noClasses()
                .that()
                .resideInAPackage(basePackage + ".domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "jakarta.persistence..",
                        "tools.jackson..",
                        "org.apache.kafka..")
                .because("领域层只表达业务规则，技术细节都放在基础设施层");
    }

    /** 规则 3：接口层不能绕过应用层直接操作仓储 */
    public static ArchRule interfacesDoNotUseRepositories(String basePackage) {
        return noClasses()
                .that()
                .resideInAPackage(basePackage + ".interfaces..")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .because("Controller 直接调仓储会绕过事务边界和业务规则");
    }

    /** 规则 4：包之间不能有循环依赖 */
    public static ArchRule noCycles(String basePackage) {
        return slices().matching(basePackage + ".(*)..").should().beFreeOfCycles();
    }
}
