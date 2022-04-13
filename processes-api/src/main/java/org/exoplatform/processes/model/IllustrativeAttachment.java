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

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IllustrativeAttachment {

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
}
