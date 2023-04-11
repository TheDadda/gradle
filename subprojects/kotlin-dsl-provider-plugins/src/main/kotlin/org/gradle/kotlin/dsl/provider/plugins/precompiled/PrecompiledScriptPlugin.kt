/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.kotlin.dsl.provider.plugins.precompiled


import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle

import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver
import org.gradle.kotlin.dsl.support.KotlinScriptType
import org.gradle.kotlin.dsl.support.KotlinScriptTypeMatch
import org.gradle.kotlin.dsl.support.uppercaseFirstChar

import org.gradle.util.internal.TextUtil.convertLineSeparatorsToUnix
import org.gradle.util.internal.TextUtil.normaliseFileSeparators

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.NameUtils

import java.io.File
import java.util.Locale


internal
data class PrecompiledScriptPlugin(internal val scriptFile: File) {

    val scriptFileName: String
        get() = scriptFile.name

    /**
     * Gradle plugin id inferred from the script file name and package declaration (if any).
     */
    val id by lazy {
        packagePrefixed(fileNameWithoutScriptExtension)
    }

    /**
     * Fully qualified name for the [Plugin] implementation class.
     *
     * The [Plugin] implementation class adapts the precompiled script class
     * to the Gradle [Plugin] protocol and it is automatically generated by
     * the `generateScriptPluginAdapters` task.
     */
    val implementationClass by lazy {
        packagePrefixed(simplePluginAdapterClassName)
    }

    val simplePluginAdapterClassName by lazy {
        fileNameWithoutScriptExtension
            .kebabCaseToPascalCase()
            .asJavaIdentifier() + "Plugin"
    }

    private
    val fileNameWithoutScriptExtension by lazy {
        scriptFileName.removeSuffix(scriptExtension)
            .also(::validateFileNameWithoutScriptExtension)
    }

    private
    fun validateFileNameWithoutScriptExtension(fileNameWithoutScriptExtension: String) {
        if (fileNameWithoutScriptExtension.isEmpty()) {
            val scriptTypeMessage = when (scriptType) {
                KotlinScriptType.INIT -> "<plugin-id>.init.gradle.kts"
                KotlinScriptType.SETTINGS -> "<plugin-id>.settings.gradle.kts"
                KotlinScriptType.PROJECT -> TODO("This should not happen, please report an issue.")
            }
            throw GradleException("Precompiled script '${normaliseFileSeparators(scriptFile.absolutePath)}' file name is invalid, please rename it to '$scriptTypeMessage'.")
        }
    }

    val targetType by lazy {
        when (scriptType) {
            KotlinScriptType.PROJECT -> Project::class.qualifiedName
            KotlinScriptType.SETTINGS -> Settings::class.qualifiedName
            KotlinScriptType.INIT -> Gradle::class.qualifiedName
        }
    }

    val scriptType
        get() = scriptTypeMatch.scriptType

    private
    val scriptExtension
        get() = scriptTypeMatch.match.value

    private
    val scriptTypeMatch by lazy {
        KotlinScriptTypeMatch.forName(scriptFileName)!!
    }

    /**
     * Fully qualified name
     */
    val compiledScriptTypeName by lazy {
        packagePrefixed(scriptClassNameForFile(scriptFile))
    }

    val packageName: String? by lazy {
        packageNameOf(scriptText)
    }

    val hashString by lazy {
        PrecompiledScriptDependenciesResolver.hashOfNormalisedString(scriptText)
    }

    val scriptText: String
        get() = convertLineSeparatorsToUnix(scriptFile.readText())

    private
    fun packagePrefixed(id: String) =
        packageName?.let { "$it.$id" } ?: id
}


internal
fun scriptPluginFilesOf(list: List<PrecompiledScriptPlugin>) =
    list.map { it.scriptFile }.toSet()


private
fun packageNameOf(code: String): String? =
    KotlinLexer().run {
        start(code)
        skipWhiteSpaceAndComments()
        when (tokenType) {
            KtTokens.PACKAGE_KEYWORD -> {
                advance()
                skipWhiteSpaceAndComments()
                parseQualifiedName()
            }

            else -> null
        }
    }


private
fun KotlinLexer.parseQualifiedName(): String =
    StringBuilder().run {
        while (tokenType == KtTokens.IDENTIFIER || tokenType == KtTokens.DOT) {
            append(tokenText)
            advance()
        }
        toString()
    }


private
fun KotlinLexer.skipWhiteSpaceAndComments() {
    while (tokenType in KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET) {
        advance()
    }
}


private
fun scriptClassNameForFile(file: File) =
    NameUtils.getScriptNameForFile(file.name).asString()


private
fun CharSequence.kebabCaseToPascalCase() =
    kebabCaseToCamelCase().uppercaseFirstChar()


private
fun CharSequence.kebabCaseToCamelCase() =
    replace("-[a-z]".toRegex()) { it.value.drop(1).uppercase(Locale.US) }


private
fun CharSequence.asJavaIdentifier() =
    replaceBy { if (it.isJavaIdentifierPart()) it else '_' }.let {
        if (it.first().isJavaIdentifierStart()) it
        else "_$it"
    }


private
inline fun CharSequence.replaceBy(f: (Char) -> Char) =
    StringBuilder(length).let { builder ->
        forEach { char -> builder.append(f(char)) }
        builder.toString()
    }
