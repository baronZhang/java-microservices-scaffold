package com.wd.ms.user;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.wd.ms.common.test.MsArchitectureRules;

/** 分层依赖规则：违反时这个测试失败，构建不通过。规则本身在 common-test 的 MsArchitectureRules。 */
@AnalyzeClasses(packages = ArchitectureTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE = "com.wd.ms.user";

    @ArchTest
    static final ArchRule layers = MsArchitectureRules.layers(BASE);

    @ArchTest
    static final ArchRule domainIsFrameworkFree = MsArchitectureRules.domainIsFrameworkFree(BASE);

    @ArchTest
    static final ArchRule interfacesDoNotUseRepositories = MsArchitectureRules.interfacesDoNotUseRepositories(BASE);

    @ArchTest
    static final ArchRule noCycles = MsArchitectureRules.noCycles(BASE);
}
