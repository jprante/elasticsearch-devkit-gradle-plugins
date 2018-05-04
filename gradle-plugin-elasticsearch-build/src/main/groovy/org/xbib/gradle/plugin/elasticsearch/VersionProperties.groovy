package org.xbib.gradle.plugin.elasticsearch

/**
 * Accessor for dependency versions used by this plugin.
 */
class VersionProperties {

    static final Map<String, String> versions = [:]

    static {
        Properties props = new Properties()
        InputStream propsStream = VersionProperties.class.getResourceAsStream('/gradle.properties')
        if (propsStream) {
            props.load(propsStream)
        }
        for (String property : props.stringPropertyNames()) {
            versions.put(property, props.getProperty(property))
        }
    }

    static String getVersion(String name) {
        versions.get(name + '.version')
    }

    static Map getAllVersions() {
        versions.collectEntries { k, v -> [(k.replace('.version', '')): v]}
    }
}
