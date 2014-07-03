package org.mandelcog.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import processing.core.PApplet;
import processing.core.PImage;
import processing.event.KeyEvent;
import processing.event.MouseEvent;

/**
 *
 * @author lopho
 */
@SuppressWarnings("serial")
public class Sketch extends PApplet {

    double vXMin = -2;
    double vYMin = -2;
    double vW = 4;
    double vH = 4;
    int initw;
    int inith;
    int maxIter;
    int defaultColor;

    double scale;

    double sx;
    double sy;

    PImage buf;
    PImage bufRender;
    PImage bufZoom;
    PImage bufComposite;
    PImage viewPort;

    int dirty;
    boolean resize;
    boolean color;

    double[] vals;
    int[] hist;

    int[] gradcol;
    double[] graddist;
    int defcol;

    ExecutorService ex;

    int amode;
    boolean blur;

    int step;

    private float startSelectionX, startSelectionY;
    private float endSelectionX, endSelectionY;

    public Sketch(int w, int h) {
        initw = w;
        inith = h;
        defaultColor = 0x00000000;
        scale = 1;
        maxIter = getMaxIter(1);
        dirty = 1;
        buf = new PImage(w, h, RGB);
        bufZoom = new PImage(w, h, ARGB);
        bufRender = new PImage(w, h, ARGB);
        bufComposite = new PImage(w, h, ARGB);
        viewPort = new PImage(w, h, RGB);
        resize = false;
        color = false;

        defcol = 0xff_ff_ff_ff;
        gradcol = new int[]{
            0xff_ff_ff_ff,
            0xff_00_00_11,
            0xff_ff_00_22,
            0xff_ff_C0_33,
            0xff_00_ff_ff,
            0xff_ff_ff_ff
        };
        graddist = new double[]{1, 1, 1, 1, 1, 1};
        /*
         gradcol = new int[] {
         0x00_00_00_00,
         0xff_ff_00_00,
         0xff_00_ff_00,
         0xff_00_00_ff,
         0xff_ff_00_ff,
         0xff_ff_ff_00,
         0xff_00_ff_ff,
         0xff_ff_ff_ff,
         };
         graddist = new double[] { 1,1,1,1,1,1,1,1 };*/
        amode = REPLACE;
        blur = true;
    }

    private static int getMaxIter(double scale) {
        double tune = 1;
        int iter = (int) (Math.sqrt(Math.abs(2 * Math.sqrt(Math.abs(1 - Math.sqrt(5 * scale))))) * 66.5 * tune);
        System.out.println(iter + " :: " + scale);
        return iter;
    }

    private static double mandel(double x, double y, int max) {
        double z = 0;
        double zi = 0;
        double nz;

        int iter = 0;

        while (z * z + zi * zi < 256 && iter < max) {
            nz = z * z - zi * zi + x;
            zi = 2 * z * zi + y;
            z = nz;
            iter = iter + 1;
        }

        double fIter = iter;
        if (iter < max) {
            double zn = Math.sqrt(z * z + zi * zi);
            double nu = Math.log(Math.log(zn) / Math.log(2)) / Math.log(2);
            // Rearranging the potential function.
            // Could remove the sqrt and multiply log(zn) by 1/2, but less clear.
            // Dividing log(zn) by log(2) instead of log(N = 1<<8)
            // because we want the entire palette to range from the
            // center to radius 2, NOT our bailout radius.
            fIter = fIter + 1 - nu;
        }

        return fIter;
    }

    private static int grad(double value, double max, int def, final int[] grad, final double[] dist) {
        // normalization
        if (dist.length != grad.length || grad.length == 0 || max <= 0 || value < 0)
            return def;

        if (value > max)
            value = value % max;

        value = dmap(value, 0, max, 0, 1);

        int[] g = grad.clone();
        double[] d = dist.clone();

        int c = 0;
        for (double dv : d) c += dv;

        for (int i = 0; i < d.length; i++) {
            d[i] /= c;
        }

        // mapping
        int range = 0;
        double d1 = 0;
        double d2 = 0;

        for (int i = 0; i < d.length - 1; i++) {
            d1 = d2;
            d2 = d2 + d[i];
            range = i;
            if (value <= d2) {
                break;
            }

        }

        int c1, c2;

        c1 = g[range];
        c2 = g[range + 1];

        double localValue = dmap(value, d1, d2, 0, 1);

        // interpolation
        return dlerpColor(c1, c2, localValue);
    }

    public static final double dmap(double value, double start1, double stop1, double start2, double stop2) {
        return start2 + (stop2 - start2) * ((value - start1) / (stop1 - start1));
    }

    public static int dlerpColor(int c1, int c2, double amt) {
        if (amt < 0) amt = 0;
        if (amt > 1) amt = 1;

        double a1 = ((c1 >> 24) & 0xff);
        double r1 = (c1 >> 16) & 0xff;
        double g1 = (c1 >> 8) & 0xff;
        double b1 = c1 & 0xff;
        double a2 = (c2 >> 24) & 0xff;
        double r2 = (c2 >> 16) & 0xff;
        double g2 = (c2 >> 8) & 0xff;
        double b2 = c2 & 0xff;

        return (((int) (a1 + (a2 - a1) * amt) << 24)
                | ((int) (r1 + (r2 - r1) * amt) << 16)
                | ((int) (g1 + (g2 - g1) * amt) << 8)
                | ((int) (b1 + (b2 - b1) * amt)));
    }

    @Override
    public void setup() {
        size(initw, inith);
        //colorMode(ARGB);
        background(0);
        step = inith / 16;
        calcMandel(step);
        loadPixels();
        viewPort.loadPixels();
        viewPort.pixels = pixels.clone();
        viewPort.updatePixels();
        stepSizeX();
        stepSizeY();
        
        fill(255, 0);
        stroke(127);
    }

    public void calcMandel(int step) {
        sx = stepSizeX();
        sy = stepSizeY();
        vals = new double[buf.pixels.length];
        ex = Executors.newFixedThreadPool(4);
        for (int y = 0; y < step; y++) {
            ex.submit(new Mandeler(y, step));
        }

        ex.shutdown();
        try {
            ex.awaitTermination(32, TimeUnit.SECONDS);
        } catch (InterruptedException ex1) {
            Logger.getLogger(Sketch.class.getName()).log(Level.SEVERE, null, ex1);
        }

    }

    private void colorMandel(int step) {
        double dIter = 0;
        for (int i = 0; i < vals.length; i++) {
            if (dIter < vals[i]) dIter = vals[i];
        }
        buf.loadPixels();
        ex = Executors.newFixedThreadPool(4);
        for (int i = 0; i < step; i++) {
            ex.submit(new Grader(dIter, i, step));
            //buf.pixels[i] = grad(vals[i],dIter,defcol,gradcol,graddist);
        }

        ex.shutdown();
        try {
            ex.awaitTermination(32, TimeUnit.SECONDS);
        } catch (InterruptedException ex1) {
            Logger.getLogger(Sketch.class.getName()).log(Level.SEVERE, null, ex1);
        }

        buf.updatePixels();
    }

    public PImage getView() {
        return viewPort;
    }

    @Override
    public void draw() {
        if (resize) {
            buf.resize(width, height);
            bufRender.resize(width, height);
            bufComposite.resize(width, height);
            bufZoom.resize(width, height);
            viewPort.resize(width, height);
            clear();
            image(viewPort, 0, 0);
            resize = false;
        }
        if (color) {
            colorMandel(step);
            bufRender.set(0, 0, buf);
            if (blur) bufRender.filter(BLUR, 0.6f);
            bufComposite.blend(bufRender,
                               0, 0, bufRender.width, bufRender.height,
                               0, 0, bufComposite.width, bufComposite.height,
                               amode);
            viewPort.set(0, 0, bufComposite.get());
            clear();
            image(viewPort, 0, 0);
            color = false;
        }
        if (dirty == 1) {
            calcMandel(buf.height / 16);
            colorMandel(buf.height / 16);
            bufRender.set(0, 0, buf);
            if (blur) bufRender.filter(BLUR, 0.6f);
            bufComposite.blend(bufRender,
                               0, 0, bufRender.width, bufRender.height,
                               0, 0, bufComposite.width, bufComposite.height,
                               amode);
            viewPort.set(0, 0, bufComposite.get());
            clear();
            image(viewPort, 0, 0);
            dirty--;
        }
        if (dirty == 2) {
            viewPort.set(0, 0, bufZoom);
            if (amode == REPLACE) clear();
            image(viewPort, 0, 0);
            dirty--;
        }
    }

    /**
     * Saves the first coordinates of the zoom selection.
     */
    @Override
    public void mousePressed() {
        if (mouseButton == LEFT) {
            startSelectionX = mouseX;
            startSelectionY = mouseY;
        }
    }

    /**
     * Draws the selection rectangle.
     */
    @Override
    public void mouseDragged() {
        if (mouseButton == LEFT) {
            //viewPort.set(0, 0, bufComposite.get());
            //clear();
            //image(viewPort, 0, 0);
            this.set(0, 0, viewPort);

            // Draw the selection

            rect(startSelectionX, startSelectionY, mouseX - startSelectionX, mouseY - startSelectionY);
        }
    }

    /**
     * Saves the second coordinates of the zoom selection and zoomes in.
     */
    @Override
    public void mouseReleased() {
        if ((mouseButton == LEFT) && (startSelectionX != mouseX || startSelectionY != mouseY)) {
            endSelectionX = mouseX;
            endSelectionY = mouseY;
            double step = 1;

            if ((startSelectionX + endSelectionX) < (startSelectionY + endSelectionY)) {
                step = height / abs(startSelectionY - endSelectionY);
                zoom((int) (startSelectionX + endSelectionX) / 2, (int) (startSelectionY + endSelectionY) / 2, step, true);
            } else {
                step = width / abs(startSelectionX - endSelectionX);
                zoom((int) (startSelectionX + endSelectionX) / 2, (int) (startSelectionY + endSelectionY) / 2, step, true);
            }
        }

    }

    @Override
    public void mouseClicked() {
        if (mouseButton == RIGHT) {
            zoom(mouseX, mouseY, 2, false);
        }
    }

    @Override
    public void mouseWheel(MouseEvent event) {
        float e = event.getCount();

        if (e > 0) {
            int c = gradcol[0];
            for (int i = 0; i < gradcol.length - 1; i++) {
                gradcol[i] = gradcol[i + 1];
            }
            gradcol[gradcol.length - 1] = c;

        } else if (e < 0) {
            int c = gradcol[gradcol.length - 1];
            for (int i = gradcol.length - 1; i > 0; i--) {
                gradcol[i] = gradcol[i - 1];
            }
            gradcol[0] = c;
        }

        color = true;

    }

    @Override
    public void keyTyped(KeyEvent event) {
        if (event.getKey() == ' ') {
            if (amode == BLEND) amode = REPLACE;
            else amode = BLEND;
        } else if (event.getKey() == 'b') {
            blur = !blur;
        }
        dirty = 1;
    }

    public void zoom(int posx, int posy, double step, boolean in) {
        sx = stepSizeX();
        sy = stepSizeY();

        int nw, nh, nx, ny;

        if (in) {
            vW = vW / step;
            vH = vH / step;
            scale *= step;
            nw = (int) (buf.width / step);
            nh = (int) (buf.height / step);
        } else {
            vW = vW * step;
            vH = vH * step;
            scale /= step;
            nw = (int) (buf.width * step);
            nh = (int) (buf.height * step);
        }

        maxIter = getMaxIter(scale);
        vXMin = vXMin + sx * posx - (vW / 2);
        vYMin = vYMin + sy * posy - (vH / 2);

        nx = (int) (posx - nw / 2.0);
        ny = (int) (posy - nh / 2.0);

        bufZoom.resize((int) (bufZoom.width / step), (int) (bufZoom.height / step));
        bufZoom.set(0, 0, buf.get(nx, ny, nw, nh));
        //bufZoom = buf.get(nx, ny, nw, nh);
        bufZoom.resize(buf.width, buf.height);

        dirty = 2;
    }

    public void dirty() {
        dirty = 2;
        resize = true;
    }

    private double stepSizeX() {
        return (vW / buf.width);
    }

    private double stepSizeY() {
        return (vH / buf.height);
    }

    // <editor-fold defaultstate="collapsed" desc="Unused">
    private int intLinear(int v, int c1, int c2, int min, int max) {
        double mapped = dmap(v, min, max, 0, 1);
        int color = dlerpColor(c1, c2, mapped);
        return color;
    }

    private int intCurve(int v, int c1, int c2, int min, int max,
                         float p1, float p2, float p3, float p4) {
        double mapped = curvePoint(p1, p2, p3, p4, map(v, min, max, 0, 1));
        int color = dlerpColor(c1, c2, mapped);
        return color;
    }
    // </editor-fold>

    private class Mandeler implements Runnable {

        private final int start;
        private final int step;

        Mandeler(int start, int step) {
            this.start = start;
            this.step = step;
        }

        @Override
        public void run() {
            double py = vYMin / sy;
            for (int y = start; y < buf.height; y += step) {
                for (int x = 0; x < buf.width; x++) {
                    double col = mandel(vXMin + sx * x, vYMin + sy * y, maxIter);
                    vals[y * buf.width + x] = col;
                }
            }
        }

    }

    private class Grader implements Runnable {

        private final double max;
        private final int step;
        private final int start;

        Grader(double max, int start, int step) {
            this.max = max;
            this.step = step;
            this.start = start;
        }

        @Override
        public void run() {
            for (int i = start; i < vals.length; i += step) {
                buf.pixels[i] = grad(vals[i], max, defcol, gradcol, graddist);
            }
        }

    }

}
