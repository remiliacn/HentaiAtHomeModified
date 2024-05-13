/*

Copyright 2008-2024 E-Hentai.org
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
along with Hentai@Home.  If not, see <https://www.gnu.org/licenses/>.

*/

package hath.base;

import java.util.List;

public class HTTPSessionKiller implements Runnable {
    private final List<HTTPSession> remove;
    private final Thread rachel;

    public HTTPSessionKiller(List<HTTPSession> remove) {
        this.remove = remove;
        rachel = new Thread(this);
    }

    public void satsuriku() {
        rachel.start();
    }

    public void run() {
        Out.debug("Starting kill thread for " + remove.size() + " sessions");
        remove.forEach(HTTPSession::forceCloseSocket);
        Out.debug("Kill thread finished closing " + remove.size() + " sessions");
    }
}
