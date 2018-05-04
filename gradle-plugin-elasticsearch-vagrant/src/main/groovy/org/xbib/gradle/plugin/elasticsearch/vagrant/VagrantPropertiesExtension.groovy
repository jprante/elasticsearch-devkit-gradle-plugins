package org.xbib.gradle.plugin.elasticsearch.vagrant

import org.gradle.api.tasks.Input

class VagrantPropertiesExtension {

    @Input
    List<String> boxes

    @Input
    String upgradeFromVersion

    @Input
    List<String> upgradeFromVersions

    @Input
    String batsDir

    @Input
    Boolean inheritTests

    @Input
    Boolean inheritTestArchives

    @Input
    Boolean inheritTestUtils

    VagrantPropertiesExtension(List<String> availableBoxes) {
        this.boxes = availableBoxes
        this.batsDir = 'src/test/resources/packaging'
    }

    void boxes(String... boxes) {
        this.boxes = Arrays.asList(boxes)
    }

    void setBatsDir(String batsDir) {
        this.batsDir = batsDir
    }

    void setInheritTests(Boolean inheritTests) {
        this.inheritTests = inheritTests
    }

    void setInheritTestArchives(Boolean inheritTestArchives) {
        this.inheritTestArchives = inheritTestArchives
    }

    void setInheritTestUtils(Boolean inheritTestUtils) {
        this.inheritTestUtils = inheritTestUtils
    }
}
