package org.xbib.gradle.plugin.randomizedtesting;

import org.junit.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ProcessNameTest {

    private static final Logger logger = Logger.getLogger(ProcessNameTest.class.getName());

    @Test
    public void testProcessName() {
        logger.log(Level.INFO, System.getProperties().toString());
    }
}
