import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

public final class GenerateS03RealFontCorpus {
    private static final int PAIR_COUNT = 50;
    private static final int WIDTH = 480;
    private static final int HEIGHT = 128;
    private static final String VERSION = "real-font-paired-v2";
    private static final String FONT_NAME = "Noto Sans Regular";
    private static final String FONT_COMMIT = "2fe1e8c6b137fb13041a7cfca6fdcd8a910ea4dc";
    private static final String FONT_URL =
        "https://raw.githubusercontent.com/google/fonts/" + FONT_COMMIT +
        "/ofl/notosans/NotoSans%5Bwdth,wght%5D.ttf";

    private GenerateS03RealFontCorpus() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: <font.ttf> <output-directory>");
        }
        Path fontPath = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Path imageDirectory = output.resolve("images");
        Files.createDirectories(imageDirectory);
        try (var files = Files.list(imageDirectory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".png"))
                .forEach(GenerateS03RealFontCorpus::delete);
        }

        Font font;
        try (InputStream input = Files.newInputStream(fontPath)) {
            font = Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(Font.PLAIN, 64f);
        }
        MessageDigest corpusDigest = MessageDigest.getInstance("SHA-256");
        long totalBytes = 0;
        for (int value = 0; value < PAIR_COUNT; value++) {
            totalBytes += render(
                font,
                "ITEM " + value,
                imageDirectory.resolve(filename(value, "plain")),
                corpusDigest
            );
            totalBytes += render(
                font,
                "ITEM " + String.format("%03d", value),
                imageDirectory.resolve(filename(value, "padded")),
                corpusDigest
            );
        }

        String manifest = """
            {
              "version": "%s",
              "pairCount": %d,
              "sampleCount": %d,
              "width": %d,
              "height": %d,
              "fontName": "%s",
              "fontLicense": "SIL Open Font License 1.1",
              "fontSource": "%s",
              "fontSha256": "%s",
              "corpusBytes": %d,
              "corpusSha256": "%s"
            }
            """.formatted(
                VERSION,
                PAIR_COUNT,
                PAIR_COUNT * 2,
                WIDTH,
                HEIGHT,
                FONT_NAME,
                FONT_URL,
                sha256(fontPath),
                totalBytes,
                HexFormat.of().formatHex(corpusDigest.digest())
            );
        Files.writeString(output.resolve("corpus.json"), manifest);
    }

    private static long render(
        Font font,
        String text,
        Path output,
        MessageDigest corpusDigest
    ) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            graphics.setColor(Color.BLACK);
            graphics.setFont(font);
            graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );
            graphics.setRenderingHint(
                RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON
            );
            FontMetrics metrics = graphics.getFontMetrics();
            int x = (WIDTH - metrics.stringWidth(text)) / 2;
            int y = (HEIGHT - metrics.getHeight()) / 2 + metrics.getAscent();
            graphics.drawString(text, x, y);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "png", output.toFile())) {
            throw new IOException("PNG writer unavailable");
        }
        byte[] bytes = Files.readAllBytes(output);
        corpusDigest.update(bytes);
        return bytes.length;
    }

    private static String filename(int value, String style) {
        return "pair-%03d-%s.png".formatted(value, style);
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        );
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
