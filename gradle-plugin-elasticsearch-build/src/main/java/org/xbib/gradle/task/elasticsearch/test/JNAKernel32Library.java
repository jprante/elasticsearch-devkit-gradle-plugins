package org.xbib.gradle.task.elasticsearch.test;

import com.sun.jna.Native;
import com.sun.jna.WString;
import org.apache.tools.ant.taskdefs.condition.Os;

public class JNAKernel32Library {

    private static final class Holder {
        private static final JNAKernel32Library instance = new JNAKernel32Library();
    }

    private static JNAKernel32Library getInstance() {
        return Holder.instance;
    }

    private JNAKernel32Library() {
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            Native.register("kernel32");
        }
    }

    native int GetShortPathNameW(WString lpszLongPath, char[] lpszShortPath, int cchBuffer);

    static String getShortPathName(String path) {
        if (!Os.isFamily(Os.FAMILY_WINDOWS)) {
            throw new IllegalStateException();
        }
        final WString longPath = new WString("\\\\?\\" + path);
        // first we get the length of the buffer needed
        final int length = getInstance().GetShortPathNameW(longPath, null, 0);
        if (length == 0) {
            throw new IllegalStateException("path [" + path + "] encountered error [" + Native.getLastError() + "]");
        }
        final char[] shortPath = new char[length];
        // knowing the length of the buffer, now we get the short name
        if (getInstance().GetShortPathNameW(longPath, shortPath, length) == 0) {
            throw new IllegalStateException("path [" + path + "] encountered error [" + Native.getLastError() + "]");
        }
        // we have to strip the \\?\ away from the path for cmd.exe
        return Native.toString(shortPath).substring(4);
    }

}
