package org.xbib.gradle.task.elasticsearch.qa

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.security.MessageDigest

/**
 * A task to update secure hashes used by {@code DependencyLicensesCheck}
 */
class UpdateShasTask extends DefaultTask {

    /** The parent dependency licenses task to use configuration from */
    public DependencyLicensesTask parentTask

    public UpdateShasTask() {
        description = 'Updates the SHA files for the dependencyLicenses check'
        onlyIf { parentTask.licensesDir.exists() }
    }

    @TaskAction
    void updateShas() {
        Set<File> shaFiles = new HashSet<File>()
        parentTask.licensesDir.eachFile {
            String name = it.getName()
            if (name.endsWith(DependencyLicensesTask.SHA_EXTENSION)) {
                shaFiles.add(it)
            }
        }
        for (File dependency : parentTask.dependencies) {
            String jarName = dependency.getName()
            File shaFile = new File(parentTask.licensesDir, jarName + DependencyLicensesTask.SHA_EXTENSION)
            if (!shaFile.exists()) {
                logger.lifecycle("Adding SHA for ${jarName}")
                String sha = MessageDigest.getInstance("SHA-1").digest(dependency.getBytes()).encodeHex().toString()
                shaFile.setText(sha, 'UTF-8')
            } else {
                shaFiles.remove(shaFile)
            }
        }
        shaFiles.each { shaFile ->
            logger.lifecycle("Removing unused SHA ${shaFile.getName()}")
            Files.delete(shaFile.toPath())
        }
    }
}
