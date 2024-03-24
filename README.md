### Introduction

Make sure to read and understand the license for this program, found in the file LICENSE or at http://www.gnu.org/licenses/gpl.txt, before you start playing with it.

In order to build Hentai@Home, you just need OpenJDK 8 or newer.

In a Windows build environment, run make.bat and makejar.bat in order. On Linux, do make then make jar (or make all).

This will produce two .jar files, HentaiAtHome.jar and HentaiAtHomeGUI.jar, in the current directory. Move these .jar files to a location of your choice - this is where H@H will store all its files by default.

In a GUI environment you can usually just double-click HentaiAtHomeGUI.jar to start it. Alternatively, from a command-line, use java -jar HentaiAtHome.jar for the CLI version or java -jar HentaiAtHomeGUI.jar for the GUI version.

This package only contains the Hentai@Home Client, which is coded specifically for E-Hentai Galleries. The server-side systems are highly dependent on the setup of a given site, and must be coded specially if it is to be used for other sites.

### Why takes time to remake?

1. H@H client is written in Java 1.8, and this new version is in Java 17, which introduces several features to enhance and optimize
performance.
2. Some logs are not human readable.
3. Java 1.8 will be reaching EOL in 2026, it will be introduce potential security risks.
4. ***IMPORTANT*** Refactoring the code a bit to cope my OCD.
