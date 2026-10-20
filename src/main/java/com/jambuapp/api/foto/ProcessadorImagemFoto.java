package com.jambuapp.api.foto;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.*;
import com.jambuapp.api.comum.Hash;
import com.jambuapp.api.comum.RegraNegocioException;
import org.springframework.stereotype.Component;

@Component
public class ProcessadorImagemFoto {
    public static final int LIMITE_UPLOAD_BYTES = 8 * 1024 * 1024;
    public static final int LIMITE_LADO_PX = 8192;
    public static final long LIMITE_PIXELS = 20_000_000L;
    public static final int LARGURA_MINIMA = 600;
    public static final int ALTURA_MINIMA = 800;
    public static final int LARGURA_MAXIMA = 1080;
    public static final int ALTURA_MAXIMA = 1440;
    public static final int LARGURA_MINIATURA = 360;
    public static final int ALTURA_MINIATURA = 480;
    private static final float[] QUALIDADES_PRINCIPAL = {0.85f, 0.80f, 0.75f, 0.70f};
    private static final float[] QUALIDADES_MINIATURA = {0.80f, 0.75f, 0.70f};

    private final FotoProperties config;

    public ProcessadorImagemFoto(FotoProperties config) {
        this.config = config;
    }

    public ImagemProcessada processar(byte[] entrada) {
        return processar(entrada, config.tetoPrincipalBytes(), config.tetoMiniaturaBytes());
    }

    public ImagemProcessada processar(byte[] entrada, int tetoPrincipalBytes, int tetoMiniaturaBytes) {
        if (entrada == null || entrada.length == 0)
            throw RegraNegocioException.invalido("Envie um arquivo de imagem JPEG ou PNG válido.");
        if (entrada.length > LIMITE_UPLOAD_BYTES)
            throw RegraNegocioException.invalido("O arquivo excede o limite de 8 MiB.");

        String formato = detectarFormatoReal(entrada);
        if ("png".equals(formato)) validarEstruturaPngSemAnimacao(entrada);

        int orientacao = "jpeg".equals(formato) ? extrairOrientacaoExif(entrada) : 1;
        BufferedImage decodificada = decodificarComLimites(entrada, formato, orientacao);
        BufferedImage orientada = normalizarOrientacaoESrgb(decodificada, orientacao);

        int larguraOriginal = orientada.getWidth();
        int alturaOriginal = orientada.getHeight();
        if (larguraOriginal < LARGURA_MINIMA || alturaOriginal < ALTURA_MINIMA)
            throw RegraNegocioException.invalido("A foto deve ter resolução mínima de 600 × 800 px após recorte 3:4.");
        if ((long) larguraOriginal * 4L != (long) alturaOriginal * 3L)
            throw RegraNegocioException.invalido("A foto deve estar na proporção 3:4 vertical.");

        int larguraAlvo = larguraOriginal > LARGURA_MAXIMA ? LARGURA_MAXIMA : larguraOriginal;
        int alturaAlvo = alturaOriginal > ALTURA_MAXIMA ? ALTURA_MAXIMA : alturaOriginal;
        BufferedImage imagemPrincipal = (larguraAlvo == larguraOriginal && alturaAlvo == alturaOriginal)
                ? orientada
                : redimensionar(orientada, larguraAlvo, alturaAlvo);
        BufferedImage imagemMiniatura = redimensionar(imagemPrincipal, LARGURA_MINIATURA, ALTURA_MINIATURA);

        byte[] bytesPrincipal = compactarJpeg(imagemPrincipal, QUALIDADES_PRINCIPAL, tetoPrincipalBytes,
                "Não foi possível compactar a foto dentro do limite de 1 MiB.");
        byte[] bytesMiniatura = compactarJpeg(imagemMiniatura, QUALIDADES_MINIATURA, tetoMiniaturaBytes,
                "Não foi possível compactar a miniatura dentro do limite de 200 KiB.");

        return new ImagemProcessada(bytesPrincipal, bytesMiniatura, Hash.sha256Bytes(bytesPrincipal),
                imagemPrincipal.getWidth(), imagemPrincipal.getHeight());
    }

    private static String detectarFormatoReal(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        throw RegraNegocioException.invalido("Formato de imagem não suportado. Envie JPEG ou PNG estático.");
    }

    private static void validarEstruturaPngSemAnimacao(byte[] b) {
        int pos = 8;
        boolean viuIhdr = false;
        boolean viuIend = false;
        while (pos + 8 <= b.length) {
            int len = lerIntBE(b, pos);
            if (len < 0 || pos + 12L + len > b.length)
                throw RegraNegocioException.invalido("Arquivo PNG corrompido ou truncado.");
            String tipo = new String(b, pos + 4, 4, StandardCharsets.US_ASCII);
            if ("acTL".equals(tipo) || "fcTL".equals(tipo) || "fdAT".equals(tipo))
                throw RegraNegocioException.invalido("Imagens animadas não são permitidas.");
            if ("IHDR".equals(tipo)) viuIhdr = true;
            if ("IEND".equals(tipo)) {
                viuIend = true;
                break;
            }
            pos += 12 + len;
        }
        if (!viuIhdr || !viuIend)
            throw RegraNegocioException.invalido("Arquivo PNG inválido.");
    }

    private static BufferedImage decodificarComLimites(byte[] entrada, String formato, int orientacao) {
        Iterator<ImageReader> leitores = ImageIO.getImageReadersByFormatName(formato);
        if (!leitores.hasNext())
            throw new IllegalStateException("Leitor de imagem não disponível para " + formato);
        ImageReader leitor = leitores.next();
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(entrada))) {
            if (stream == null) throw RegraNegocioException.invalido("Não foi possível ler a imagem enviada.");
            leitor.setInput(stream, false, false);
            int larguraBruta;
            int alturaBruta;
            try {
                larguraBruta = leitor.getWidth(0);
                alturaBruta = leitor.getHeight(0);
            } catch (Exception ex) {
                throw RegraNegocioException.invalido("Arquivo de imagem inválido ou corrompido.");
            }
            if (larguraBruta <= 0 || alturaBruta <= 0)
                throw RegraNegocioException.invalido("Dimensões de imagem inválidas.");
            if (larguraBruta > LIMITE_LADO_PX || alturaBruta > LIMITE_LADO_PX
                    || (long) larguraBruta * (long) alturaBruta > LIMITE_PIXELS)
                throw RegraNegocioException.invalido("A imagem excede o limite de 20 megapixels ou 8192 px por lado.");

            int larguraVisual = orientacao >= 5 ? alturaBruta : larguraBruta;
            int alturaVisual = orientacao >= 5 ? larguraBruta : alturaBruta;
            if ((long) larguraVisual * 4L != (long) alturaVisual * 3L)
                throw RegraNegocioException.invalido("A foto deve estar na proporção 3:4 vertical.");
            if (larguraVisual < LARGURA_MINIMA || alturaVisual < ALTURA_MINIMA)
                throw RegraNegocioException.invalido("A foto deve ter resolução mínima de 600 × 800 px após recorte 3:4.");

            BufferedImage imagem;
            try {
                imagem = leitor.read(0);
            } catch (Exception ex) {
                throw RegraNegocioException.invalido("Não foi possível decodificar a imagem enviada.");
            }
            if (imagem == null || imagem.getWidth() != larguraBruta || imagem.getHeight() != alturaBruta)
                throw RegraNegocioException.invalido("Imagem decodificada inconsistente.");
            return imagem;
        } catch (IOException ex) {
            throw RegraNegocioException.invalido("Não foi possível decodificar a imagem enviada.");
        } finally {
            leitor.dispose();
        }
    }

    private static BufferedImage normalizarOrientacaoESrgb(BufferedImage origem, int orientacao) {
        int w = origem.getWidth();
        int h = origem.getHeight();
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(origem, 0, 0, null);
        } finally {
            g.dispose();
        }
        if (orientacao <= 1 || orientacao > 8) return rgb;

        int dstW = orientacao >= 5 ? h : w;
        int dstH = orientacao >= 5 ? w : h;
        BufferedImage rotacionada = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = rgb.getRGB(x, y);
                switch (orientacao) {
                    case 2 -> rotacionada.setRGB(w - 1 - x, y, pixel);
                    case 3 -> rotacionada.setRGB(w - 1 - x, h - 1 - y, pixel);
                    case 4 -> rotacionada.setRGB(x, h - 1 - y, pixel);
                    case 5 -> rotacionada.setRGB(y, x, pixel);
                    case 6 -> rotacionada.setRGB(h - 1 - y, x, pixel);
                    case 7 -> rotacionada.setRGB(h - 1 - y, w - 1 - x, pixel);
                    case 8 -> rotacionada.setRGB(y, w - 1 - x, pixel);
                    default -> rotacionada.setRGB(x, y, pixel);
                }
            }
        }
        return rotacionada;
    }

    private static BufferedImage redimensionar(BufferedImage origem, int larguraAlvo, int alturaAlvo) {
        BufferedImage atual = origem;
        int w = origem.getWidth();
        int h = origem.getHeight();
        while (w / 2 >= larguraAlvo && h / 2 >= alturaAlvo) {
            w /= 2;
            h /= 2;
            BufferedImage passo = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = passo.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(atual, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }
            atual = passo;
        }
        if (w == larguraAlvo && h == alturaAlvo) return atual;
        BufferedImage destino = new BufferedImage(larguraAlvo, alturaAlvo, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(atual, 0, 0, larguraAlvo, alturaAlvo, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private static byte[] compactarJpeg(BufferedImage imagem, float[] qualidades, int tetoBytes, String mensagemErro) {
        for (float qualidade : qualidades) {
            byte[] bytes = codificarJpegSemMetadados(imagem, qualidade);
            if (bytes.length <= tetoBytes) return bytes;
        }
        throw RegraNegocioException.invalido(mensagemErro);
    }

    private static byte[] codificarJpegSemMetadados(BufferedImage imagem, float qualidade) {
        Iterator<ImageWriter> escritores = ImageIO.getImageWritersByFormatName("jpeg");
        if (!escritores.hasNext()) throw new IllegalStateException("Escritor JPEG não disponível.");
        ImageWriter escritor = escritores.next();
        ByteArrayOutputStream saida = new ByteArrayOutputStream(65536);
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(saida)) {
            escritor.setOutput(stream);
            JPEGImageWriteParam param = new JPEGImageWriteParam(null);
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(qualidade);
            param.setOptimizeHuffmanTables(true);
            escritor.write(null, new IIOImage(imagem, null, null), param);
            stream.flush();
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao codificar JPEG normalizado.", ex);
        } finally {
            escritor.dispose();
        }
    }

    static int extrairOrientacaoExif(byte[] b) {
        int pos = 2;
        while (pos + 4 <= b.length) {
            if ((b[pos] & 0xFF) != 0xFF) return 1;
            while (pos < b.length && (b[pos] & 0xFF) == 0xFF) pos++;
            if (pos >= b.length) return 1;
            int marcador = b[pos++] & 0xFF;
            if (marcador == 0xDA || marcador == 0xD9) return 1;
            if (marcador == 0x01 || (marcador >= 0xD0 && marcador <= 0xD7)) continue;
            if (pos + 2 > b.length) return 1;
            int tamanhoSegmento = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
            if (tamanhoSegmento < 2 || pos + tamanhoSegmento > b.length) return 1;
            if (marcador == 0xE1 && tamanhoSegmento >= 16) {
                int inicio = pos + 2;
                if (b[inicio] == 'E' && b[inicio + 1] == 'x' && b[inicio + 2] == 'i'
                        && b[inicio + 3] == 'f' && b[inicio + 4] == 0 && b[inicio + 5] == 0) {
                    int tiff = inicio + 6;
                    int fim = pos + tamanhoSegmento;
                    if (tiff + 8 <= fim) {
                        boolean littleEndian;
                        if (b[tiff] == 'I' && b[tiff + 1] == 'I') littleEndian = true;
                        else if (b[tiff] == 'M' && b[tiff + 1] == 'M') littleEndian = false;
                        else return 1;
                        if (lerU16(b, tiff + 2, littleEndian) != 42) return 1;
                        int offsetIfd = lerInt(b, tiff + 4, littleEndian);
                        int ifd = tiff + offsetIfd;
                        if (offsetIfd >= 8 && ifd + 2 <= fim) {
                            int entradas = lerU16(b, ifd, littleEndian);
                            for (int i = 0; i < entradas; i++) {
                                int entrada = ifd + 2 + i * 12;
                                if (entrada + 12 > fim) break;
                                int tag = lerU16(b, entrada, littleEndian);
                                if (tag == 0x0112) {
                                    int valor = lerU16(b, entrada + 8, littleEndian);
                                    return (valor >= 1 && valor <= 8) ? valor : 1;
                                }
                            }
                        }
                    }
                }
            }
            pos += tamanhoSegmento;
        }
        return 1;
    }

    private static int lerU16(byte[] b, int offset, boolean littleEndian) {
        return littleEndian
                ? ((b[offset] & 0xFF) | ((b[offset + 1] & 0xFF) << 8))
                : (((b[offset] & 0xFF) << 8) | (b[offset + 1] & 0xFF));
    }

    private static int lerInt(byte[] b, int offset, boolean littleEndian) {
        return littleEndian
                ? ((b[offset] & 0xFF) | ((b[offset + 1] & 0xFF) << 8)
                | ((b[offset + 2] & 0xFF) << 16) | ((b[offset + 3] & 0xFF) << 24))
                : lerIntBE(b, offset);
    }

    private static int lerIntBE(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24) | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8) | (b[offset + 3] & 0xFF);
    }

    public record ImagemProcessada(byte[] principal, byte[] miniatura, String hashConteudo, int largura, int altura) {}
}
