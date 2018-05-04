package org.xbib.gradle.plugin.elasticsearch.vagrant

/**
 * Any object that can produce an accompanying stop task, meant to tear down
 * a previously instantiated service.
 */
interface Fixture {

    /** A task which will stop this fixture. This should be used as a finalizedBy for any tasks that use the fixture. */
    Object getStopTask()

}
