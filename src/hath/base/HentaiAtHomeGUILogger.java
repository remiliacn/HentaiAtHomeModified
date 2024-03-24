/*

Copyright 2008-2023 E-Hentai.org
https://forums.e-hentai.org/
tenboro@e-hentai.org

This file is part of Hentai@Home.

Hentai@Home is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hentai@Home is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hentai@Home.  If not, see <http://www.gnu.org/licenses/>.

*/

package hath.base;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SuppressWarnings("SynchronizeOnNonFinalField")
public class HentaiAtHomeGUILogger {
    public static final int DEBUG = 1;
    public static final int INFO = 2;
    public static final int WARNING = 4;
    public static final int ERROR = 8;

    public static final int LOGOUT = DEBUG | INFO | WARNING | ERROR;
    public static final int LOGERR = WARNING | ERROR;
    public static final int OUTPUT = INFO | WARNING | ERROR;
    public static final int VERBOSE = ERROR;

    private static boolean overridden, writeLogs;
    private static int suppressedOutput, logout_count, logerr_count;
    private static PrintStream def_out, def_err;
    private static OutPrintStream or_out;
    private static FileWriter logout, logerr;
    private static List<OutListener> outListeners;

    static {
        overrideDefaultOutput();
    }

    public static void overrideDefaultOutput() {
        if (overridden) {
            return;
        }

        writeLogs = false;
        overridden = true;
        outListeners = new ArrayList<>();

        suppressedOutput = 0;
        def_out = System.out;
        def_err = System.err;

        or_out = new OutPrintStream(def_out, "out", INFO);
        OutPrintStream or_err = new OutPrintStream(def_err, "ERR", ERROR);
        System.setOut(or_out);
        System.setErr(or_err);
    }

    public static void startLoggers() {
        logerr = startLogger(Settings.getErrorLogPath());

        if (!Settings.isDisableLogs()) {
            logout = startLogger(Settings.getOutputLogPath());
            writeLogs = true;
        }
    }

    public static void addOutListener(OutListener listener) {
        synchronized (outListeners) {
            if (!outListeners.contains(listener)) {
                outListeners.add(listener);
            }
        }
    }

    public static void disableLogging() {
        if (writeLogs) {
            info("Logging ended.");
            writeLogs = false;
            flushLogs();

            if (logout != null) {
                stopLogger(logout);
                logout = null;
            }
        }
    }

    public static void flushLogs() {
        if (logout != null) {
            try {
                logout.flush();
            } catch (IOException ignored) {
            }
        }
    }

    private static FileWriter startLogger(String logfile) {
        FileWriter writer = null;

        if (logfile != null) {
            // delete old log if present, and rotate
            (new File(logfile + ".old")).delete();
            (new File(logfile)).renameTo(new File(logfile + ".old"));

            if (!logfile.isEmpty()) {
                try {
                    writer = new FileWriter(logfile, true);
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                    System.err.println("Failed to open log file " + logfile);
                }
            }
        }

        if (writer != null) {
            log("\n" + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format((new Date()).toInstant())
                    + " Logging started", writer, true);
        }

        return writer;
    }

    private static boolean stopLogger(FileWriter logger) {
        try {
            logger.close();
        } catch (IOException e) {
            e.printStackTrace(def_err);
            def_err.println("Unable to close file writer handle: Cannot rotate log.");
            return false;
        }

        return true;
    }

    public static void debug(String x) {
        or_out.println(x, "debug", DEBUG);
    }

    public static void info(String x) {
        or_out.println(x, "info", INFO);
    }

    public static void warning(String x) {
        or_out.println(x, "WARN", WARNING);
    }

    public static void error(String x) {
        or_out.println(x, "ERROR", ERROR);
    }

    private static synchronized void log(String data, int severity) {
        if (((severity & LOGOUT) > 0) && writeLogs) {
            log(data, logout, false);

            if (++logout_count > 100000) {
                logout_count = 0;
                def_out.println("Rotating output logfile...");

                if (stopLogger(logout)) {
                    logout = startLogger(Settings.getOutputLogPath());
                    def_out.println("Output logfile rotated.");
                }
            }
        }

        if ((severity & LOGERR) > 0) {
            log(data, logerr, true);

            if (++logerr_count > 10000) {
                logerr_count = 0;
                def_out.println("Rotating error logfile...");

                if (stopLogger(logerr)) {
                    logerr = startLogger(Settings.getErrorLogPath());
                    def_out.println("Error logfile rotated.");
                }
            }
        }
    }

    private static void log(String data, FileWriter writer, boolean flush) {
        // note: unsynchronized. usage of this function for a specific writer must be serialized.
        if (writer != null) {
            try {
                writer.write(data + "\n");
                if (flush || Settings.isFlushLogs()) {
                    writer.flush();
                }
            } catch (IOException ioe) {
                // IMPORTANT: writes to the default System.err to prevent loops
                ioe.printStackTrace(def_err);
            }
        }
    }

    public static String verbose(int severity) {
        if ((severity & VERBOSE) > 0) {
            java.lang.StackTraceElement[] ste = java.lang.Thread.currentThread().getStackTrace();

            int offset = 0;
            while (++offset < ste.length) {
                String s = ste[offset].getClassName();
                if (!s.equals("hath.base.Out") && !s.equals("hath.base.Out$OutPrintStream") && !s.equals("java.lang.Thread")) {
                    break;
                }
            }

            if (offset < ste.length) {
                if (!ste[offset].getClassName().equals("java.lang.Throwable")) {
                    return "{" + ste[offset] + "} ";
                } else {
                    return "";
                }
            }

            return "{Unknown Source}";
        }

        return "";
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    private static class OutPrintStream extends PrintStream {
        private final PrintStream printStream;
        private final String name;
        private final int severity;

        public OutPrintStream(final PrintStream printStream, final String name, final int severity) {
            super(printStream);
            this.printStream = printStream;
            this.name = name;
            this.severity = severity;
        }

        public void println(String x) {
            println(x, name, severity);
        }

        public void println(String x, String name, int severity) {
            if (x == null) {
                return;
            }

            boolean output = (severity & HentaiAtHomeGUILogger.OUTPUT & ~HentaiAtHomeGUILogger.suppressedOutput) > 0;
            boolean log = (severity & (HentaiAtHomeGUILogger.LOGOUT | HentaiAtHomeGUILogger.LOGERR)) > 0;

            if (output || log) {
                synchronized (outListeners) {
                    String v = HentaiAtHomeGUILogger.verbose(severity);
                    String[] split = x.split("\n");
                    for (String s : split) {
                        String data = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format((new Date()).toInstant())
                                + " [" + name + "] " + v + s;

                        if (output) {
                            printStream.println(data);
                            for (OutListener listener : outListeners) {
                                listener.outputWritten(data);
                            }
                        }
                        if (log) {
                            HentaiAtHomeGUILogger.log(data, severity);
                        }
                    }
                }
            }
        }

        public void println(boolean x) {
            println(String.valueOf(x));
        }

        public void println(char x) {
            println(String.valueOf(x));
        }

        public void println(char[] x) {
            println(new String(x));
        }

        public void println(double x) {
            println(String.valueOf(x));
        }

        public void println(float x) {
            println(String.valueOf(x));
        }

        public void println(int x) {
            println(String.valueOf(x));
        }

        public void println(long x) {
            println(String.valueOf(x));
        }

        public void println(Object x) {
            println(String.valueOf(x));
        }
    }
}
