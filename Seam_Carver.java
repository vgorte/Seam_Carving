import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.io.File;
import java.io.IOException;
import javafx.scene.paint.Color;
import javax.imageio.ImageIO;

/**
 * Implementation of seam carving algorithm 
 * (see https://en.wikipedia.org/wiki/Seam_carving) in pure Java.
 * Given an image, the programm reduces its width and height, keeping as many important pixels as possible (according to energy-function).
 * My idea was to calculate the energymap of the image (heatmap) and look for seams (vertical) with least energy and remove them. 
 * I rotate the image after the seams are removed and do the same thing again, thus rescaling the image in X and Y direction.
 * In the process 8 images are generated to check all stages of the process. 
 * The result (rescaled image) will be called image_rescaled.jpg.
 * 
 * @author Viktor Gorte
 */
public class Seam_Carver {
    public static void main(String argc[]) throws IOException {
    	
    	//FILL IN YOUR INFORMATIONS BEFORE STARTING
        //______________________________________________________________________________
        //Please specify path of the (to be rescaled) image
        BufferedImage imgIn = ImageIO.read(new File("feather.jpg"));
        //Number of Seams to remove in x direction
        int numPixelsToRemoveX = 20;
        //Number of Seams to remove in y direction
        int numPixelsToRemoveY = 20;
        //______________________________________________________________________________     
        
        System.out.println("Starting ...");
        System.out.println("---------------------------------------------------------");
        System.out.println("Image: "+imgIn.getWidth()+" x "+imgIn.getHeight()+" pixels before seam carving.");
        System.out.println("---------------------------------------------------------");
        BufferedImage imgRescaled = verticalSeamCarving(imgIn, numPixelsToRemoveX, "X");
        System.out.println("Rotating 90°");
        BufferedImage rotatedImage = rotate(imgRescaled);
        imgRescaled = verticalSeamCarving(rotatedImage, numPixelsToRemoveY, "Y");
        BufferedImage rescaledImage = reverseRotation(imgRescaled);
        ImageIO.write(rescaledImage, "JPEG", new File("image_rescaled.jpg")); 
        System.out.println("---------------------------------------------------------");
        System.out.println("Rescaled image: "+ rescaledImage.getWidth()+ " x "+rescaledImage.getHeight() + " pixels." );
        System.out.println("---------------------------------------------------------");
        System.out.println("Finished!");
    }
    
    /**
     * Seam Carving in vertical direction on given BufferedImage
     * Creates a heatmap reresenting the energy of all pixels in image.
     * Calculates given number of seams with least energy to remove them.
     * Removes seams and thus shorting the length of the image
     * Creates 3 images. A heatmap, the original image with seams (color of pixels in seam get inversed)
     * and a rescaled image without least energy seams.
     * 
     * @param image 	Image to be rescaled
     * @param seams		Number of seams to be removed
     * @param direction		Direction of seams
     * @return BufferedImage	Image with removed seams
     * @throws IOException
     */
    public static BufferedImage verticalSeamCarving(BufferedImage image, int numberOfSeams, String direction) throws IOException{ 	
    	System.out.println("Starting seam carving in "+ direction +" direction:");
    	  int[][] minVarianceValue = getMinVarianceMatrix(image);
          System.out.println("Creating HEATMAP!!");
          BufferedImage imgVariance = getColorScaleVarianceMatrix(minVarianceValue);
          ImageIO.write(imgVariance, "JPEG", new File("heatmap"+direction+".jpg"));
          System.out.println("Finding best seams ...");
          markBestPaths(minVarianceValue, numberOfSeams);
          BufferedImage imgPaths = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
          for (int x = 0; x < minVarianceValue.length; x++) {
              for (int y = 0; y < minVarianceValue[0].length; y++) {
                  if (minVarianceValue[x][y] == Integer.MAX_VALUE) {
                      imgPaths.setRGB(x, y, ~image.getRGB(x, y));
                  } else {
                      imgPaths.setRGB(x, y, image.getRGB(x, y));
                  }
              }
          }
          ImageIO.write(imgPaths, "JPEG", new File("seams_to_remove"+direction+".jpg"));
          BufferedImage imgResc = rescaleImage(image, numberOfSeams);
          System.out.println("Removing seams in " + direction+ " direction");
          ImageIO.write(imgResc, "JPEG", new File("image_without_"+direction+"_seams.jpg"));
          
          return imgResc;
    }
    
    /**
     * Return the cost of a step from one pixel to another This cost is
     * calculated as a weighted average between the gradient magnitude of the "to" pixel and the
     * difference of both pixels colors
     * 
     * @param imgIn  Input image
     * @param x1,x2,y1,y2  Pixels to calculate cost 
     * @return	Cost to go from one pixel to another
     */
    private static int stepCost(BufferedImage imgIn, int x1, int y1, int x2, int y2) {
        int to = imgIn.getRGB(x1, y1);
        int from = imgIn.getRGB(x2, y2);

        return ((x1 < imgIn.getWidth() - 2 ? colorRGBDifference(to, imgIn.getRGB(x1 + 1, y1)) : 0)
                + (x1 > 0 ? colorRGBDifference(to, imgIn.getRGB(x1 - 1, y1)) : 0)
                + (y1 < imgIn.getHeight() - 2 ? colorRGBDifference(to, imgIn.getRGB(x1, y1 + 1)) : 0)
                + (y1 > 0 ? colorRGBDifference(to, imgIn.getRGB(x1, y1 - 1)) : 0)
                + 5 * colorRGBDifference(to, from)) / 9;

    }

    /**
     * Return the difference between two RGB colors This value is a number
     * between 0 (same color) and 443 (difference between black and white, since
     * sqrt(3*256^2)=443 )
     * src:  https://stackoverflow.com/questions/9018016/how-to-compare-two-colors-for-similarity-difference
     * 
     * @param a, b	 RGB Value of a pixel
     * @return	difference between two given pixels a, b
     */
    private static int colorRGBDifference(int a, int b) {
        return (int) Math.sqrt(Math.pow((a & 0xFF) - (b & 0xFF), 2)
                + Math.pow(((a & 0xFF00) >> 8) - ((b & 0xFF00) >> 8), 2)
                + Math.pow(((a & 0xFF0000) >> 16) - ((b & 0xFF0000) >> 16), 2));
    }

    /**
     * Return a matrix representing each pixels minimum cost for reaching it form the bottom
     * Used for finding the least energy path
     * @param imgIn input image
     * @return the cumulative cost matrix
     */
    public static int[][] getMinVarianceMatrix(BufferedImage imgIn) {
        int[][] minVarianceValue = new int[imgIn.getWidth()][imgIn.getHeight()];
        //first row, is all 0s
        for (int x = 0; x < imgIn.getWidth(); x++) {
            minVarianceValue[x][imgIn.getHeight() - 1] = 0;
        }
        int widthMaxIndex = imgIn.getWidth() - 1;
        for (int y = imgIn.getHeight() - 2; y > 0; y--) {
            minVarianceValue[widthMaxIndex][y] = Math.min(stepCost(imgIn, widthMaxIndex, y + 1, widthMaxIndex, y) + minVarianceValue[widthMaxIndex][y + 1], stepCost(imgIn, widthMaxIndex - 1, y + 1, widthMaxIndex, y) + minVarianceValue[widthMaxIndex - 1][y + 1]);
            for (int x = 1; x < imgIn.getWidth() - 1; x++) {
                minVarianceValue[x][y] = Math.min(stepCost(imgIn, x, y + 1, x, y) + minVarianceValue[x][y + 1],
                        Math.min(stepCost(imgIn, x - 1, y + 1, x, y) + minVarianceValue[x - 1][y + 1],
                                stepCost(imgIn, x + 1, y + 1, x, y) + minVarianceValue[x + 1][y + 1])
                );
            }
            minVarianceValue[0][y] = Math.min(stepCost(imgIn, 0, y + 1, 0, y) + minVarianceValue[0][y + 1], stepCost(imgIn, 1, y + 1, 0, y) + minVarianceValue[1][y + 1]);
        }
        return minVarianceValue;
    }

    /**
     * Return an image representing the variance matrix in color scale (colors
     * with hue from blue to red)
     *
     * @param minVarianceValue   the cumulative variance matrix
     * @return BufferedImage  heatmap representing the energy of each pixel
     */
    public static BufferedImage getColorScaleVarianceMatrix(int[][] minVarianceValue) {
        BufferedImage imgVariance = new BufferedImage(minVarianceValue.length, minVarianceValue[0].length, BufferedImage.TYPE_INT_RGB);
        int maxVariance = 0;
        for (int x = 0; x < minVarianceValue.length; x++) {
            for (int y = 0; y < minVarianceValue[0].length; y++) {
                if (minVarianceValue[x][y] > maxVariance) {
                    maxVariance = minVarianceValue[x][y];
                }
            }
        }
        for (int x = 0; x < minVarianceValue.length; x++) {
            for (int y = 0; y < minVarianceValue[0].length; y++) {
                double hue = Color.BLUE.getHue() + (Color.RED.getHue() - Color.BLUE.getHue()) * minVarianceValue[x][y] / maxVariance;
                imgVariance.setRGB(x, y, java.awt.Color.HSBtoRGB((float) hue / 360, 1, 1));
            }
        }
        return imgVariance;
    }
   
    /**
     * Return the vector with the minimum variance path (as an ordered list of x
     * coordinates) based on the given cumulative variance matrix 
     * 
     * @param minVarianceValue
     * @return 
     */
    private static int[] minVariancePath(int[][] minVarianceValue) {
        int[] pxlToRemoveindex = new int[minVarianceValue[0].length];
        pxlToRemoveindex[0] = 1;
        for (int x = 1; x < minVarianceValue.length - 1; x++) {
            if (minVarianceValue[x][0] < minVarianceValue[pxlToRemoveindex[0]][0] && minVarianceValue[x][0] != Integer.MAX_VALUE) {
                pxlToRemoveindex[0] = x;
            }
        }

        for (int y = 1; y < minVarianceValue[0].length - 1; y++) {
            pxlToRemoveindex[y] = pxlToRemoveindex[y - 1];
            if (pxlToRemoveindex[y] > 0 && minVarianceValue[pxlToRemoveindex[y - 1] - 1][y] < minVarianceValue[pxlToRemoveindex[y - 1]][y]) {
                pxlToRemoveindex[y] = pxlToRemoveindex[y - 1] - 1;
            }

            if (pxlToRemoveindex[y - 1] < minVarianceValue.length - 1 && minVarianceValue[pxlToRemoveindex[y - 1] + 1][y] < minVarianceValue[pxlToRemoveindex[y - 1]][y]) {
                pxlToRemoveindex[y] = pxlToRemoveindex[y - 1] + 1;
            }

            if (minVarianceValue[pxlToRemoveindex[y]][y] == Integer.MAX_VALUE) {
                for (int x = 1; x < minVarianceValue.length - 1; x++) {
                    if (minVarianceValue[x][y] < minVarianceValue[pxlToRemoveindex[y]][y]) {
                        pxlToRemoveindex[y] = x;
                    }
                }
            }
        }
        return pxlToRemoveindex;
    }

    /**
     * Mark the n best paths (minimum variance) 
     * setting their values to Integer.MAX_VALUE
     *
     * @param minVarianceValue the cumulative variance matrix
     * @param numPaths the number of paths to be marked
     */
    public static void markBestPaths(int[][] minVarianceValue, int numPaths) {
        for (int i = 0; i < numPaths; i++) {
            int[] pxlToRemoveindex = minVariancePath(minVarianceValue);
            for (int y = 1; y < minVarianceValue[0].length; y++) {
                minVarianceValue[pxlToRemoveindex[y]][y] = Integer.MAX_VALUE;
            }
        }
    }

    /**
     * Return the rescaled ARGB image with the width decreased by
     * numPixelsToRemove(X/Y) Pixels with the coordinates of an Integer.MAX_VALUE 
     * 
     * @param imgIn	 Image to be rescaled
     * @param minVarianceValue	Matrix of variance values
     * @param numPixelsToRemove	 Number of pixels to remove
     * @return BufferedImage	Image (rescaled)
     */
    private static BufferedImage rescaleImage(BufferedImage imgIn, int[][] minVarianceValue, int numPixelsToRemove) {
        BufferedImage imgResc = new BufferedImage(imgIn.getWidth() - numPixelsToRemove, imgIn.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < minVarianceValue[0].length; y++) {
            int survivorIndex = 0;
            for (int x = 0; x < minVarianceValue.length - numPixelsToRemove - 1; x++) {

                while (minVarianceValue[survivorIndex][y] == Integer.MAX_VALUE) {
                    survivorIndex++;
                }
                imgResc.setRGB(x, y, imgIn.getRGB(survivorIndex, y));
                survivorIndex++;
            }
        }
        return imgResc;
    }

    /**
     * Applies the seam carving to the given image, reducing it by
     * numPixelsToRemove(X/Y) pixels 
     *
     * @param imgIn the original BufferedImage to be rescaled
     * @param numPixelsToRemoveX how much to reduce the width, in pixels
     * @return the rescaled RGB image, with a reduced width
     */
    public static BufferedImage rescaleImage(BufferedImage imgIn, int numPixelsToRemoveX) {
        int[][] minVarianceValue = getMinVarianceMatrix(imgIn);
        markBestPaths(minVarianceValue, numPixelsToRemoveX);
        return rescaleImage(imgIn, minVarianceValue, numPixelsToRemoveX);
    }

    /**
     * Rescaling image in steps. 
     * 
     * @param imgIn the original   BufferedImage to be rescaled
     * @param numPixelsToRemoveX   how much to reduce the width, in pixels
     * @param stepSize   how many pixels remove in each step
     * @return the rescaled RGB image
     */
    public static BufferedImage rescaleImageInSteps(BufferedImage imgIn, int numPixelsToRemoveX, int stepSize) {
        for (int a = 1; a < numPixelsToRemoveX; a += stepSize) {
            imgIn = rescaleImage(imgIn, stepSize);
        }
        imgIn = rescaleImage(imgIn, numPixelsToRemoveX % stepSize);
        return imgIn;
    }
    
    /**
     * Rotating an image around its center (90° to the right) 
     * 
     * @param image  Image to be rotated
     * @return BufferedImage   rotated image
     * @throws IOException
     */
    public static BufferedImage rotate(BufferedImage image) throws IOException{
          BufferedImage rotatedImage =  new BufferedImage(image.getHeight(), image.getWidth(), image.getType());
          AffineTransform tx = new AffineTransform();
          tx.rotate(Math.PI / 2, rotatedImage.getWidth() / 2, image.getHeight() / 2);
          AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
          op.filter(image, rotatedImage);
                    
          return rotatedImage;
    }
    
    /**
     * Reversing the rotation from a rotated BufferedImage by rotating it another three times.
     * 
     * @param img  rotated image
     * @return BufferedImage  image in start orientation
     * @throws IOException
     */
    public static BufferedImage reverseRotation (BufferedImage img) throws IOException{
    	BufferedImage image = rotate(rotate(rotate(img)));
    	System.out.println("Reversing rotation of rescaled image");
    	return image;
    }   
}
