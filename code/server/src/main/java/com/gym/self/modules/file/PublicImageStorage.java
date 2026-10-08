package com.gym.self.modules.file;

public interface PublicImageStorage {

    String store(String biz, String contentType, byte[] body);
}
