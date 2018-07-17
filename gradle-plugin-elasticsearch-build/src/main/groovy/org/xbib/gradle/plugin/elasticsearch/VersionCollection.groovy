package org.xbib.gradle.plugin.elasticsearch

import org.gradle.api.GradleException
import org.gradle.api.Project

import java.util.regex.Matcher

/**
 * The collection of version constants declared in Version.java, for use in BWC testing.
 */
class VersionCollection {

    private final List<Version> versions = []

    Version nextMinorSnapshot

    Version stagedMinorSnapshot

    Version nextBugfixSnapshot

    Version maintenanceBugfixSnapshot

    Version currentVersion

    private final boolean buildSnapshot = System.getProperty("build.snapshot", "true") == "true"

    VersionCollection(Project project) {
        this(fromProject(project))
    }

    /**
     * Construct a VersionCollection from the lines of the Version.java file.
     * @param versionLines The lines of the Version.java file.
     */
    VersionCollection(List<String> versionLines) {
        for (final String line : versionLines) {
            final Matcher match = line =~ /.*Version V_(\d+)_(\d+)_(\d+)(_alpha\d+|_beta\d+|_rc\d+)?(_snapshot)? =.*/
            if (match.matches()) {
                Integer major = Integer.parseInt(match.group(1))
                Integer minor = Integer.parseInt(match.group(2))
                Integer revision = Integer.parseInt(match.group(3))
                String suffix = (match.group(4) ?: '').replace('_', '-')
                String snapshot = (match.group(5) ?: '').replace('_', '-')
                Boolean isSnapshot = !snapshot.isEmpty()
                String branch = null
                final Version foundVersion = new Version(major, minor, revision, suffix, isSnapshot, branch)
                if (versions.size() > 0 && foundVersion.onOrBeforeIncludingSuffix(versions[-1])) {
                    throw new GradleException("Versions.java contains out of order version constants:" +
                            " ${foundVersion} should come before ${versions[-1]}")
                }
                // Only keep the last alpha/beta/rc in the series
                if (versions.size() > 0 && versions[-1].id == foundVersion.id) {
                    versions[-1] = foundVersion
                } else {
                    versions.add(foundVersion)
                }
            }
        }
        if (versions.isEmpty()) {
            throw new GradleException("Unexpectedly found no version constants in Versions.java");
        }
        versions.removeAll { !it.suffix.isEmpty() && isMajorReleased(it, versions) }
        Version lastVersion = versions.last()
        currentVersion = new Version(lastVersion.major, lastVersion.minor, lastVersion.revision, lastVersion.suffix,
                buildSnapshot, lastVersion.branch)

        if (isReleased(currentVersion)) {
            Version highestMinor = getHighestPreviousMinor(currentVersion.major)
            maintenanceBugfixSnapshot = replaceAsSnapshot(highestMinor)
        } else {
            if (currentVersion.minor == 0) {
                for (Version version : getMinorTips(currentVersion.major - 1)) {
                    if (!isReleased(version)) {
                        if (nextMinorSnapshot == null) {
                            nextMinorSnapshot = replaceAsSnapshot(version)
                        } else if (stagedMinorSnapshot == null) {
                            stagedMinorSnapshot = replaceAsSnapshot(version)
                        } else {
                            throw new GradleException("More than 2 snapshot version existed for the next minor and staged (frozen) minors.")
                        }
                    } else {
                        nextBugfixSnapshot = replaceAsSnapshot(version)
                        break
                    }
                }
                Version highestMinor = getHighestPreviousMinor(currentVersion.major - 1)
                maintenanceBugfixSnapshot = replaceAsSnapshot(highestMinor)
            } else {
                for (Version version : getMinorTips(currentVersion.major)) {
                    if (!isReleased(version)) {
                        if (stagedMinorSnapshot == null) {
                            stagedMinorSnapshot = replaceAsSnapshot(version)
                        } else {
                            throw new GradleException("More than 1 snapshot version existed for the staged (frozen) minors.")
                        }
                    } else {
                        nextBugfixSnapshot = replaceAsSnapshot(version)
                        break
                    }
                }
                Version highestMinor = getHighestPreviousMinor(currentVersion.major)
                maintenanceBugfixSnapshot = replaceAsSnapshot(highestMinor)
            }
        }
    }

    static List<String> fromProject(Project project) {
        String esVersion = project.property('elasticsearch.version')
        // Version.java location may move around. Then use a system property to work around this.
        String esVersionUrl = System.getProperty('esVersionUrl', 'https://raw.githubusercontent.com/elastic/elasticsearch/v%s/server/src/main/java/org/elasticsearch/Version.java')
        URL url = new URL(sprintf(esVersionUrl, esVersion))
        println "trying version URL ${url}"
        url.readLines()
    }

    List<Version> getVersions() {
        versions
    }

    Version getBWCSnapshotForCurrentMajor() {
        getLastSnapshotWithMajor(currentVersion.major)
    }

    Version getBWCSnapshotForPreviousMajor() {
        getLastSnapshotWithMajor(currentVersion.major - 1)
    }

    private Version getLastSnapshotWithMajor(int targetMajor) {
        String currentVersion = currentVersion.toString()
        int snapshotIndex = versions.findLastIndexOf {
            it.major == targetMajor && it.before(currentVersion) && it.snapshot == buildSnapshot
        }
        return snapshotIndex == -1 ? null : versions[snapshotIndex]
    }

    private List<Version> versionsOnOrAfterExceptCurrent(Version minVersion) {
        final String minVersionString = minVersion.toString()
        return versions.findAll { it.onOrAfter(minVersionString) && it != currentVersion }
    }

    List<Version> getVersionsIndexCompatibleWithCurrent() {
        Version version = versions.find { it.major >= currentVersion.major - 1 }
        versionsOnOrAfterExceptCurrent(version)
    }

    List<Version> getVersionsWireCompatibleWithCurrent() {
        versionsOnOrAfterExceptCurrent(getMinimumWireCompatibilityVersion())
    }

    List<Version> getIndexCompatible() {
        int actualMajor = (currentVersion.major == 5 ? 2 : currentVersion.major - 1)
        Version version = Version.fromString("${actualMajor}.0.0")
        versions.findAll { it.onOrAfter(version.toString()) }.findAll { it.before(currentVersion.toString()) }
    }

    List<Version> getWireCompatible() {
        Version lowerBound = getHighestPreviousMinor(currentVersion.major)
        Version version = Version.fromString("${lowerBound.major}.${lowerBound.minor}.0")
        versions.findAll { it.onOrAfter(version.toString()) }.findAll { it.before(currentVersion.toString()) }
    }

    private Version getMinimumWireCompatibilityVersion() {
        int firstIndexOfThisMajor = versions.findIndexOf { it.major == currentVersion.major }
        if (firstIndexOfThisMajor == 0) {
            return versions[0]
        }
        Version lastVersionOfEarlierMajor = versions[firstIndexOfThisMajor - 1]
        versions.find { it.major == lastVersionOfEarlierMajor.major && it.minor == lastVersionOfEarlierMajor.minor }
    }

    private boolean isMajorReleased(Version version, List<Version> list) {
        Version v1 = Version.fromString("${version.major}.0.0")
        Version v2 = Version.fromString("${version.major + 1}.0.0")
        return list.findAll { it.onOrAfter(v1.toString()) }.findAll { it.before(v2.toString()) }
                .count { it.suffix.isEmpty() }
                .intValue() > 1
    }

    private Version getHighestPreviousMinor(Integer nextMajorVersion) {
        Version version = Version.fromString("${nextMajorVersion}.0.0")
        versions.findAll { it.before(version.toString()) }.last()
    }

    private Version replaceAsSnapshot(Version version) {
        versions.remove(version)
        Version snapshotVersion = new Version(version.major, version.minor, version.revision, version.suffix, true, null)
        versions.add(snapshotVersion)
        snapshotVersion
    }

    private List<Version> getMinorTips(Integer major) {
        List<Version> majorSet = getMajorSet(major)
        List<Version> minorList = new ArrayList<>()
        for (int minor = majorSet.last().minor; minor >= 0; minor--) {
            List<Version> minorSetInMajor = getMinorSetForMajor(major, minor)
            minorList.add(minorSetInMajor.last())
        }
        minorList
    }

    private List<Version> getMajorSet(Integer major) {
        Version version = Version.fromString("${major}.0.0")
        versions.findAll { it.onOrAfter(version.toString()) }.findAll { it.before(currentVersion.toString()) }
    }

    private List<Version> getMinorSetForMajor(Integer major, Integer minor) {
        Version v1 = Version.fromString("${major}.${minor}.0")
        Version v2 = Version.fromString("${major}.${minor + 1}.0")
        versions.findAll { it.onOrAfter(v1.toString()) }.findAll { it.before(v2.toString()) }
    }

    private static boolean isReleased(Version version) {
        version.revision > 0
    }

}
