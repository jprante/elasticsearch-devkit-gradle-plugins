package org.xbib.gradle.plugin.randomizedtesting

class StackTraceFiltersConfiguration {
    List<String> patterns = []
    List<String> contains = []

    void regex(String pattern) {
        patterns.add(pattern)
    }

    void contains(String contain) {
        contains.add(contain)
    }
}
