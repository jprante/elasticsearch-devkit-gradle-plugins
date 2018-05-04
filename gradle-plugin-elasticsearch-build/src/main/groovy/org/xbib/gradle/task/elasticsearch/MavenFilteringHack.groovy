package org.xbib.gradle.task.elasticsearch

import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.file.CopySpec

/**
 * Gradle provides "expansion" functionality using Groovy's SimpleTemplateEngine.
 * However, it allows substitutions of the form {@code $foo} (no curlies). Rest tests provide
 * some substitution from the test runner, which this form is used for.
 *
 * This class provides a helper to do maven filtering, where only the form {@code $\{foo\}} is supported.
 */
class MavenFilteringHack {
    /**
     * Adds a filter to the given copy spec that will substitute maven variables.
     * @param CopySpec
     */
    static void filter(CopySpec copySpec, Map substitutions) {
        Map mavenSubstitutions = substitutions.collectEntries() {
            key, value -> ["{${key}".toString(), value.toString()]
        }
        copySpec.filter(ReplaceTokens, tokens: mavenSubstitutions, beginToken: '$', endToken: '}')
    }
}
