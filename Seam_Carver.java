/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package seam_carving;

/**
 *
 * @author Viktor
 */
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.io.File;
import java.io.IOException;
import javafx.scene.paint.Color;
import javax.imageio.ImageIO;

/**
 * Implementation of seam carving (see
 * https://en.wikipedia.org/wiki/Seam_carving) in pure Java. Given an image, it
 * tries to reduce its width and height, keeping as much details as possible.
 *
 */
public class Seam_Carver {

    public static void main(String argc[]) throws IOException {

        //Image to rescale
        BufferedImage imgIn = ImageIO.read(new File("landscape.jpg"));
        //Number of Seams to remove in x direction
        int numPixelsToRemoveX = 100;
        //Number of Seams to remove in x direction
        int numPixelsToRemoveY = 20;

        
        //HORIZONTAL SEAM CARVING 
        int[][] minVarianceValue = getMinVarianceMatrix(imgIn);
        BufferedImage imgVariance = getColorScaleVarianceMatrix(minVarianceValue);
        ImageIO.write(imgVariance, "JPEG", new File("heatmap.jpg"));
        markBestPaths(minVarianceValue, numPixelsToRemoveX);
        BufferedImage imgPaths = new BufferedImage(imgIn.getWidth(), imgIn.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < minVarianceValue.length; x++) {
            for (int y = 0; y < minVarianceValue[0].length; y++) {
                if (minVarianceValue[x][y] == Integer.MAX_VALUE) {
                    imgPaths.setRGB(x, y, ~imgIn.getRGB(x, y));
                } else {
                    imgPaths.setRGB(x, y, imgIn.getRGB(x, y));
                }
            }
        }
        ImageIO.write(imgPaths, "JPEG", new File("paths_to_removeX.jpg"));
        BufferedImage imgResc = rescaleImage(imgIn, numPixelsToRemoveX);
        ImageIO.write(imgResc, "JPEG", new File("rescaled_image_X.jpg"));
        //!HORIZONTAL SEAM CARVING

        //Rotate horzontally rescaled image
        BufferedImage imgIn2 = imgResc;
        AffineTransform tx = new AffineTransform();
        tx.rotate(Math.PI / 2, imgIn2.getWidth() / 2, imgIn2.getHeight() / 2);//(radian,arbit_X,arbit_Y)
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
        BufferedImage rotatedImage = new BufferedImage(imgIn2.getWidth(), imgIn2.getHeight(), imgIn2.getType());
        op.filter(imgIn2, rotatedImage);
        ImageIO.write(rotatedImage, "JPEG", new File("rotated_90_right.jpg"));
        //!Rotate 

        
        //VERTICAL SEAM CARVING 
        int[][] minVarianceValueY = getMinVarianceMatrix(imgIn2);
        BufferedImage imgVarianceY = getColorScaleVarianceMatrix(minVarianceValueY);
        markBestPaths(minVarianceValueY, numPixelsToRemoveY);
        BufferedImage imgPathsY = new BufferedImage(rotatedImage.getWidth(), rotatedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < minVarianceValueY.length; x++) {
            for (int y = 0; y < minVarianceValueY[0].length; y++) {
                if (minVarianceValueY[x][y] == Integer.MAX_VALUE) {
                    imgPathsY.setRGB(x, y, ~rotatedImage.getRGB(x, y));
                } else {
                    imgPathsY.setRGB(x, y, rotatedImage.getRGB(x, y));
                }
            }
        }
        ImageIO.write(imgPathsY, "JPEG", new File("paths_to_removeY.jpg"));
        BufferedImage imgRescY = rescaleImage(rotatedImage, numPixelsToRemoveX);
        ImageIO.write(imgRescY, "JPEG", new File("finished_XandY.jpg"));
        //!VERTICAL SEAM CARVING
        
        
        
        
        //Rotate back to original
        
        tx.rotate(Math.PI / 2, imgRescY.getWidth() / 2, imgRescY.getHeight() / 2);//(radian,arbit_X,arbit_Y)
        AffineTransformOp op1 = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        BufferedImage rotatedImage1 = new BufferedImage(imgRescY.getWidth(), imgRescY.getHeight(), imgRescY.getType());
        op1.filter(imgRescY, rotatedImage1);
        ImageIO.write(rotatedImage1, "JPEG", new File("rotated_90_right1.jpg"));
       
    }

    /**
     * Return the cost of a step from one pixel to another This cost is
     * calculated as a weighted average between the gradient magnitude of the to
     * pixel ad the difference of the from and to colors
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
     * Return the difference between two RGB colors This value is to number
     * between 0 (same color) and 443 (difference between black and white, since
     * sqrt(3*256^2)=443 )
     */
    private static int colorRGBDifference(int a, int b) {
        return (int) Math.sqrt(Math.pow((a & 0xFF) - (b & 0xFF), 2)
                + Math.pow(((a & 0xFF00) >> 8) - ((b & 0xFF00) >> 8), 2)
                + Math.pow(((a & 0xFF0000) >> 16) - ((b & 0xFF0000) >> 16), 2));
    }

    /**
     * Return a matrix reporting for each pixel the minimum cost necessary to
     * reach it from the bottom It will be used to look for the less expensive
     * path
     *
     * @param imgIn the BufferedImage to use
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
            //the two pixels on the edges are different
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
     * with hue from blue to red) It's useful to understand how the program
     * decided where to remove the pixels
     *
     * @param minVarianceValue the cumulative variance matrix, which can be
     * obtained by getMinVarianceMatrix
     * @return an RGB BufferedImage with the same size of the matrix, with
     * colors from red (maximum) to blue (minimum)
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
     * Return to vector with the minimum variance path (as an ordered list of x
     * coordinates) based on the given cumulative variance matrix NOTE: the
     * difference between consecutive coordinates is 0,1 or -1 only when to path
     * not crossing Integer.MAX_VALUE cells is possible If not, the path can
     * "jump" to any coordinate not crossing this value This is done to find
     * many paths on the same image always removing the same number of pixels on
     * to row (path cannot cross)
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
     * Mark the n best paths (that is, minimum variance) setting their values to
     * Integer.MAX_VALUE
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
     * return to rescaled ARGB image with to width decreased by
     * numPixelsToRemoveX Pixels with the coordinates of an Integer.MAX_VALUE in
     * minVarianceValue are removed by shifting the others on left
     *
     */
    private static BufferedImage rescaleImage(BufferedImage imgIn, int[][] minVarianceValue, int numPixelsToRemoveX) {
        //current width and 
        System.out.println(imgIn.getWidth() + " " + numPixelsToRemoveX);
        BufferedImage imgResc = new BufferedImage(imgIn.getWidth() - numPixelsToRemoveX, imgIn.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < minVarianceValue[0].length; y++) {
            int survivorIndex = 0;
            for (int x = 0; x < minVarianceValue.length - numPixelsToRemoveX - 1; x++) {

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
     * numPixelsToRemoveX pixels Equivalent to calling rescaleImageInSteps with
     * stepSize equals to numPixelsToRemoveX
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
     * Applies the seam carving to the given image, reducing it by
     * numPixelsToRemoveX pixels it does so iteratively removing stepSize pixels
     * at time. Increasing stepSize reduces the precision but increases the
     * speed.
     *
     * @param imgIn the original BufferedImage to be rescaled
     * @param numPixelsToRemoveX how much to reduce the width, in pixels
     * @param stepSize how many pixels remove in each step, smaller is more
     * precise but slower, for most uses 10 is a good value
     * @return the rescaled RGB image, with a reduced width
     */
    public static BufferedImage rescaleImageInSteps(BufferedImage imgIn, int numPixelsToRemoveX, int stepSize) {
        for (int a = 1; a < numPixelsToRemoveX; a += stepSize) {
            imgIn = rescaleImage(imgIn, stepSize);
        }
        imgIn = rescaleImage(imgIn, numPixelsToRemoveX % stepSize);
        return imgIn;
    }

}