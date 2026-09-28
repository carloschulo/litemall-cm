package org.linlinjava.litemall.core.qcode;

import cn.binarywang.wx.miniapp.api.WxMaService;
import me.chanjar.weixin.common.error.WxErrorException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.storage.StorageService;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.db.domain.LitemallStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;

@Service
public class QCodeService {
    private final Log logger = LogFactory.getLog(QCodeService.class);
    @Autowired
    WxMaService wxMaService;

    @Autowired
    private StorageService storageService;


    public String createGrouponShareImage(String goodName, String goodPicUrl, LitemallGroupon groupon) {
        try {
            //创建该商品的二维码
            File file = wxMaService.getQrcodeService().createWxaCodeUnlimit("groupon," + groupon.getId(), "pages" +
                    "/index/index");
            FileInputStream inputStream = new FileInputStream(file);
            //将商品图片，商品名字,商城名字画到模版图中
            byte[] imageData = drawPicture(inputStream, goodPicUrl, goodName);
            ByteArrayInputStream inputStream2 = new ByteArrayInputStream(imageData);
            //存储分享图
            LitemallStorage storageInfo = storageService.store(inputStream2, imageData.length, "image/jpeg",
                    getKeyName(groupon.getId().toString()));

            return storageInfo.getUrl();
        } catch (WxErrorException e) {
            logger.error(e.getMessage(), e);
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage(), e);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        return "";
    }


    /**
     * 创建商品分享图
     *
     * @param goodId
     * @param goodPicUrl
     * @param goodName
     */
    public String createGoodShareImage(String goodId, String goodPicUrl, String goodName) {
        if (!SystemConfig.isAutoCreateShareImage())
            return "";

        try {
            //创建该商品的二维码
            File file = wxMaService.getQrcodeService().createWxaCodeUnlimit("goods," + goodId, "pages/index/index");
            FileInputStream inputStream = new FileInputStream(file);
            //将商品图片，商品名字,商城名字画到模版图中
            byte[] imageData = drawPicture(inputStream, goodPicUrl, goodName);
            ByteArrayInputStream inputStream2 = new ByteArrayInputStream(imageData);
            //存储分享图
            LitemallStorage litemallStorage = storageService.store(inputStream2, imageData.length, "image/jpeg",
                    getKeyName(goodId));

            return litemallStorage.getUrl();
        } catch (WxErrorException e) {
            logger.error(e.getMessage(), e);
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage(), e);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        return "";
    }

    private String getKeyName(String goodId) {
        return "GOOD_QCODE_" + goodId + ".jpg";
    }

    /**
     * 将商品图片，商品名字画到模版图中
     *
     * @param qrCodeImg  二维码图片
     * @param goodPicUrl 商品图片地址
     * @param goodName   商品名称
     * @return
     * @throws IOException
     */
    private byte[] drawPicture(InputStream qrCodeImg, String goodPicUrl, String goodName) throws IOException {
        //底图
        ClassPathResource redResource = new ClassPathResource("back.png");
        BufferedImage red = ImageIO.read(redResource.getInputStream());


        //商品图片
        BufferedImage goodImage = loadSafeImage(goodPicUrl);

        //小程序二维码
        BufferedImage qrCodeImage = ImageIO.read(qrCodeImg);

        // --- 画图 ---

        //底层空白 bufferedImage
        BufferedImage baseImage = new BufferedImage(red.getWidth(), red.getHeight(), BufferedImage.TYPE_4BYTE_ABGR_PRE);

        //画上图片
        drawImgInImg(baseImage, red, 0, 0, red.getWidth(), red.getHeight());

        //画上商品图片
        if (goodImage != null) {
            drawImgInImg(baseImage, goodImage, 71, 69, 660, 660);
        }

        //画上小程序二维码
        drawImgInImg(baseImage, qrCodeImage, 448, 767, 300, 300);

        //写上商品名称
        drawTextInImg(baseImage, goodName, 65, 867);

        //写上商城名称
        //        drawTextInImgCenter(baseImage, shopName, 98);


        //转jpg
        BufferedImage result = new BufferedImage(baseImage.getWidth(), baseImage
                .getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        result.getGraphics().drawImage(baseImage, 0, 0, null);
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        ImageIO.write(result, "jpg", bs);

        //最终byte数组
        return bs.toByteArray();
    }

    private BufferedImage loadSafeImage(String goodPicUrl) throws IOException {
        if (goodPicUrl == null || goodPicUrl.trim().isEmpty()) {
            throw new IOException("goodPicUrl cannot be empty");
        }

        String currentUrlStr = goodPicUrl.trim();
        int maxRedirects = 5;

        for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
            URL currentUrl;
            try {
                currentUrl = new URL(currentUrlStr);
            } catch (MalformedURLException e) {
                throw new IOException("Malformed image URL: " + currentUrlStr, e);
            }

            String protocol = currentUrl.getProtocol();
            if (protocol == null || (!protocol.equalsIgnoreCase("http") && !protocol.equalsIgnoreCase("https"))) {
                throw new IOException("Unsupported protocol for image URL: " + protocol);
            }

            String host = currentUrl.getHost();
            if (host == null || host.trim().isEmpty()) {
                throw new IOException("Host cannot be empty in image URL: " + currentUrlStr);
            }

            String cleanHost = host.trim();
            if (cleanHost.startsWith("[") && cleanHost.endsWith("]")) {
                cleanHost = cleanHost.substring(1, cleanHost.length() - 1);
            }

            InetAddress[] addresses = InetAddress.getAllByName(cleanHost);
            if (addresses == null || addresses.length == 0) {
                throw new IOException("Cannot resolve host: " + host);
            }

            for (InetAddress address : addresses) {
                if (isInternalOrReservedIp(address)) {
                    throw new IOException("Access to internal/reserved address is forbidden: " + address.getHostAddress());
                }
            }

            URLConnection urlConnection = currentUrl.openConnection();
            if (!(urlConnection instanceof HttpURLConnection)) {
                throw new IOException("Non-HTTP connection: " + currentUrlStr);
            }

            HttpURLConnection conn = (HttpURLConnection) urlConnection;
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; LitemallBot/1.0)");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {

                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("Redirect with empty Location header");
                }
                URL nextUrl = new URL(currentUrl, location.trim());
                currentUrlStr = nextUrl.toString();
                continue;
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect();
                throw new IOException("Failed to fetch image, HTTP response code: " + responseCode);
            }

            long contentLength = conn.getContentLengthLong();
            if (contentLength > 20 * 1024 * 1024) {
                conn.disconnect();
                throw new IOException("Image size exceeds limit: " + contentLength);
            }

            try (InputStream in = conn.getInputStream()) {
                return ImageIO.read(in);
            } finally {
                conn.disconnect();
            }
        }

        throw new IOException("Too many redirects fetching image: " + goodPicUrl);
    }

    private static boolean isInternalOrReservedIp(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() ||
                addr.isLinkLocalAddress() || addr.isSiteLocalAddress() ||
                addr.isMulticastAddress()) {
            return true;
        }

        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            return isInternalOrReservedIpv4(raw);
        } else if (raw.length == 16) {
            // Check for IPv4-mapped IPv6 address (::ffff:x.x.x.x) or IPv4-compatible (::x.x.x.x)
            boolean isMapped = true;
            for (int i = 0; i < 10; i++) {
                if (raw[i] != 0) {
                    isMapped = false;
                    break;
                }
            }
            if (isMapped) {
                if ((raw[10] & 0xFF) == 0xFF && (raw[11] & 0xFF) == 0xFF) {
                    // ::ffff:x.x.x.x
                    byte[] ipv4 = new byte[4];
                    System.arraycopy(raw, 12, ipv4, 0, 4);
                    return isInternalOrReservedIpv4(ipv4);
                } else if (raw[10] == 0 && raw[11] == 0) {
                    // ::x.x.x.x
                    byte[] ipv4 = new byte[4];
                    System.arraycopy(raw, 12, ipv4, 0, 4);
                    return isInternalOrReservedIpv4(ipv4);
                }
            }

            int b0 = raw[0] & 0xFF;
            int b1 = raw[1] & 0xFF;

            // Unique Local Address fc00::/7 (fc00:: - fdff::)
            if ((b0 & 0xFE) == 0xFC) {
                return true;
            }
            // Link-local unicast fe80::/10
            if (b0 == 0xFE && (b1 & 0xC0) == 0x80) {
                return true;
            }
            // Site-local unicast (deprecated) fec0::/10
            if (b0 == 0xFE && (b1 & 0xC0) == 0xC0) {
                return true;
            }
            // Discard prefix 100::/64
            if (b0 == 0x01 && b1 == 0x00) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInternalOrReservedIpv4(byte[] raw) {
        int b0 = raw[0] & 0xFF;
        int b1 = raw[1] & 0xFF;
        int b2 = raw[2] & 0xFF;
        int b3 = raw[3] & 0xFF;

        // 0.0.0.0/8 (Current network)
        if (b0 == 0) return true;
        // 10.0.0.0/8 (Private network)
        if (b0 == 10) return true;
        // 100.64.0.0/10 (Shared address space / Carrier grade NAT)
        if (b0 == 100 && (b1 & 0xC0) == 64) return true;
        // 127.0.0.0/8 (Loopback)
        if (b0 == 127) return true;
        // 169.254.0.0/16 (Link-local)
        if (b0 == 169 && b1 == 254) return true;
        // 172.16.0.0/12 (Private network)
        if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;
        // 192.0.0.0/24 (IETF protocol assignments)
        if (b0 == 192 && b1 == 0 && b2 == 0) return true;
        // 192.0.2.0/24 (TEST-NET-1)
        if (b0 == 192 && b1 == 0 && b2 == 2) return true;
        // 192.88.99.0/24 (6to4 relay anycast)
        if (b0 == 192 && b1 == 88 && b2 == 99) return true;
        // 192.168.0.0/16 (Private network)
        if (b0 == 192 && b1 == 168) return true;
        // 198.18.0.0/15 (Benchmarking)
        if (b0 == 198 && (b1 == 18 || b1 == 19)) return true;
        // 198.51.100.0/24 (TEST-NET-2)
        if (b0 == 198 && b1 == 51 && b2 == 100) return true;
        // 203.0.113.0/24 (TEST-NET-3)
        if (b0 == 203 && b1 == 0 && b2 == 113) return true;
        // 224.0.0.0/4 (Multicast)
        if ((b0 & 0xF0) == 224) return true;
        // 240.0.0.0/4 (Reserved / Future use)
        if ((b0 & 0xF0) == 240) return true;
        // 255.255.255.255 (Broadcast)
        if (b0 == 255 && b1 == 255 && b2 == 255 && b3 == 255) return true;

        return false;
    }

    private void drawTextInImgCenter(BufferedImage baseImage, String textToWrite, int y) {
        Graphics2D g2D = (Graphics2D) baseImage.getGraphics();
        g2D.setColor(new Color(167, 136, 69));

        String fontName = "Microsoft YaHei";

        Font f = new Font(fontName, Font.PLAIN, 28);
        g2D.setFont(f);
        g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 计算文字长度，计算居中的x点坐标
        FontMetrics fm = g2D.getFontMetrics(f);
        int textWidth = fm.stringWidth(textToWrite);
        int widthX = (baseImage.getWidth() - textWidth) / 2;
        // 表示这段文字在图片上的位置(x,y) .第一个是你设置的内容。

        g2D.drawString(textToWrite, widthX, y);
        // 释放对象
        g2D.dispose();
    }

    private void drawTextInImg(BufferedImage baseImage, String textToWrite, int x, int y) {
        Graphics2D g2D = (Graphics2D) baseImage.getGraphics();
        g2D.setColor(new Color(167, 136, 69));

        //TODO 注意，这里的字体必须安装在服务器上
        g2D.setFont(new Font("Microsoft YaHei", Font.PLAIN, 28));
        g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2D.drawString(textToWrite, x, y);
        g2D.dispose();
    }

    private void drawImgInImg(BufferedImage baseImage, BufferedImage imageToWrite, int x, int y, int width,
                              int heigth) {
        Graphics2D g2D = (Graphics2D) baseImage.getGraphics();
        g2D.drawImage(imageToWrite, x, y, width, heigth, null);
        g2D.dispose();
    }
}
