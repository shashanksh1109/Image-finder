package com.eulerity.hackathon.imagefinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@WebServlet(name = "ImageFinder", urlPatterns = { "/main" })
public class ImageFinder extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    // This is needed for tests to work
    public static final String[] testImages = {
        "https://example.com/image1.jpg",
        "https://example.com/image2.png"
    };
    
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_THREADS = 10;
    private static final int MAX_CRAWL_PAGES = 100; // Limit total crawled pages

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        
        String url = req.getParameter("url");
        if (url == null || url.isEmpty()) {
            resp.getWriter().print("{\"error\": \"URL parameter is required\"}");
            return;
        }
        
        List<String> imageUrls = findImages(url);
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        resp.getWriter().print(gson.toJson(imageUrls));
    }
    
    private List<String> findImages(String url) {
        try {
            // Extract domain for same-domain restriction
            String domain = extractDomain(url);
            if (domain == null) return new ArrayList<>();
            
            // Track visited URLs
            Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
            Set<String> foundImages = ConcurrentHashMap.newKeySet();
            
            // Create thread pool for crawling
            ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
            
            // Start with the seed URL
            visitedUrls.add(url);
            crawlPage(url, domain, visitedUrls, foundImages, executor);
            
            // Wait for completion
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
            
            // Return results
            return new ArrayList<>(foundImages);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    private void crawlPage(String url, String domain, Set<String> visitedUrls, 
                         Set<String> foundImages, ExecutorService executor) {
        try {
            // Stop if we've reached the maximum number of pages
            if (visitedUrls.size() > MAX_CRAWL_PAGES) {
                return;
            }
            
            // Add a small delay to be a friendly crawler
            Thread.sleep(200);
            
            // Connect to URL
            Document doc = Jsoup.connect(url)
                .userAgent("EulerityImageFinder/1.0")
                .timeout(TIMEOUT_MS)
                .get();
            
            // Find all images
            Elements imgElements = doc.select("img[src]");
            for (Element img : imgElements) {
                String imgUrl = img.absUrl("src");
                if (isValidImageUrl(imgUrl)) {
                    foundImages.add(imgUrl);
                }
            }
            
            // Also look for background images in CSS
            Elements elementsWithStyle = doc.select("[style]");
            for (Element element : elementsWithStyle) {
                String style = element.attr("style");
                if (style.contains("background-image")) {
                    extractBackgroundImage(style, url, foundImages);
                }
            }
            
            // Find all links and recursively crawl them
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String nextUrl = link.absUrl("href");
                
                // Skip URLs with fragments or query parameters
                final String cleanedUrl = cleanUrl(nextUrl);
                
                // Only process URLs from the same domain that we haven't visited
                if (cleanedUrl != null && !cleanedUrl.isEmpty() && 
                    cleanedUrl.contains(domain) && !visitedUrls.contains(cleanedUrl)) {
                    
                    // Mark as visited and submit for crawling
                    if (visitedUrls.add(cleanedUrl)) {
                        executor.submit(() -> crawlPage(cleanedUrl, domain, visitedUrls, foundImages, executor));
                    }
                }
            }
        } catch (Exception e) {
            // Just log and continue
            System.err.println("Error processing page " + url + ": " + e.getMessage());
        }
    }
    
    private void extractBackgroundImage(String style, String pageUrl, Set<String> foundImages) {
        int startIndex = style.indexOf("url(");
        if (startIndex >= 0) {
            startIndex += 4;
            int endIndex = style.indexOf(")", startIndex);
            if (endIndex >= 0) {
                String url = style.substring(startIndex, endIndex).trim();
                // Remove quotes if present
                if ((url.startsWith("\"") && url.endsWith("\"")) || 
                    (url.startsWith("'") && url.endsWith("'"))) {
                    url = url.substring(1, url.length() - 1);
                }
                
                // Convert to absolute URL if needed
                if (url.startsWith("/")) {
                    String domain = extractDomain(pageUrl);
                    if (domain != null) {
                        url = domain + url;
                    }
                } else if (!url.startsWith("http")) {
                    // Relative URL
                    try {
                        java.net.URL base = new java.net.URL(pageUrl);
                        java.net.URL resolved = new java.net.URL(base, url);
                        url = resolved.toString();
                    } catch (Exception e) {
                        return;
                    }
                }
                
                if (isValidImageUrl(url)) {
                    foundImages.add(url);
                }
            }
        }
    }
    
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        
        String lower = url.toLowerCase();
        
        // Check for valid image extensions
        boolean hasValidExtension = lower.endsWith(".jpg") || 
                                   lower.endsWith(".jpeg") || 
                                   lower.endsWith(".png") || 
                                   lower.endsWith(".gif") || 
                                   lower.endsWith(".bmp") || 
                                   lower.endsWith(".svg") ||
                                   lower.endsWith(".webp");
        
        // Skip common web icons and tiny images
        if (lower.contains("favicon") || 
            lower.contains("icon-") || 
            lower.contains("/icon/") ||
            lower.contains("pixel.") || 
            lower.contains("spacer.") ||
            lower.contains("1x1.gif") ||
            lower.contains("transparent.")) {
            return false;
        }
        
        return hasValidExtension;
    }
    
    private String cleanUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        // Remove fragments
        int fragmentIndex = url.indexOf('#');
        if (fragmentIndex > 0) {
            url = url.substring(0, fragmentIndex);
        }
        
        // Remove query parameters
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            url = url.substring(0, queryIndex);
        }
        
        return url;
    }
    
    private String extractDomain(String url) {
        try {
            java.net.URL urlObj = new java.net.URL(url);
            return urlObj.getProtocol() + "://" + urlObj.getHost();
        } catch (Exception e) {
            return null;
        }
    }
}