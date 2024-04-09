/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.project

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.internal.declarativedsl.analysis.DataParameterImpl
import org.gradle.internal.declarativedsl.analysis.UnknownParameterSemantics
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaComponent
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef


/**
 * Introduces functions for registering dependencies, such as `implementation(...)`, as member functions of
 * types with getters returning [DependencyCollector] in the schema.
 */
internal
class DependencyCollectorsComponent : EvaluationSchemaComponent {
    private
    val gavDependencyParam = DataParameterImpl("dependency", String::class.toDataTypeRef(), false, UnknownParameterSemantics)

    private
    val projectDependencyParam = DataParameterImpl("dependency", ProjectDependency::class.toDataTypeRef(), false, UnknownParameterSemantics)

    private
    val dependencyCollectorFunctionExtractorAndRuntimeResolver = DependencyCollectorFunctionExtractorAndRuntimeResolver(gavDependencyParam, projectDependencyParam)

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        dependencyCollectorFunctionExtractorAndRuntimeResolver
    )

    override fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = listOf(
        dependencyCollectorFunctionExtractorAndRuntimeResolver
    )
}
