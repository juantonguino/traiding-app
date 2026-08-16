package com.example.tradingbot

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.jupiter.api.Test

class ArchitectureTest {

    private val importedClasses: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.example.tradingbot")

    private val domainLayer = "..domain.."
    private val applicationLayer = "..application.."
    private val adapterInputLayer = "..adapter.input.."
    private val adapterOutputLayer = "..adapter.output.."

    @Test
    fun `domain depends only on itself and the JDK`() {
        classes().that().resideInAPackage(domainLayer)
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(domainLayer, "java..", "kotlin..", "org.jetbrains.annotations..", "jakarta..")
            .because("the domain layer must be technology-agnostic")
            .check(importedClasses)
    }

    @Test
    fun `application depends only on domain, its own ports and the JDK`() {
        classes().that().resideInAPackage(applicationLayer)
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                applicationLayer,
                domainLayer,
                "java..",
                "kotlin..",
                "org.slf4j..",
                "org.jetbrains.annotations..",
            )
            .because("application must depend on the domain and its own ports only")
            .check(importedClasses)
    }

    @Test
    fun `application never depends on adapters`() {
        noClasses().that().resideInAPackage(applicationLayer)
            .should().dependOnClassesThat().resideInAnyPackage(adapterInputLayer, adapterOutputLayer)
            .because("application must not depend on adapters (dependency inversion)")
            .check(importedClasses)
    }

    @Test
    fun `input adapters never depend on output adapters`() {
        noClasses().that().resideInAPackage(adapterInputLayer)
            .should().dependOnClassesThat().resideInAPackage(adapterOutputLayer)
            .because("adapters must be independent of each other")
            .check(importedClasses)
    }

    @Test
    fun `output adapters never depend on input adapters`() {
        noClasses().that().resideInAPackage(adapterOutputLayer)
            .should().dependOnClassesThat().resideInAPackage(adapterInputLayer)
            .because("adapters must be independent of each other")
            .check(importedClasses)
    }

    @Test
    fun `layers must be acyclic`() {
        slices()
            .matching("com.example.tradingbot.(*)..")
            .should().beFreeOfCycles()
            .because("layers must form a clean dependency graph")
            .check(importedClasses)
    }

    @Test
    fun `ports must be interfaces`() {
        classes()
            .that()
            .resideInAPackage("..application.port..")
            .and(simpleNameEndingWith("UseCase").or(simpleNameEndingWith("Port")))
            .should().beInterfaces()
            .because("ports are contracts, not implementations")
            .check(importedClasses)
    }

    @Test
    fun `port classes do not live in the domain layer`() {
        noClasses().that().resideInAPackage(domainLayer)
            .should().resideInAPackage("..application.port..")
            .check(importedClasses)
    }
}
