/*
 * Copyright 2015 Toxic Bakery
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ToxicBakery.betterernaming

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet

class RenamePlugin implements Plugin<Project> {

    static final String ANDROID_APPLICATION_PLUGIN = "com.android.application"
//    static final String ANDROID_LIBRARY_PLUGIN = "com.android.library"

    void apply(Project project) {
        project.extensions.create("rename", RenameConfigExtension)

        project.afterEvaluate {
            def variants = getVariants(project)
            if (variants != null) {
                for (variant in variants) {
                    configure(project, variant);
                }
            }
        }
    }

    static def getVariants(Project project) {
        if (hasAppPlugin(project)) {
            return project.android.applicationVariants
        } else {
            // Only do work on applications
            return null;
        }
    }

    static boolean hasAppPlugin(Project projectUnderTest) {
        return projectUnderTest.plugins.hasPlugin(ANDROID_APPLICATION_PLUGIN)
    }

    static void configure(Project project, def variant) {
        variant.assemble.doLast {

            if (!hasAppPlugin(project)) {
                throw new IllegalStateException("Project is not an Android Application")
            }

            // Parse requested artifact format. This works by looking for %...% group matches and
            // running string replace with the match as the needle and a dynamic property or method
            // result as the value.
            String outputName = project.rename.artifactFormat;
            def matcher = outputName =~ /%([^%]+)%/
            matcher.each { match, matchGroup ->
                String matchGroupStr = matchGroup.toString()
                String value;

                // Attempt to replace the match with the first likely match
                if ("ext".equals(matchGroupStr)) {
                    project.logger.debug "Matched hard code ext for %$matchGroupStr%"
                    value = "apk"
                } else if (project.rename.hasProperty(matchGroupStr)) {
                    value = project.rename."$matchGroupStr"
                    project.logger.debug "Matched dynamic property for %$matchGroupStr%"
                } else if (project.rename.metaClass.respondsTo(project.rename, matchGroupStr)) {
                    value = project.rename."$matchGroupStr"()
                    project.logger.debug "Matched rename metaClass method for %$matchGroupStr%"
                } else if (variant.hasProperty(matchGroupStr)) {
                    project.logger.debug "Matched variant property for %$matchGroupStr%"
                    value = variant."$matchGroupStr"
                } else if (variant.metaClass.respondsTo(variant, matchGroupStr)) {
                    value = variant."$matchGroupStr"()
                    project.logger.debug "Matched variant metaClass method for %$matchGroupStr%"
                } else if (project.hasProperty(matchGroupStr)) {
                    value = project."$matchGroupStr"
                    project.logger.debug "Matched project property for %$matchGroupStr%"
                } else if (project.metaClass.respondsTo(project, matchGroupStr)) {
                    value = project."$matchGroupStr"()
                    project.logger.debug "Matched project metaClass method for %$matchGroupStr%"
                } else if (hasProperty(matchGroupStr)) {
                    value = "$matchGroupStr"
                    project.logger.debug "Matched internal property for %$matchGroupStr%"
                } else {
                    throw new IllegalArgumentException("Requested group '$matchGroupStr' is not a method  or property.")
                }

                outputName = outputName.replace("%$matchGroupStr%", value)
            }

            // Debug formatting results
            project.logger.debug "Requested artifact format: $project.rename.artifactFormat"
            project.logger.debug "Formatted artifact name: $outputName"

            renameApkFile(project, variant, outputName);
        }
    }

    static renameApkFile(Project project, def variant, String newName) {
        // Change the release artifact name
        def apkFilePath = "$project.buildDir/outputs/apk"
        def buildTypeName = variant.buildType.name.capitalize()
        def projectFlavorNames = variant.productFlavors.collect { it.name.capitalize() }
        def projectFlavorName = projectFlavorNames.join('-')

        def apkName
        if (projectFlavorName != "") {
            apkName = "${project.name}-${projectFlavorName.toLowerCase()}-${buildTypeName.toLowerCase()}.apk"
        } else {
            apkName = "${project.name}-${buildTypeName.toLowerCase()}.apk"
        }

        project.logger.debug "==========================="
        project.logger.debug "$apkFilePath/$apkName"
        project.logger.debug "${project.getPath()}"
        project.logger.debug "==========================="

        File apk = new File("$apkFilePath/$apkName")
        apk.renameTo("${apk.getParent()}/$newName")
    }

    static PublishArtifactSet getPublishArtifactSet(Project project) {
        return project.configurations.archives.artifacts
    }

}