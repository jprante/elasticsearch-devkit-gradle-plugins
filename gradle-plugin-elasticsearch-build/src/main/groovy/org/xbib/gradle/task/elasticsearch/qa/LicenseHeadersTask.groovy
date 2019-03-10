package org.xbib.gradle.task.elasticsearch.qa

import org.apache.rat.anttasks.Report
import org.apache.rat.anttasks.SubstringLicenseMatcher
import org.apache.rat.license.SimpleLicenseFamily
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile

import java.nio.file.Files

/**
 * Checks files for license headers.
 */
class LicenseHeadersTask extends AntTask {

    @OutputFile
    File reportFile = new File(project.buildDir, 'reports/licenseHeaders/rat.log')

    /**
     * The list of java files to check. protected so the afterEvaluate closure in the
     * constructor can write to it.
     */
    protected List<FileCollection> javaFiles

    /** Allowed license families. */
    @Input
    List<String> approvedLicenses = ['empty', 'Apache', 'GNU Public License']

    /**
     * Additional license families that may be found. The key is the license category name (5 characters),
     * followed by the family name and the value list of patterns to search for.
     */
    protected Map<String, String> additionalLicenses = new HashMap<>()

    LicenseHeadersTask() {
        description = "Checks sources for missing, incorrect, or unacceptable license headers"
        // Delay resolving the dependencies until after evaluation so we pick up generated sources
        project.afterEvaluate {
            javaFiles = project.sourceSets.collect({it.allJava})
            inputs.files(javaFiles)
        }
    }

    /**
     * Add a new license type.
     *
     * The license may be added to the {@link #approvedLicenses} using the {@code familyName}.
     *
     * @param categoryName A 5-character string identifier for the license
     * @param familyName An expanded string name for the license
     * @param pattern A pattern to search for, which if found, indicates a file contains the license
     */
    void additionalLicense(String categoryName, String familyName, String pattern) {
        if (categoryName.length() != 5) {
            throw new IllegalArgumentException("License category name must be exactly 5 characters, got ${categoryName}");
        }
        additionalLicenses.put(categoryName + familyName, pattern)
    }

    @Override
    protected void runAnt(AntBuilder ant) {
        ant.project.addTaskDefinition('ratReport', Report)
        ant.project.addDataTypeDefinition('substringMatcher', SubstringLicenseMatcher)
        ant.project.addDataTypeDefinition('approvedLicense', SimpleLicenseFamily)

        Files.deleteIfExists(reportFile.toPath())

        // run rat, going to the file
        List<FileCollection> input = javaFiles
        ant.ratReport(reportFile: reportFile.absolutePath, addDefaultLicenseMatchers: true) {
            for (FileCollection dirSet : input) {
               for (File dir: dirSet.srcDirs) {
                   // sometimes these dirs don't exist, e.g. site-plugin has no actual java src/main...
                   if (dir.exists()) {
                       ant.fileset(dir: dir) {
                           exclude(name: '**/module-info.java')
                       }
                   }
               }
            }

            // empty
            substringMatcher(licenseFamilyCategory: "EMPTY ",
                    licenseFamilyName:     "empty") {
                pattern(substring: "package ")
            }

            // Apache
            substringMatcher(licenseFamilyCategory: "AL   ",
                             licenseFamilyName:     "Apache") {
               // Apache license (ES)
               pattern(substring: "Licensed to Elasticsearch under one or more contributor")
               // Apache license (ASF)
               pattern(substring: "Licensed to the Apache Software Foundation (ASF) under")
               // this is the old-school one under some files
               pattern(substring: "Licensed under the Apache License, Version 2.0 (the \"License\")")
            }

            // GPL
            substringMatcher(licenseFamilyCategory: "GPL  ",
                    licenseFamilyName:     "GNU Public License") {
                pattern(substring: "GNU General Public License")
            }

            // license types added by the project
            for (Map.Entry<String, String> additional : additionalLicenses.entrySet()) {
                String category = additional.getKey().substring(0, 5)
                String family = additional.getKey().substring(5)
                substringMatcher(licenseFamilyCategory: category,
                                 licenseFamilyName: family) {
                    pattern(substring: additional.getValue())
                }
            }

            // approved categories
            for (String licenseFamily : approvedLicenses) {
                approvedLicense(familyName: licenseFamily)
            }
        }

        // check the license file for any errors
        boolean zeroUnknownLicenses = false
        boolean foundProblemsWithFiles = false
        reportFile.eachLine('UTF-8') { line ->
            if (line.startsWith("0 Unknown Licenses")) {
                zeroUnknownLicenses = true
            }

            if (line.startsWith(" !")) {
                foundProblemsWithFiles = true
            }
        }

        if (!zeroUnknownLicenses || foundProblemsWithFiles) {
            // print the unapproved license section, usually its all you need to fix problems.
            int sectionNumber = 0
            reportFile.eachLine('UTF-8') { line ->
                if (line.startsWith("*******************************")) {
                    sectionNumber++
                } else {
                    if (sectionNumber == 2) {
                        logger.error(line)
                    }
                }
            }
            logger
            throw new IllegalStateException("License header problems were found! Full details: " + reportFile.absolutePath)
        }
    }
}
