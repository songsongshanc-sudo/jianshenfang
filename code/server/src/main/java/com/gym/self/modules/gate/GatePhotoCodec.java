package com.gym.self.modules.gate;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;

public final class GatePhotoCodec {

    private static final int MAX_BYTES = 300 * 1024;

    private GatePhotoCodec() {
    }

    public static String encode(byte[] raw) {
        byte[] jpeg = toJpeg(raw);
        String data = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
        return URLEncoder.encode(data, StandardCharsets.UTF_8);
    }

    private static byte[] toJpeg(byte[] raw) {
        if (raw == null || raw.length == 0) {
            throw new IllegalArgumentException("没有照片");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (Exception exception) {
            image = null;
        }
        if (image == null) {
            if (raw.length <= MAX_BYTES) {
                return raw;
            }
            throw new IllegalArgumentException("照片过大");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        double scale = Math.min(1d, Math.min(1280d / width, 720d / height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage rgb = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, targetWidth, targetHeight);
        graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        float quality = 0.85f;
        byte[] encoded = write(rgb, quality);
        while (encoded.length > MAX_BYTES && quality > 0.35f) {
            quality -= 0.15f;
            encoded = write(rgb, quality);
        }
        if (encoded.length > MAX_BYTES) {
            throw new IllegalArgumentException("照片过大");
        }
        return encoded;
    }

    private static byte[] write(BufferedImage image, float quality) {
        try {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new IllegalArgumentException("照片无法编码");
            }
            ImageWriter writer = writers.next();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            return output.toByteArray();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("照片无法编码");
        }
    }
}
