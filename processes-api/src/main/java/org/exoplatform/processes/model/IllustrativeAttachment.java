/*
 * Copyright (C) 2022 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IllustrativeAttachment {

  private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp");

  private Long   id;

  private String fileName;

  private String fileBody;

  private String mimeType;

  private long   fileSize;

  private Long   lastUpdated;

  private boolean toDelete;

  private InputStream fileInputStream;

  public IllustrativeAttachment(Long id) {
    this.id = id;
  }

  public IllustrativeAttachment(Long id, String fileName, String mimeType, Long fileSize, Long lastUpdated) {
    this.id = id;
    this.fileName = fileName;
    this.mimeType = mimeType;
    this.fileSize = fileSize;
    this.lastUpdated = lastUpdated;
  }

  public IllustrativeAttachment(Long id, String fileName, InputStream inputStream, String mimeType, Long fileSize, Long lastUpdated) {
    this.id = id;
    this.fileName = fileName;
    this.fileInputStream = inputStream;
    this.mimeType = mimeType;
    this.fileSize = fileSize;
    this.lastUpdated = lastUpdated;
  }

  /**
   * Whether an illustration of this type may be stored and served as an
   * image: one of the types the process form accepts, PNG, JPEG, GIF, WEBP or
   * BMP, whatever its case or surrounding blanks.
   *
   * @param mimeType the illustration's type
   * @return true for an image type the browser renders as an image only
   */
  public static boolean isImageType(String mimeType) {
    return mimeType != null && IMAGE_TYPES.contains(mimeType.trim().toLowerCase(Locale.ROOT));
  }
}
