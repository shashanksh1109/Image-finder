package com.eulerity.hackathon.imagefinder;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;

/**
 * Simple test class for ImageFinder servlet
 */
public class ImageFinderTest {

    @Test
    public void testMain() {
        // This test just verifies that the ImageFinder class can be instantiated
        ImageFinder servlet = new ImageFinder();
        assertNotNull(servlet);
    }
    
    @Test
    public void testTestImages() {
        // Verify the test images array is not empty
        assertNotNull(ImageFinder.testImages);
        assertTrue(ImageFinder.testImages.length > 0);
        
        // Verify all test images have valid image extensions
        Arrays.stream(ImageFinder.testImages).forEach(url -> {
            assertTrue(url.toLowerCase().endsWith(".jpg") || 
                      url.toLowerCase().endsWith(".jpeg") || 
                      url.toLowerCase().endsWith(".png") || 
                      url.toLowerCase().endsWith(".gif"));
        });
    }
}