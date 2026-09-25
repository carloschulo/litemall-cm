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
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

@Service
public class QCodeService {
    private final Log logger = LogFactory.getLog(QCodeService.class);
    @Autowired
    WxMaService wxMaService;

    @Autowired
    private StorageService storageService;


    public String createGrouponShareImage(String goodName, String goodPicUrl, LitemallGroupon groupon) {
        if (!isSafeUrl(goodPicUrl)) {
            logger.error("Invalid or unsafe goodPicUrl: " + goodPicUrl);
            return "";
        }

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

        if (!isSafeUrl(goodPicUrl)) {
            logger.error("Invalid or unsafe goodPicUrl: " + goodPicUrl);
            return "";
        }

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
        //商品图片
        BufferedImage goodImage = downloadImage(goodPicUrl);

        //底图
        ClassPathResource redResource = new ClassPathResource("back.png");
        BufferedImage red = ImageIO.read(redResource.getInputStream());

        //小程序二维码
        BufferedImage qrCodeImage = ImageIO.read(qrCodeImg);

        // --- 画图 ---

        //底层空白 bufferedImage
        BufferedImage baseImage = new BufferedImage(red.getWidth(), red.getHeight(), BufferedImage.TYPE_4BYTE_ABGR_PRE);

        //画上图片
        drawImgInImg(baseImage, red, 0, 0, red.getWidth(), red.getHeight());

        //画上商品图片
        drawImgInImg(baseImage, goodImage, 71, 69, 660, 660);

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

    private BufferedImage downloadImage(String urlString) throws IOException {
        int redirects = 0;
        String currentUrl = urlString;
        while (redirects < 5) {
            if (!isSafeUrl(currentUrl)) {
                throw new IOException("Invalid or unsafe goodPicUrl: " + currentUrl);
            }

            URL url = new URL(currentUrl);
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (!(conn instanceof HttpURLConnection)) {
                throw new IOException("Unsupported URL protocol for: " + currentUrl);
            }

            HttpURLConnection httpConn = (HttpURLConnection) conn;
            httpConn.setInstanceFollowRedirects(false);
            httpConn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = httpConn.getResponseCode();
            if (responseCode >= 300 && responseCode < 400) {
                String location = httpConn.getHeaderField("Location");
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("Missing redirect Location header for: " + currentUrl);
                }
                URL redirectUrl = new URL(url, location);
                currentUrl = redirectUrl.toString();
                redirects++;
                continue;
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to fetch image, HTTP response code: " + responseCode);
            }

            try (InputStream in = httpConn.getInputStream()) {
                BufferedImage image = ImageIO.read(in);
                if (image == null) {
                    throw new IOException("Failed to decode image from: " + currentUrl);
                }
                return image;
            }
        }

        throw new IOException("Too many redirects fetching image: " + urlString);
    }

    public boolean isSafeUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }

        URL url;
        try {
            url = new URL(urlString.trim());
        } catch (MalformedURLException e) {
            return false;
        }

        String protocol = url.getProtocol();
        if (protocol == null || (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol))) {
            return false;
        }

        try {
            URI uri = url.toURI();
            if (uri.getHost() == null) {
                return false;
            }
        } catch (URISyntaxException e) {
            // Some URLs might have unencoded characters in path/query
        }

        String host = url.getHost();
        if (host == null || host.trim().isEmpty()) {
            return false;
        }

        int port = url.getPort();
        if (port != -1 && (port <= 0 || port > 65535)) {
            return false;
        }

        String cleanHost = host.trim();
        if (cleanHost.startsWith("[") && cleanHost.endsWith("]")) {
            cleanHost = cleanHost.substring(1, cleanHost.length() - 1).trim();
        }

        String lowerHost = cleanHost.toLowerCase();
        if (lowerHost.equals("localhost") || lowerHost.endsWith(".localhost") ||
                lowerHost.endsWith(".local") || lowerHost.endsWith(".internal") ||
                lowerHost.endsWith(".corp") || lowerHost.endsWith(".home") ||
                lowerHost.endsWith(".lan")) {
            return false;
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(cleanHost);
        } catch (UnknownHostException e) {
            return false;
        }

        if (addresses == null || addresses.length == 0) {
            return false;
        }

        for (InetAddress address : addresses) {
            if (isInternalOrPrivate(address)) {
                return false;
            }
        }

        return true;
    }

    public boolean isInternalOrPrivate(InetAddress addr) {
        if (addr == null) {
            return true;
        }

        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() ||
                addr.isLinkLocalAddress() || addr.isSiteLocalAddress() ||
                addr.isMulticastAddress()) {
            return true;
        }

        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            if (b0 == 0 || b0 == 10 || b0 == 127) {
                return true;
            }
            if (b0 == 169 && b1 == 254) {
                return true;
            }
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
                return true;
            }
            if (b0 == 192 && b1 == 168) {
                return true;
            }
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
                return true;
            }
            if (b0 >= 224) {
                return true;
            }
        } else if (b.length == 16) {
            int firstByte = b[0] & 0xFF;
            if ((firstByte & 0xfe) == 0xfc) {
                return true;
            }
            boolean isIPv4MappedOrCompatible = true;
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) {
                    isIPv4MappedOrCompatible = false;
                    break;
                }
            }
            if (isIPv4MappedOrCompatible && ((b[10] == 0 && b[11] == 0) || ((b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF))) {
                try {
                    byte[] ipv4Bytes = new byte[4];
                    System.arraycopy(b, 12, ipv4Bytes, 0, 4);
                    return isInternalOrPrivate(InetAddress.getByAddress(ipv4Bytes));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }

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
