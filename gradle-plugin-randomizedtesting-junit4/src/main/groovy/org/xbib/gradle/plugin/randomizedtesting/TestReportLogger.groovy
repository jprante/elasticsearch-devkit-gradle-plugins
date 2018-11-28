package org.xbib.gradle.plugin.randomizedtesting

import com.carrotsearch.ant.tasks.junit4.FormattingUtils
import com.carrotsearch.ant.tasks.junit4.JUnit4
import com.carrotsearch.ant.tasks.junit4.Pluralize
import com.carrotsearch.ant.tasks.junit4.TestsSummaryEventListener
import com.google.common.eventbus.Subscribe
import com.carrotsearch.ant.tasks.junit4.events.EventType
import com.carrotsearch.ant.tasks.junit4.events.IEvent
import com.carrotsearch.ant.tasks.junit4.events.IStreamEvent
import com.carrotsearch.ant.tasks.junit4.events.SuiteStartedEvent
import com.carrotsearch.ant.tasks.junit4.events.TestFinishedEvent
import com.carrotsearch.ant.tasks.junit4.events.aggregated.AggregatedQuitEvent
import com.carrotsearch.ant.tasks.junit4.events.aggregated.AggregatedResultEvent
import com.carrotsearch.ant.tasks.junit4.events.aggregated.AggregatedStartEvent
import com.carrotsearch.ant.tasks.junit4.events.aggregated.AggregatedSuiteResultEvent
import com.carrotsearch.ant.tasks.junit4.events.aggregated.AggregatedSuiteStartedEvent
import com.carrotsearch.ant.tasks.junit4.events.aggregated.AggregatedTestResultEvent
import com.carrotsearch.ant.tasks.junit4.events.aggregated.ChildBootstrap
import com.carrotsearch.ant.tasks.junit4.events.aggregated.HeartBeatEvent
import com.carrotsearch.ant.tasks.junit4.events.aggregated.PartialOutputEvent
import com.carrotsearch.ant.tasks.junit4.events.aggregated.TestStatus
import com.carrotsearch.ant.tasks.junit4.events.mirrors.FailureMirror
import com.carrotsearch.ant.tasks.junit4.listeners.AggregatedEventListener
import com.carrotsearch.ant.tasks.junit4.listeners.StackTraceFilter
import org.apache.tools.ant.filters.TokenFilter
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.junit.runner.Description

import java.util.concurrent.atomic.AtomicInteger

class TestReportLogger extends TestsSummaryEventListener implements AggregatedEventListener {

    EnumMap<? extends TestStatus, String> statusNames

    JUnit4 owner

    /** Logger to write the report to */
    Logger logger

    TestLoggingConfiguration config

    /** Forked concurrent JVM count. */
    int forkedJvmCount

    /** Format line for JVM ID string. */
    String jvmIdFormat

    /** Output stream that logs messages to the given logger */
    LoggingOutputStream outStream

    LoggingOutputStream errStream

    /** A list of failed tests, if to be displayed at the end. */
    List<Description> failedTests

    /** Stack trace filters. */
    StackTraceFilter stackFilter

    Map<String, Long> suiteTimes

    boolean slowTestsFound

    int totalSuites

    AtomicInteger suitesCompleted

    TestReportLogger(Logger logger, TestLoggingConfiguration config) {
        suiteTimes = new HashMap<>()
        stackFilter = new StackTraceFilter()
        failedTests = new ArrayList<>()
        suitesCompleted = new AtomicInteger()
        statusNames = new EnumMap<>(TestStatus)
        for (TestStatus s : TestStatus.values()) {
            this.statusNames.put(s, s == TestStatus.IGNORED_ASSUMPTION ? "IGNOR/A" : s.toString())
        }
        this.logger = logger
        this.config = config
    }

    @Override
    void setOuter(JUnit4 task) {
        owner = task
    }

    @Subscribe
    void onStart(AggregatedStartEvent e) throws IOException {
        this.totalSuites = e.getSuiteCount()
        StringBuilder info = new StringBuilder('==> Test Info: ')
        info.append('seed=' + owner.getSeed() + '; ')
        info.append(Pluralize.pluralize(e.getSlaveCount(), 'jvm') + '=' + e.getSlaveCount() + '; ')
        info.append(Pluralize.pluralize(e.getSuiteCount(), 'suite') + '=' + e.getSuiteCount())
        logger.lifecycle(info.toString())

        forkedJvmCount = e.getSlaveCount()
        jvmIdFormat = " J%-" + (1 + (int) Math.floor(Math.log10(forkedJvmCount))) + "d"

        outStream = new LoggingOutputStream(logger: logger, level: LogLevel.LIFECYCLE, prefix: "  1> ")
        errStream = new LoggingOutputStream(logger: logger, level: LogLevel.ERROR, prefix: "  2> ")

        for (String contains : config.stackTraceFilters.contains) {
            TokenFilter.ContainsString containsFilter = new TokenFilter.ContainsString()
            containsFilter.setContains(contains)
            stackFilter.addContainsString(containsFilter)
        }
        for (String pattern : config.stackTraceFilters.patterns) {
            TokenFilter.ContainsRegex regexFilter = new TokenFilter.ContainsRegex()
            regexFilter.setPattern(pattern)
            stackFilter.addContainsRegex(regexFilter)
        }
    }

    @Subscribe
    void onChildBootstrap(ChildBootstrap e) throws IOException {
        logger.info("Started J" + e.getSlave().id + " PID(" + e.getSlave().getPidString() + ").")
    }

    @Subscribe
    void onHeartbeat(HeartBeatEvent e) throws IOException {
        logger.warn("HEARTBEAT J" + e.getSlave().id + " PID(" + e.getSlave().getPidString() + "): " +
                FormattingUtils.formatTime(e.getCurrentTime()) + ", stalled for " +
                FormattingUtils.formatDurationInSeconds(e.getNoEventDuration()) + " at: " +
                (e.getDescription() == null ? "<unknown>" : FormattingUtils.formatDescription(e.getDescription())))
        slowTestsFound = true
    }

    @Subscribe
    void onQuit(AggregatedQuitEvent e) throws IOException {
        if (config.showNumFailuresAtEnd > 0 && !failedTests.isEmpty()) {
            List<Description> sublist = this.failedTests
            StringBuilder b = new StringBuilder()
            b.append('Tests with failures')
            if (sublist.size() > config.showNumFailuresAtEnd) {
                sublist = sublist.subList(0, config.showNumFailuresAtEnd)
                b.append(" (first " + config.showNumFailuresAtEnd + " out of " + failedTests.size() + ")")
            }
            b.append(':\n')
            for (Description description : sublist) {
                b.append("  - ").append(FormattingUtils.formatDescription(description, true)).append('\n')
            }
            logger.warn(b.toString())
        }
        if (config.slowTests.summarySize > 0) {
            List<Map.Entry<String, Long>> sortedSuiteTimes = new ArrayList<>(suiteTimes.entrySet())
            Collections.sort(sortedSuiteTimes, new Comparator<Map.Entry<String, Long>>() {
                @Override
                int compare(Map.Entry<String, Long> o1, Map.Entry<String, Long> o2) {
                    return o2.value - o1.value // sort descending
                }
            })
            LogLevel level = slowTestsFound ? LogLevel.WARN : LogLevel.INFO
            int numToLog = Math.min(config.slowTests.summarySize, sortedSuiteTimes.size())
            logger.log(level, 'Slow Tests Summary:')
            for (int i = 0; i < numToLog; ++i) {
                // we use milliseonds here because of JDK 11 / Groovy problems when using static initialize on div constants
                String key = sortedSuiteTimes.get(i).key
                long millis = sortedSuiteTimes.get(i).value
                String s = sprintf('%d ms | %s', millis, key)
                logger.log(level, s)
            }
            logger.log(level, '') // extra vertical separation
        }
        if (failedTests.isEmpty()) {
            // summary is already printed for failures
            logger.lifecycle('==> Test Summary: ' + getResult().toString())
        }
    }

    @Subscribe
    void onSuiteStart(AggregatedSuiteStartedEvent e) throws IOException {
        if (isPassthrough()) {
            SuiteStartedEvent evt = e.getSuiteStartedEvent()
            emitSuiteStart(LogLevel.LIFECYCLE, evt.getDescription())
        }
    }

    @Subscribe
    void onOutput(PartialOutputEvent e) throws IOException {
        if (isPassthrough()) {
            // We only allow passthrough output if there is one JVM.
            switch (e.getEvent().getType()) {
                case EventType.APPEND_STDERR:
                    ((IStreamEvent) e.getEvent()).copyTo(errStream)
                    break
                case EventType.APPEND_STDOUT:
                    ((IStreamEvent) e.getEvent()).copyTo(outStream)
                    break
                default:
                    break
            }
        }
    }

    @Subscribe
    void onTestResult(AggregatedTestResultEvent e) throws IOException {
        if (isPassthrough() && e.getStatus() != TestStatus.OK) {
            flushOutput()
            emitStatusLine(LogLevel.ERROR, e, e.getStatus(), e.getExecutionTime())
        }
        if (!e.isSuccessful()) {
            failedTests.add(e.getDescription())
        }
    }

    @Subscribe
    void onSuiteResult(AggregatedSuiteResultEvent e) throws IOException {
        final int completed = suitesCompleted.incrementAndGet()
        if (e.isSuccessful() && e.getTests().isEmpty()) {
            return
        }
        if (config.slowTests.summarySize > 0) {
            suiteTimes.put(e.getDescription().getDisplayName(), e.getExecutionTime())
        }
        LogLevel level = e.isSuccessful() && config.outputMode != TestLoggingConfiguration.OutputMode.ALWAYS ? LogLevel.INFO : LogLevel.LIFECYCLE
        // We must emit buffered test and stream events (in case of failures).
        if (!isPassthrough()) {
            emitSuiteStart(level, e.getDescription())
            emitBufferedEvents(level, e)
        }

        // Emit a synthetic failure for suite-level errors, if any.
        if (!e.getFailures().isEmpty()) {
            emitStatusLine(level, e, TestStatus.ERROR, 0)
        }

        if (!e.getFailures().isEmpty()) {
            failedTests.add(e.getDescription())
        }

        emitSuiteEnd(level, e, completed)
    }

    void emitSuiteStart(LogLevel level, Description description) throws IOException {
        logger.log(level, 'Suite: ' + description.getDisplayName())
    }

    void emitBufferedEvents(LogLevel level, AggregatedSuiteResultEvent e) throws IOException {
        if (config.outputMode == TestLoggingConfiguration.OutputMode.NEVER) {
            return
        }
        IdentityHashMap<TestFinishedEvent,AggregatedTestResultEvent> eventMap = new IdentityHashMap<>()
        for (Object tre : e.getTests()) {
            eventMap.put(tre.getTestFinishedEvent(), tre as AggregatedTestResultEvent)
        }
        boolean emitOutput = config.outputMode == TestLoggingConfiguration.OutputMode.ALWAYS && !isPassthrough() ||
                                   config.outputMode == TestLoggingConfiguration.OutputMode.ONERROR && !e.isSuccessful()
        for (Object obj : e.getEventStream()) {
            IEvent event = obj as IEvent
            switch (event.getType()) {
                case EventType.APPEND_STDOUT:
                    if (emitOutput) ((IStreamEvent) event).copyTo(outStream)
                    break

                case EventType.APPEND_STDERR:
                    if (emitOutput) ((IStreamEvent) event).copyTo(errStream)
                    break

                case EventType.TEST_FINISHED:
                    assert eventMap.containsKey(event)
                    final AggregatedTestResultEvent aggregated = eventMap.get(event)
                    if (aggregated.getStatus() != TestStatus.OK) {
                        flushOutput()
                        emitStatusLine(level, aggregated, aggregated.getStatus(), aggregated.getExecutionTime())
                    }
                    break

                default:
                    break
            }
        }
        if (emitOutput) {
            flushOutput()
        }
    }

    void emitSuiteEnd(LogLevel level, AggregatedSuiteResultEvent e, int suitesCompleted) throws IOException {
        StringBuilder b = new StringBuilder()
        b.append(sprintf('Completed [%d/%d]%s in %.2fs, ',
                suitesCompleted,
                totalSuites,
                e.getSlave().slaves > 1 ? ' on J' + e.getSlave().id : '',
                e.getExecutionTime() / 1000.0d))
        b.append(e.getTests().size()).append(Pluralize.pluralize(e.getTests().size(), ' test'))
        int failures = e.getFailureCount()
        if (failures > 0) {
            b.append(', ').append(failures).append(Pluralize.pluralize(failures, ' failure'))
        }
        int errors = e.getErrorCount()
        if (errors > 0) {
            b.append(', ').append(errors).append(Pluralize.pluralize(errors, ' error'))
        }
        int ignored = e.getIgnoredCount()
        if (ignored > 0) {
            b.append(', ').append(ignored).append(' skipped')
        }
        if (!e.isSuccessful()) {
            b.append(' <<< FAILURES!')
        }
        b.append('\n')
        logger.log(level, b.toString())
    }

    void emitStatusLine(LogLevel level, AggregatedResultEvent result, TestStatus status, long timeMillis) throws IOException {
        StringBuilder line = new StringBuilder()
        line.append(FormattingUtils.padEnd(statusNames.get(status), 8, ' ' as char))
        line.append(FormattingUtils.formatDurationInSeconds(timeMillis))
        if (forkedJvmCount > 1) {
            line.append(sprintf(jvmIdFormat, result.getSlave().id))
        }
        line.append(' | ')

        line.append(FormattingUtils.formatDescription(result.getDescription()))
        if (!result.isSuccessful()) {
            line.append(' <<< FAILURES!')
        }
        logger.log(level, line.toString())
        PrintWriter writer = new PrintWriter(new LoggingOutputStream(logger: logger, level: level, prefix: '   > '))
        if (status == TestStatus.IGNORED && result instanceof AggregatedTestResultEvent) {
            writer.write('Cause: ')
            writer.write(((AggregatedTestResultEvent) result).getCauseForIgnored())
            writer.flush()
        }
        List<FailureMirror> failures = result.getFailures()
        if (!failures.isEmpty()) {
            int count = 0
            for (FailureMirror fm : failures) {
                count++
                if (fm.isAssumptionViolation()) {
                    writer.write(sprintf('Assumption #%d: %s\n',
                            count, fm.getMessage() == null ? '(no message)' : fm.getMessage()))
                } else {
                    writer.write(sprintf('Throwable #%d: %s\n', count,
                            stackFilter.apply(fm.getTrace())))
                }
            }
            writer.flush()
        }
    }

    void flushOutput() throws IOException {
        outStream.flush()
        errStream.flush()
    }

    /** Returns true if output should be logged immediately. */
    boolean isPassthrough() {
        return forkedJvmCount == 1 && config.outputMode == TestLoggingConfiguration.OutputMode.ALWAYS
    }
}
