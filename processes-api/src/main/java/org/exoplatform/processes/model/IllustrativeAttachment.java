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
