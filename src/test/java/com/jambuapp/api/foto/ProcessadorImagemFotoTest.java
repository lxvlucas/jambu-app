package com.jambuapp.api.foto;

import static org.assertj.core.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Random;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import com.jambuapp.api.comum.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessadorImagemFotoTest {
    private final ProcessadorImagemFoto processador = new ProcessadorImagemFoto(new FotoProperties(
            null, "local", null, null, null, false, "rekognition", null, null, null,
            "2026-10-v1", 80.0, 40.0, 3, null, null, 10, false, null, 1_048_576, 204_800, null));

    @Test
    void rejeitaEntradaVaziaOuAcimaDe8MiB() {
        assertThatThrownBy(() -> processador.processar(null))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> processador.processar(new byte[0]))
                .isInstanceOf(RegraNegocioException.class);
        byte[] gigante = new byte[ProcessadorImagemFoto.LIMITE_UPLOAD_BYTES + 1];
        gigante[0] = (byte) 0xFF;
        gigante[1] = (byte) 0xD8;
        gigante[2] = (byte) 0xFF;
        assertThatThrownBy(() -> processador.processar(gigante))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("8 MiB");
    }

    @Test
    void rejeitaFormatoInvalidoDisfarcadoOuPngAnimado() throws Exception {
        assertThatThrownBy(() -> processador.processar("GIF89a-falso-jpeg".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("JPEG ou PNG");
        assertThatThrownBy(() -> processador.processar("RIFF____WEBP".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(RegraNegocioException.class);

        byte[] apng = criarPngComChunkExtra(600, 800, "acTL", new byte[]{0, 0, 0, 2, 0, 0, 0, 0});
        assertThatThrownBy(() -> processador.processar(apng))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("animadas");
    }

    @Test
    void rejeitaImagemAcimaDe20MpOu8192PxSemAlocar() {
        byte[] ladoExcessivo = criarCabecalhoPngSintetico(9000, 12000);
        assertThatThrownBy(() -> processador.processar(ladoExcessivo))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("20 megapixels ou 8192 px");

        byte[] pixelsExcessivos = criarCabecalhoPngSintetico(4500, 6000);
        assertThatThrownBy(() -> processador.processar(pixelsExcessivos))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("20 megapixels ou 8192 px");
    }

    @Test
    void rejeitaProporcaoInvalidaEResolucaoInsuficiente() throws Exception {
        byte[] quadrada = gerarJpeg(800, 800, Color.BLUE);
        assertThatThrownBy(() -> processador.processar(quadrada))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("3:4");

        byte[] paisagemSemExif = gerarJpeg(800, 600, Color.BLUE);
        assertThatThrownBy(() -> processador.processar(paisagemSemExif))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("3:4");

        byte[] pequena = gerarJpeg(300, 400, Color.BLUE);
        assertThatThrownBy(() -> processador.processar(pequena))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("600 × 800");
    }

    @Test
    void aplicaOrientacaoExifERemoveMetadadosExifGpsDaSaida() throws Exception {
        // Fisicamente 800x600 (4:3), mas com EXIF Orientation = 6 (90° horário -> 600x800, 3:4).
        byte[] jpegBruto = gerarJpeg(800, 600, new Color(20, 120, 220));
        byte[] jpegComExifEGps = injetarExifOrientacaoEGpsFalso(jpegBruto, 6, "GPS_COORD_SECRETA_-2.53_-44.30");
        assertThat(ProcessadorImagemFoto.extrairOrientacaoExif(jpegComExifEGps)).isEqualTo(6);

        var resultado = processador.processar(jpegComExifEGps);
        assertThat(resultado.largura()).isEqualTo(600);
        assertThat(resultado.altura()).isEqualTo(800);
        assertThat(resultado.hashConteudo()).matches("^[0-9a-f]{64}$");

        String principalAscii = new String(resultado.principal(), StandardCharsets.ISO_8859_1);
        String miniaturaAscii = new String(resultado.miniatura(), StandardCharsets.ISO_8859_1);
        assertThat(principalAscii).doesNotContain("Exif", "GPS_COORD_SECRETA");
        assertThat(miniaturaAscii).doesNotContain("Exif", "GPS_COORD_SECRETA");

        BufferedImage imgPrincipal = ImageIO.read(new ByteArrayInputStream(resultado.principal()));
        BufferedImage imgMiniatura = ImageIO.read(new ByteArrayInputStream(resultado.miniatura()));
        assertThat(imgPrincipal.getWidth()).isEqualTo(600);
        assertThat(imgPrincipal.getHeight()).isEqualTo(800);
        assertThat(imgMiniatura.getWidth()).isEqualTo(360);
        assertThat(imgMiniatura.getHeight()).isEqualTo(480);
    }

    @Test
    void mantemResolucaoIntermediariaSemAmpliarEReduzAcimaDe1080x1440() throws Exception {
        var intermediaria = processador.processar(gerarJpeg(750, 1000, Color.ORANGE));
        assertThat(intermediaria.largura()).isEqualTo(750);
        assertThat(intermediaria.altura()).isEqualTo(1000);

        var grande = processador.processar(gerarJpeg(1500, 2000, Color.GREEN));
        assertThat(grande.largura()).isEqualTo(1080);
        assertThat(grande.altura()).isEqualTo(1440);
        BufferedImage miniatura = ImageIO.read(new ByteArrayInputStream(grande.miniatura()));
        assertThat(miniatura.getWidth()).isEqualTo(360);
        assertThat(miniatura.getHeight()).isEqualTo(480);
    }

    @Test
    void compoeTransparenciaPngSobreFundoBranco() throws Exception {
        BufferedImage transparente = new BufferedImage(600, 800, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(transparente, "png", baos);

        var processada = processador.processar(baos.toByteArray());
        BufferedImage saida = ImageIO.read(new ByteArrayInputStream(processada.principal()));
        Color pixel = new Color(saida.getRGB(300, 400));
        assertThat(pixel.getRed()).isGreaterThan(250);
        assertThat(pixel.getGreen()).isGreaterThan(250);
        assertThat(pixel.getBlue()).isGreaterThan(250);
    }

    @Test
    void respeitaTetoEmBytesERejeitaQuandoCompactacaoNaoCabeNoTeto() throws Exception {
        BufferedImage ruidosa = new BufferedImage(1080, 1440, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < 1440; y++) {
            for (int x = 0; x < 1080; x++) {
                ruidosa.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(ruidosa, "png", baos);
        byte[] entrada = baos.toByteArray();

        var ok = processador.processar(entrada);
        assertThat(ok.principal().length).isLessThanOrEqualTo(1_048_576);
        assertThat(ok.miniatura().length).isLessThanOrEqualTo(204_800);

        assertThatThrownBy(() -> processador.processar(entrada, 1024, 204_800))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("1 MiB");
    }

    @Test
    void armazenamentoLocalGravaLeApagaEBloqueiaPathTraversal(@TempDir Path dir) {
        var storage = new ArmazenamentoFotosLocal(dir);
        assertThatThrownBy(() -> storage.gravar("../escape.jpg", new byte[]{1, 2, 3}))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> storage.gravar("fotos/sub/escape.jpg", new byte[]{1, 2, 3}))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> storage.gravar("fotos/10000000-0000-0000-0000-000000000001/arquivo.png", new byte[]{1, 2, 3}))
                .isInstanceOf(RegraNegocioException.class);

        String chave = "fotos/10000000-0000-0000-0000-000000000001/principal.jpg";
        byte[] conteudo = new byte[]{10, 20, 30, 40};
        storage.gravar(chave, conteudo);
        assertThat(storage.existe(chave)).isTrue();
        assertThat(storage.ler(chave)).isEqualTo(conteudo);
        storage.apagar(chave);
        assertThat(storage.existe(chave)).isFalse();
        assertThatThrownBy(() -> storage.ler(chave)).isInstanceOf(RegraNegocioException.class);
    }

    static byte[] gerarJpeg(int largura, int altura, Color cor) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(cor);
            g.fillRect(0, 0, largura, altura);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        return out.toByteArray();
    }

    private static byte[] injetarExifOrientacaoEGpsFalso(byte[] jpeg, int orientacao, String marcadorGps) throws IOException {
        ByteArrayOutputStream app1 = new ByteArrayOutputStream();
        app1.write(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        // Cabeçalho TIFF Big Endian ("MM", 0x002A, offset IFD = 8)
        app1.write(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8});
        // 1 entrada no IFD0: tag 0x0112 (Orientation), tipo 3 (SHORT), count 1, valor = orientacao
        app1.write(new byte[]{0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, (byte) (orientacao >> 8), (byte) orientacao});
        app1.write(new byte[]{0, 0, 0, 0});
        app1.write(marcadorGps.getBytes(StandardCharsets.US_ASCII));
        byte[] payload = app1.toByteArray();
        int len = payload.length + 2;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((len >> 8) & 0xFF);
        out.write(len & 0xFF);
        out.write(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] criarCabecalhoPngSintetico(int largura, int altura) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            ByteBuffer ihdr = ByteBuffer.allocate(13);
            ihdr.putInt(largura);
            ihdr.putInt(altura);
            ihdr.put((byte) 8);
            ihdr.put((byte) 2);
            ihdr.put((byte) 0);
            ihdr.put((byte) 0);
            ihdr.put((byte) 0);
            escreverChunkPng(out, "IHDR", ihdr.array());
            escreverChunkPng(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static byte[] criarPngComChunkExtra(int largura, int altura, String tipoExtra, byte[] dadosExtra) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        ByteBuffer ihdr = ByteBuffer.allocate(13);
        ihdr.putInt(largura);
        ihdr.putInt(altura);
        ihdr.put((byte) 8);
        ihdr.put((byte) 2);
        ihdr.put((byte) 0);
        ihdr.put((byte) 0);
        ihdr.put((byte) 0);
        escreverChunkPng(out, "IHDR", ihdr.array());
        escreverChunkPng(out, tipoExtra, dadosExtra);
        escreverChunkPng(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void escreverChunkPng(OutputStream out, String tipo, byte[] dados) throws IOException {
        byte[] tipoBytes = tipo.getBytes(StandardCharsets.US_ASCII);
        out.write(ByteBuffer.allocate(4).putInt(dados.length).array());
        out.write(tipoBytes);
        out.write(dados);
        CRC32 crc = new CRC32();
        crc.update(tipoBytes);
        crc.update(dados);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
