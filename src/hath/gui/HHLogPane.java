/*

Copyright 2008-2023 E-Hentai.org
https://forums.e-hentai.org/
tenboro@e-hentai.org

This file is part of Hentai@Home GUI.

Hentai@Home GUI is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hentai@Home GUI is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hentai@Home GUI.  If not, see <http://www.gnu.org/licenses/>.

*/

package hath.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

import hath.base.Out;
import hath.base.OutListener;
import hath.base.Settings;

public class HHLogPane extends JPanel implements OutListener, ComponentListener {
    private final JTextArea textArea;
    private int logpointer = 0;
    private int logLinesSinceRebuild = 0;
    private long lastLogDisplayRebuild = 0;
    private final Object logSyncer = new Object();
    private int stringCutoff = 142;
    private int displayLineCount = 18;
    private boolean windowResized = false;
    private static final int STRING_CUT_OFF_MAX = 250;
    private static final StringBuilder STRING_BUILDER = new StringBuilder(3000);

    private static final int LOG_LINE_COUNT = 100;
    private static final String[] LOG_LINES = new String[LOG_LINE_COUNT];

    public HHLogPane() {
        setLayout(new BorderLayout());

        textArea = new JTextArea("");
        textArea.setFont(new Font("Courier", Font.PLAIN, 11));
        textArea.setEditable(false);
        textArea.setLineWrap(false);
        addText("Hentai@Home GUI " + Settings.CLIENT_VERSION + " initializing...");
        addText("The client will automatically start up momentarily...");

        JScrollPane taHolder = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        taHolder.setPreferredSize(new Dimension(1000, 300));

        add(taHolder, BorderLayout.CENTER);
        Out.addOutListener(this);
        addComponentListener(this);
    }

    public void outputWritten(String entry) {
        addText(entry);
    }

    public void addText(String toAdd) {
        synchronized (logSyncer) {
            if (++logpointer >= LOG_LINE_COUNT) {
                logpointer = 0;
            }

            if (toAdd.length() > stringCutoff) {
                LOG_LINES[logpointer] = toAdd.substring(0, stringCutoff);
            } else {
                LOG_LINES[logpointer] = toAdd;
            }

            ++logLinesSinceRebuild;
        }
    }

    public synchronized void checkRebuildLogDisplay() {
        long nowtime = System.currentTimeMillis();

        if (windowResized) {
            windowResized = false;
            stringCutoff = Math.max(STRING_CUT_OFF_MAX, getWidth() / 7);
            displayLineCount = Math.max(1, Math.min(LOG_LINE_COUNT, getHeight() / 16));
        } else if ((logLinesSinceRebuild < 1) || (nowtime - lastLogDisplayRebuild < 500)) {
            return;
        }

        lastLogDisplayRebuild = nowtime;
        logLinesSinceRebuild = 0;
        STRING_BUILDER.setLength(0);
        int displayLineIndex = LOG_LINE_COUNT - displayLineCount;

        // sync to prevent weirdness from threads adding text to the log array while the display text is building
        synchronized (logSyncer) {
            while (++displayLineIndex <= LOG_LINE_COUNT) {
                int logindex = logpointer + displayLineIndex;
                logindex = logindex >= LOG_LINE_COUNT ? logindex - LOG_LINE_COUNT : logindex;

                if (LOG_LINES[logindex] != null) {
                    STRING_BUILDER.append(LOG_LINES[logindex]);
                    STRING_BUILDER.append("\n");
                }
            }
        }

        textArea.setText(STRING_BUILDER.toString());
        textArea.setCaretPosition(STRING_BUILDER.length());
    }

    public void componentHidden(ComponentEvent event) {
    }

    public void componentMoved(ComponentEvent event) {
    }

    public void componentShown(ComponentEvent event) {
    }

    public void componentResized(ComponentEvent event) {
        windowResized = true;
        checkRebuildLogDisplay();
    }
}
