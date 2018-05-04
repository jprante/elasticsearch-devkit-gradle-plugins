package org.xbib.gradle.plugin.elasticsearch

import groovy.transform.Sortable
import org.gradle.api.InvalidUserDataException

import java.util.regex.Matcher

/**
 * Encapsulates comparison and printing logic for an x.y.z version.
 */
@Sortable(includes=['id'])
class Version {

    final int major
    final int minor
    final int revision
    final int id
    final boolean snapshot
    final String branch
    /**
     * Suffix on the version name. Unlike Version.java the build does not
     * consider alphas and betas different versions, it just preserves the
     * suffix that the version was declared with in Version.java.
     */
    final String suffix

    Version(int major, int minor, int revision, String suffix, boolean snapshot, String branch) {
        this.major = major
        this.minor = minor
        this.revision = revision
        this.snapshot = snapshot
        this.suffix = suffix
        this.branch = branch
        this.id = major * 100000 + minor * 1000 + revision * 10 +
            (snapshot ? 1 : 0)
    }

    static Version fromString(String s) {
        Matcher m = s =~ /(\d+)\.(\d+)\.(\d+)(-alpha\d+|-beta\d+|-rc\d+)?(-SNAPSHOT)?/
        if (!m.matches()) {
            throw new InvalidUserDataException("Invalid version [${s}]")
        }
        return new Version(m.group(1) as int, m.group(2) as int,
            m.group(3) as int, m.group(4) ?: '', m.group(5) != null, null)
    }

    @Override
    String toString() {
        String snapshotStr = snapshot ? '-SNAPSHOT' : ''
        return "${major}.${minor}.${revision}${suffix}${snapshotStr}"
    }

    boolean before(String compareTo) {
        return id < fromString(compareTo).id
    }

    boolean onOrBefore(String compareTo) {
        return id <= fromString(compareTo).id
    }

    boolean onOrAfter(String compareTo) {
        return id >= fromString(compareTo).id
    }

    boolean after(String compareTo) {
        return id > fromString(compareTo).id
    }

    boolean onOrBeforeIncludingSuffix(Version otherVersion) {
        if (id != otherVersion.id) {
            return id < otherVersion.id
        }

        if (suffix == '') {
            return otherVersion.suffix == ''
        }

        return otherVersion.suffix == '' || suffix < otherVersion.suffix
    }

    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }

        Version version = (Version) o

        if (id != version.id) {
            return false
        }
        if (major != version.major) {
            return false
        }
        if (minor != version.minor) {
            return false
        }
        if (revision != version.revision) {
            return false
        }
        if (snapshot != version.snapshot) {
            return false
        }
        if (suffix != version.suffix) {
            return false
        }

        return true
    }

    int hashCode() {
        int result
        result = major
        result = 31 * result + minor
        result = 31 * result + revision
        result = 31 * result + id
        result = 31 * result + (snapshot ? 1 : 0)
        result = 31 * result + (suffix != null ? suffix.hashCode() : 0)
        return result
    }
}
