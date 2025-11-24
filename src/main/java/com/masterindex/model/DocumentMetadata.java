package com.masterindex.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 存储在索引中的文档元数据。
 * 跟踪文件信息以检测变更。
 * @author liwenchao
 */
public class DocumentMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("size")
    private long size;

    @JsonProperty("mtime")
    private long mtime;

    @JsonProperty("hash")
    private String hash;

    @JsonProperty("type")
    private String type;

    @JsonProperty("extension")
    private String extension;

    public DocumentMetadata() {
    }

    public DocumentMetadata(long size, long mtime, String hash, String type, String extension) {
        this.size = size;
        this.mtime = mtime;
        this.hash = hash;
        this.type = type;
        this.extension = extension;
    }

    // Getter和Setter方法
    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getMtime() {
        return mtime;
    }

    public void setMtime(long mtime) {
        this.mtime = mtime;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    @Override
    public String toString() {
        return "DocumentMetadata{" +
                "size=" + size +
                ", mtime=" + mtime +
                ", hash='" + hash + '\'' +
                ", type='" + type + '\'' +
                ", extension='" + extension + '\'' +
                '}';
    }
}
