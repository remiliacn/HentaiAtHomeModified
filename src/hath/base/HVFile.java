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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class HVFile {
    public static final Pattern VALID_REGEX_1
            = Pattern.compile("^[a-f0-9]{40}-[0-9]{1,10}-[0-9]{1,5}-[0-9]{1,5}-(jpg|png|gif|mp4|wbm|wbp|avf|jxl)$");
    public static final Pattern VALID_REGEX_2 = Pattern.compile("^[a-f0-9]{40}-[0-9]{1,10}-(jpg|png|gif|mp4|wbm|wbp|avf|jxl)$");
    private final String hash;
    private final int size;
    private final int xres;
    private final int yres;
    private final String type;
    public static final String DELIMITER = "-";

    private HVFile(String hash, int size, int xres, int yres, String type) {
        this.hash = hash;
        this.size = size;
        this.xres = xres;
        this.yres = yres;
        this.type = type;
    }

    public File getLocalFileRef() {
        return new File(Settings.getCacheDir(), hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + getFileid());
    }

    public Path getLocalFilePath() {
        return getLocalFileRef().toPath();
    }

    public String getMimeType() {
        return switch (type) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "mp4" -> "video/mp4";
            case "wbm" -> "video/webm";
            case "wbp" -> "image/webp";
            case "avf" -> "image/avif";
            case "jxl" -> "image/jxl";
            default -> "application/octet-stream";
        };
    }

    public String getFileid() {
        if (xres > 0) {
            return String.join(DELIMITER, hash, Integer.toString(size),
                    Integer.toString(xres), Integer.toString(yres), type);
        } else {
            return String.join(DELIMITER, hash, Integer.toString(size), type);
        }
    }

    public String getHash() {
        return hash;
    }

    public int getSize() {
        return size;
    }

    public String getStaticRange() {
        return hash.substring(0, 4);
    }

    public static boolean isValidHVFileid(String fileid) {
        return VALID_REGEX_1.matcher(fileid).matches() || VALID_REGEX_2.matcher(fileid).matches();
    }

    public static HVFile getHVFileFromFile(File file) {
        return getHVFileFromFile(file, null);
    }

    public static HVFile getHVFileFromFile(File file, FileValidator validator) {
        if (file.exists()) {
            String fileid = file.getName();

            try {
                HVFile hvFile = getHVFileFromFileid(fileid);

                if (hvFile == null) {
                    return null;
                }

                if (file.length() != hvFile.getSize()) {
                    return null;
                }

                if (validator != null) {
                    if (!validator.validateFile(file.toPath(), fileid.substring(0, 40))) {
                        return null;
                    }
                }

                return hvFile;
            } catch (IOException e) {
                e.printStackTrace();
                Out.warning("Warning: Encountered IO error computing the hash value of " + file);
            }
        }

        return null;
    }

    public static HVFile getHVFileFromFileid(String fileid) {
        if (isValidHVFileid(fileid)) {
            try {
                String[] fileidParts = fileid.split("-");
                String hash = fileidParts[0];
                int size = Integer.parseInt(fileidParts[1]);

                int xres = 0, yres = 0;
                String type;

                if (fileidParts.length == 3) {
                    type = fileidParts[2];
                } else {
                    xres = Integer.parseInt(fileidParts[2]);
                    yres = Integer.parseInt(fileidParts[3]);
                    type = fileidParts[4];
                }

                return new HVFile(hash, size, xres, yres, type);
            } catch (Exception e) {
                Out.warning("Failed to parse fileid \"" + fileid + "\" : " + e);
            }
        } else {
            Out.warning("Invalid fileid \"" + fileid + "\"");
        }

        return null;
    }

    public String toString() {
        return getFileid();
    }
}
