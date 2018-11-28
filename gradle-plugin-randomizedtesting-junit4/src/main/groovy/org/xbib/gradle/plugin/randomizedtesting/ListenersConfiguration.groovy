package org.xbib.gradle.plugin.randomizedtesting

import com.carrotsearch.ant.tasks.junit4.listeners.AggregatedEventListener
import com.carrotsearch.ant.tasks.junit4.listeners.antxml.AntXmlReport
import com.carrotsearch.ant.tasks.junit4.listeners.json.JsonReport

class ListenersConfiguration {

    RandomizedTestingTask task

    List<AggregatedEventListener> listeners = []

    ListenersConfiguration(RandomizedTestingTask task) {
        this.task = task
    }

    void junitXmlReport(Map<String, Object> props) {
        AntXmlReport reportListener = new AntXmlReport()
        Object dir = props == null ? null : props.get('dir')
        if (dir != null) {
            reportListener.setDir(task.project.file(dir))
        } else {
            reportListener.setDir(new File(task.project.buildDir, 'reports' + File.separator + "${task.name}Junit"))
        }
        listeners.add(reportListener)
    }

    void junitHtmlReport(Map<String, Object> props) {
        JsonReport jsonReport = new JsonReport()
        jsonReport.setFile(new File(task.project.buildDir, 'reports' + File.separator + "${task.name}Junit/index.html"));
        listeners.add(jsonReport)
    }

    void custom(AggregatedEventListener listener) {
        listeners.add(listener)
    }
}
