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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.Hashtable;

public class Tools {

    public static final String SHA_1 = "SHA-1";

    public static File checkAndCreateDir(File dir) throws IOException {
        if (dir.isFile()) {
            dir.delete();
        }

        if (!dir.isDirectory()) {
            if (!dir.mkdirs()) {
                throw new IOException("Could not create directory " + dir + "; check permissions and I/O errors.");
            }
        }

        return dir;
    }

    public static String getStringFileContents(File file) throws IOException {
        char[] cbuf = new char[(int) file.length()];
        FileReader fr = new FileReader(file);
        fr.read(cbuf);
        fr.close();
        return new String(cbuf);
    }

    public static void putStringFileContents(File file, String content) throws IOException {
        FileWriter fw = new FileWriter(file);
        fw.write(content);
        fw.close();
    }

    public static void putStringFileContents(File file, String content, String charset) throws IOException {
        int fileLength = content.length();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset));
        bw.write(content, 0, fileLength);
        bw.close();
    }

    public static File[] listSortedFiles(File dir) {
        File[] files = dir.listFiles();

        if (files != null) {
            Arrays.sort(files);
        }

        return files;
    }

    public static Hashtable<String, String> parseAdditional(String additional) {
        Hashtable<String, String> addTable = new Hashtable<>();

        if (additional != null) {
            if (!additional.isEmpty()) {
                String[] keyValuePairs = additional.trim().split(";");

                for (String kvPair : keyValuePairs) {
                    // you cannot get k=v with less than a three-characters string
                    if (kvPair.length() > 2) {
                        String[] kvPairParts = kvPair.trim().split("=", 2);

                        if (kvPairParts.length == 2) {
                            addTable.put(kvPairParts[0].trim(), kvPairParts[1].trim());
                        } else {
                            Out.warning("Invalid kvPair: " + kvPair);
                        }
                    }
                }
            }
        }

        return addTable;
    }

    public static String getSHA1String(String stringToHash) {
        String hash = null;

        try {
            hash = binaryToHex(MessageDigest.getInstance(SHA_1).digest(stringToHash.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            HentaiAtHomeClient.dieWithError(e);
        }

        return hash;
    }

    public static String getSHA1String(File fileToHash) {
        FileChannel fileChannel = null;
        String hash = null;

        try {
            fileChannel = FileChannel.open(fileToHash.toPath(), StandardOpenOption.READ);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect((int) Math.min(65536, fileToHash.length()));

            while (fileChannel.read(byteBuffer) != -1) {
                byteBuffer.flip();
                messageDigest.update(byteBuffer);
                byteBuffer.clear();
            }

            hash = binaryToHex(messageDigest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            HentaiAtHomeClient.dieWithError(e);
        } catch (IOException e) {
            Out.warning("Failed to calculate SHA-1 hash of file " + fileToHash + ": " + e.getMessage());
        } finally {
            try {
                if (fileChannel != null) {
                    fileChannel.close();
                }
            } catch (IOException ignored) {
            }
        }

        return hash;
    }

    public static String binaryToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);

        for (byte b : data) {
            int i = (int) b & 0xff;

            if (i < 0x10) {
                sb.append("0");
            }

            sb.append(Integer.toHexString(i));
        }

        return sb.toString().toLowerCase();
    }

    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.2f %cB", value / 1024.0, ci.current());
    }

    public static boolean isDigit(String string) {
        return string.chars().allMatch(Character::isDigit);
    }
}
