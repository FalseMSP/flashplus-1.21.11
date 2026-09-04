package redsmods.flashplus;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PanoramaScreenshotHelper {

    public static void takePanorama(Minecraft mc, String outputBase) {
        float savedXRot = mc.player.getXRot();
        float savedYRot = mc.player.getYRot();
        float savedXRotO = mc.player.xRotO;
        float savedYRotO = mc.player.yRotO;

        mc.gameRenderer.setRenderBlockOutline(false);

        try {
            Flashback.getReplayServer().getEditorState().replayVisuals.overrideFov = false;

            File targetDir = new File(outputBase + "_panorama");
            targetDir.mkdirs();

            // Use Minecraft's built-in panorama capture
            mc.grabPanoramixScreenshot(targetDir);

        } catch (Exception e) {
            Flashplus.LOGGER.error(String.valueOf(e));
        } finally {
            mc.player.setXRot(savedXRot);
            mc.player.setYRot(savedYRot);
            mc.player.xRotO = savedXRotO;
            mc.player.yRotO = savedYRotO;
            mc.gameRenderer.setRenderBlockOutline(true);
        }
    }

    /**
     * Converts 6 cubemap faces to an equirectangular (lat/long) projection.
     *
     * Face index convention (matches vanilla panorama order):
     *   0 = Front  (+Z, yaw=0)
     *   1 = Left   (-X, yaw=+90)
     *   2 = Back   (-Z, yaw=+180)
     *   3 = Right  (+X, yaw=-90)
     *   4 = Top    (+Y, pitch=-90)
     *   5 = Bottom (-Y, pitch=+90)
     */

    public static boolean tryConvert(String outputBase) throws IOException {
        Path outbot = Paths.get(outputBase);
        Path panoramaDir = outbot.resolveSibling(outbot.getFileName() + "_panorama");
        System.out.println(panoramaDir.toString());
        File screenshotsDir = panoramaDir.resolve("screenshots").toFile();
        System.out.println(screenshotsDir.toString());


        // Check all 6 faces exist before attempting to read
        for (int i = 0; i < 6; i++) {
            File face = new File(screenshotsDir, "panorama_" + i + ".png");
            if (!face.exists() || !face.canRead()) return false;
        }

        // Load all 6 faces
        BufferedImage[] faces = new BufferedImage[6];
        for (int i = 0; i < 6; i++) {
            File face = new File(screenshotsDir, "panorama_" + i + ".png");
            faces[i] = ImageIO.read(face);
            if (faces[i] == null) return false;
        }

        // Async convert to equirectangular
        final BufferedImage[] facesFinal = faces;
        File equirectFile = panoramaDir.resolve("equirectangular.png").toFile();
        Thread convertThread = new Thread(() -> {
            try {
                BufferedImage equirect = toEquirectangular(facesFinal, 8192, 4096);
                ImageIO.write(equirect, "png", equirectFile);
            } catch (Exception e) {
                // log if needed
            }
        });
        convertThread.setDaemon(true);
        convertThread.start();

        Flashback.getReplayServer().getEditorState().replayVisuals.overrideFov = true;
        return true;
    }
    public static BufferedImage toEquirectangular(BufferedImage[] faces, int outWidth, int outHeight) {
        BufferedImage out = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
        int faceSize = faces[0].getWidth();

        for (int py = 0; py < outHeight; py++) {
            double lat = Math.PI * (0.5 - (double) py / outHeight);
            double cosLat = Math.cos(lat);
            double sinLat = Math.sin(lat);

            for (int px = 0; px < outWidth; px++) {
                double lon = 2.0 * Math.PI * ((double) px / outWidth - 0.5);

                double dx = cosLat * Math.sin(lon);
                double dy = sinLat;
                double dz = cosLat * Math.cos(lon);

                int face;
                double u, v;

                double absX = Math.abs(dx);
                double absY = Math.abs(dy);
                double absZ = Math.abs(dz);

                if (absX >= absY && absX >= absZ) {
                    if (dx > 0) {
                        face = 3;
                        u = -dz / absX;
                        v = -dy / absX;
                    } else {
                        face = 1;
                        u = dz / absX;
                        v = -dy / absX;
                    }
                } else if (absY >= absX && absY >= absZ) {
                    if (dy > 0) {
                        face = 4;
                        u = dx / absY;
                        v = dz / absY;
                    } else {
                        face = 5;
                        u = dx / absY;
                        v = -dz / absY;
                    }
                } else {
                    if (dz > 0) {
                        face = 0;
                        u = dx / absZ;
                        v = -dy / absZ;
                    } else {
                        face = 2;
                        u = -dx / absZ;
                        v = -dy / absZ;
                    }
                }

                if (face == 1) face = 3;
                else if (face == 3) face = 1;

                int sx = (int) Math.min(faceSize - 1, Math.max(0, (u + 1.0) * 0.5 * faceSize));
                int sy = (int) Math.min(faceSize - 1, Math.max(0, (v + 1.0) * 0.5 * faceSize));

                out.setRGB(px, py, faces[face].getRGB(sx, sy));
            }
        }

        return out;
    }

    public static void convertCubemapToEquirectangular(Path baseFolder, String baseName, int faceSize) {
        int outWidth = faceSize * 4;
        int outHeight = faceSize * 2;
        NativeImage equi = new NativeImage(outWidth, outHeight, false);

        // 1. Cache the 6 faces in memory
        // Standard order: +X, -X, +Y, -Y, +Z, -Z
        String[] suffixes = {"_right", "_left", "_up", "_down", "_front", "_back"};
        NativeImage[] faces = new NativeImage[6];
        try {
            for (int i = 0; i < 6; i++) {
                Path path = baseFolder.resolve(baseName + suffixes[i] + ".png");
                try (var is = Files.newInputStream(path)) {
                    faces[i] = NativeImage.read(is);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // 2. Perform Projection
        for (int v = 0; v < outHeight; v++) {
            // Normalize V to [0, 1] then to latitude [-PI/2, PI/2]
            double theta = (v / (double) outHeight) * Math.PI;

            for (int u = 0; u < outWidth; u++) {
                // Normalize U to [0, 1] then to longitude [-PI, PI]
                double phi = (u / (double) outWidth) * 2 * Math.PI;

                // Spherical to Cartesian (Standard OpenGL-style coordinates)
                double x = Math.sin(theta) * Math.cos(phi);
                double y = Math.cos(theta);
                double z = Math.sin(theta) * Math.sin(phi);

                int faceIndex;
                double uc, vc;
                double absX = Math.abs(x), absY = Math.abs(y), absZ = Math.abs(z);

                // Determine which face and calculate UV (-1 to 1 range)
                if (absX >= absY && absX >= absZ) {
                    faceIndex = x > 0 ? 0 : 1; // Right (+X) or Left (-X)
                    uc = (x > 0 ? -z : z) / absX;
                    vc = -y / absX;
                } else if (absY >= absX && absY >= absZ) {
                    faceIndex = y > 0 ? 2 : 3; // Up (+Y) or Down (-Y)
                    uc = x / absY;
                    vc = (y > 0 ? z : -z) / absY;
                } else {
                    faceIndex = z > 0 ? 4 : 5; // Front (+Z) or Back (-Z)
                    uc = (z > 0 ? x : -x) / absZ;
                    vc = -y / absZ;
                }

                // Map UV (-1 to 1) to Pixel (0 to faceSize - 1)
                int px = (int) Math.min(faceSize - 1, Math.max(0, (0.5 * (uc + 1.0) * faceSize)));
                int py = (int) Math.min(faceSize - 1, Math.max(0, (0.5 * (vc + 1.0) * faceSize)));

                // Use pixel copy to ensure color bit-order is preserved
                equi.setPixel(u, v, faces[faceIndex].getPixel(px, py));
            }
        }

        // 3. Save and Cleanup
        try {
            equi.writeToFile(baseFolder.resolve(baseName + "_panorama.png"));
            for (NativeImage img : faces) {
                if (img != null) img.close();
            }
            equi.close();
            if (FlashplusClient.deleteCubeMap) {
                for (int i = 0; i < 6; i++) {
                    Path facePath = baseFolder.resolve(baseName + suffixes[i] + ".png");
                    try {
                        boolean deleted = Files.deleteIfExists(facePath);
                        if (deleted) {
                            System.out.println("Deleted source face: " + facePath.getFileName());
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to delete " + facePath.getFileName() + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}