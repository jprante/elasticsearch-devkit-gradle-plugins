package org.elasticsearch.test;

import junit.framework.TestCase;

/**
 * These inner classes all fail the NamingConventionsCheck. They have to live in the tests or else they won't be scanned.
 */
public class NamingConventionsCheckBadClasses {
    public static final class NotImplementingTests {
    }

    public static final class WrongName extends UnitTestCase {
        /*
         * Dummy test so the tests pass. We do this *and* skip the tests so anyone who jumps back to a branch without these tests can still
         * compile without a failure. That is because clean doesn't actually clean these....
         */
        public void testDummy() {}
    }

    public abstract static class DummyAbstractTests extends UnitTestCase {
    }

    public interface DummyInterfaceTests {
    }

    public static final class InnerTests extends UnitTestCase {
        public void testDummy() {}
    }

    public static final class WrongNameTheSecond extends UnitTestCase {
        public void testDummy() {}
    }

    public static final class PlainUnit extends TestCase {
        public void testDummy() {}
    }

    public abstract static class UnitTestCase extends TestCase {
    }

    public abstract static class IntegTestCase extends UnitTestCase {
    }
}
